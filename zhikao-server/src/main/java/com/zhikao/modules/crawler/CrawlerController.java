package com.zhikao.modules.crawler;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageQuery;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.crawler.dto.CrawlDocumentQueryDTO;
import com.zhikao.modules.crawler.dto.CrawlImportLocalDTO;
import com.zhikao.modules.crawler.dto.CrawlQuestionQueryDTO;
import com.zhikao.modules.crawler.dto.CrawlReviewDTO;
import com.zhikao.modules.crawler.dto.CrawlSourceSaveDTO;
import com.zhikao.modules.crawler.dto.CrawlTaskQueryDTO;
import com.zhikao.modules.crawler.vo.CrawlDocumentVO;
import com.zhikao.modules.crawler.vo.CrawlOcrSummaryVO;
import com.zhikao.modules.crawler.vo.CrawlQuestionVO;
import com.zhikao.modules.crawler.vo.CrawlSourceVO;
import com.zhikao.modules.crawler.vo.CrawlStatsVO;
import com.zhikao.modules.crawler.vo.CrawlTaskVO;
import com.zhikao.modules.crawler.vo.CrawlUploadVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库采集（文档摄取 + 清洗）接口（七期）。
 *
 * <p>权限：整类挂在 {@code crawler:manage} 上——采集与「把外来内容写进正式题库」是同一条风险链，
 * 因此只有题库管理员持有该权限点；审核入库（PROMOTE）也在这个权限之内，不再细分。
 *
 * <p>七期删掉了「网页爬虫」这条路线，采集入口收敛为两个（本地目录、文档上传），
 * 它们共用同一条「抽取（含 OCR）→解析→清洗→打标→审核→入库」流水线。
 *
 * <p><b>与前端契约的对应关系</b>（zhikao-web/src/api/crawler.js + views/admin/crawler/index.vue）：
 * <pre>
 * GET    /crawler/stats                 → 阶段计数「拍平」成 {RAW,PARSED,...,REJECTED} + 概览字段
 * GET    /crawler/ocr/status            → {mode, rapidocr, qwen-vl}，通道可用性（布尔）
 * GET    /crawler/source/list           → 数组（不分页）
 * POST   /crawler/source                → 新建/保存，返回 sourceId
 * PUT    /crawler/source/{id}           → 编辑
 * DELETE /crawler/source/{id}           → 删除来源
 * POST   /crawler/source/{id}/toggle    → 启停，回传切换后的 enabled（1/0）
 * POST   /crawler/task/run/{sourceId}   → 返回 taskId（异步执行）
 * GET    /crawler/task/page             → { records, total }
 * GET    /crawler/document/page         → { records, total }，逐文档解析留痕
 * GET    /crawler/document/{id}         → 单份文档详情（原文片段 + 该文档产出的候选题）
 * GET    /crawler/question/page         → { records, total }，可按 stage/subjectId/grade/rawId 过滤
 * GET    /crawler/question/{id}         → 单条详情
 * POST   /crawler/question/review       → PROMOTE 入库 / REJECT 退回，返回成功失败计数
 * POST   /crawler/import/local          → 目录入队，返回一句可直接展示的话
 * POST   /crawler/upload                → multipart 上传文档，返回 {stored, rejected, sourceId, taskId}
 * </pre>
 *
 * <p>两个分页接口的入参是 <b>page / size</b>（前端在 api 层把 pageNum/pageSize 翻译成这两个名字），
 * 为兼容脚本与其它调用方，同时仍接受 DTO 原生的 pageNum/pageSize；出参沿用项目统一的
 * {@link PageResult}（{@code {records,total}}），前端只取这两个字段。
 */
@RestController
@RequestMapping("/crawler")
@SaCheckPermission("crawler:manage")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    // ==================== 漏斗统计 ====================

    /**
     * 阶段计数与概览。
     * 前端统计卡是按「data 顶层就是 RAW/PARSED/… 这些键」取值的
     * （{@code stats.value?.[key]}），所以这里把 VO 的 stageCounts 拍平到顶层，
     * 同时保留 stageCounts 嵌套结构，两类调用方都能读到。
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        CrawlStatsVO vo = crawlerService.stats();
        Map<String, Object> body = new LinkedHashMap<>();
        vo.getStageCounts().forEach(body::put);   // RAW / PARSED / … / REJECTED 提到顶层
        body.put("stageCounts", vo.getStageCounts());
        body.put("sourceTotal", vo.getSourceTotal());
        body.put("sourceEnabled", vo.getSourceEnabled());
        body.put("taskTotal", vo.getTaskTotal());
        body.put("rawTotal", vo.getRawTotal());
        return R.ok(body);
    }

    /**
     * OCR 双通道可用性（只读，供前端面板展示）。
     * 返回形如 {@code {mode:"auto", "rapidocr":true, "qwen-vl":false}}——
     * 布尔而非对象，前端直接当开关用；离线引擎不可用时页面会明确提示，而不是等任务跑失败。
     */
    @GetMapping("/ocr/status")
    public R<Map<String, Object>> ocrStatus() {
        return R.ok(crawlerService.ocrStatus());
    }

    /**
     * 「识别与成本」概览（S3）：取字来源分布、级联逐层漏斗、升级率与云端成本、按天走势、待人工关注的文档榜。
     *
     * <p>{@code days} 是时间窗（默认 30，0 或负数按默认；>365 视为不限）。
     * 数据全部来自 {@code crawl_raw} 的逐文档留痕列，口径与解析日志一致——
     * 两处对同一个数字给出两种说法，面板就没人信了。
     */
    @GetMapping("/ocr/summary")
    public R<CrawlOcrSummaryVO> ocrSummary(@RequestParam(required = false) Integer days) {
        return R.ok(crawlerService.ocrSummary(days));
    }

    // ==================== 采集来源 ====================

    /** 来源列表（全量，前端来源管理表格不分页） */
    @GetMapping("/source/list")
    public R<List<CrawlSourceVO>> sourceList() {
        return R.ok(crawlerService.listSources());
    }

    /** 新建来源（body 同 CrawlSourceSaveDTO，id 为空即新增） */
    @PostMapping("/source")
    @OperLog(module = "题库采集", action = "新建采集来源")
    public R<Long> sourceCreate(@Valid @RequestBody CrawlSourceSaveDTO dto) {
        dto.setId(null); // 新建通道不接受调用方自填 id
        return R.ok(crawlerService.saveSource(dto));
    }

    /** 编辑来源 */
    @PutMapping("/source/{id}")
    @OperLog(module = "题库采集", action = "编辑采集来源")
    public R<Long> sourceUpdate(@PathVariable Long id, @Valid @RequestBody CrawlSourceSaveDTO dto) {
        dto.setId(id); // 路径为准，避免 body 与 URL 指向不同来源
        return R.ok(crawlerService.saveSource(dto));
    }

    /** 删除来源（历史任务/待审题保留，只摘掉来源本身） */
    @DeleteMapping("/source/{id}")
    @OperLog(module = "题库采集", action = "删除采集来源")
    public R<Void> sourceDelete(@PathVariable Long id) {
        crawlerService.deleteSource(id);
        return R.ok();
    }

    /** 启用/停用切换：回传切换后的 enabled（1/0），前端据此直接翻转开关 */
    @PostMapping("/source/{id}/toggle")
    @OperLog(module = "题库采集", action = "启停采集来源")
    public R<Integer> sourceToggle(@PathVariable Long id) {
        crawlerService.toggleSource(id);
        return R.ok(crawlerService.sourceEnabled(id));
    }

    // ==================== 采集任务 ====================

    /** 对某来源发起一次抓取：建任务并异步执行，立即返回 taskId */
    @PostMapping("/task/run/{sourceId}")
    @OperLog(module = "题库采集", action = "发起采集任务")
    public R<Long> taskRun(@PathVariable Long sourceId) {
        return R.ok(crawlerService.runTask(sourceId));
    }

    /** 任务分页（page/size，兼容 pageNum/pageSize） */
    @GetMapping("/task/page")
    public R<PageResult<CrawlTaskVO>> taskPage(CrawlTaskQueryDTO query,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        applyPage(query, page, size);
        return R.ok(crawlerService.taskPage(query));
    }

    // ==================== 文档解析日志 ====================

    /**
     * 文档级解析日志分页（page/size，兼容 pageNum/pageSize）。
     * 与任务分页的区别：任务只有 fetched/cleaned/staged 三个聚合数，回答不了
     * 「这一份文档为什么一道题都没有」；这里的每一行就是流水线写回的逐文档留痕。
     */
    @GetMapping("/document/page")
    public R<PageResult<CrawlDocumentVO>> documentPage(CrawlDocumentQueryDTO query,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        applyPage(query, page, size);
        return R.ok(crawlerService.documentPage(query));
    }

    /** 单份文档的解析详情：取字原文片段 + 该文档产出的候选题 */
    @GetMapping("/document/{id}")
    public R<CrawlDocumentVO> documentDetail(@PathVariable Long id) {
        return R.ok(crawlerService.documentDetail(id));
    }

    // ==================== 清洗后的候选题目 ====================

    /** 待审/各阶段题目分页（page/size，兼容 pageNum/pageSize；stage/subjectId/grade/rawId 可空） */
    @GetMapping("/question/page")
    public R<PageResult<CrawlQuestionVO>> questionPage(CrawlQuestionQueryDTO query,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        applyPage(query, page, size);
        return R.ok(crawlerService.questionPage(query));
    }

    /** 单条候选题详情 */
    @GetMapping("/question/{id}")
    public R<CrawlQuestionVO> questionDetail(@PathVariable Long id) {
        return R.ok(crawlerService.questionDetail(id));
    }

    /** 批量审核：PROMOTE 写入正式题库 / REJECT 退回 */
    @PostMapping("/question/review")
    @OperLog(module = "题库采集", action = "候选题目审核入库", saveParams = false)
    public R<ImportResultVO> review(@Valid @RequestBody CrawlReviewDTO dto) {
        return R.ok(crawlerService.review(dto));
    }

    // ==================== 本地目录导入 ====================

    /**
     * 本地解压目录导入：校验白名单后建/复用 LOCAL_DIR 来源并发起任务。
     * 返回一句可直接展示的话（含 taskId），前端拿到字符串就直接用。
     */
    @PostMapping("/import/local")
    @OperLog(module = "题库采集", action = "本地目录导入")
    public R<String> importLocal(@Valid @RequestBody CrawlImportLocalDTO dto) {
        Long taskId = crawlerService.importLocal(dto);
        return R.ok("目录已加入采集队列（任务号 " + taskId + "）");
    }

    /**
     * 文档上传采集：docx/doc/pdf/图片/md/txt/json/csv 落进摄取目录后入队，走同一条流水线。
     *
     * <p>表单字段名固定为 {@code files}（可多选），前端用 FormData 直接 append 即可。
     * 逐个文件的拒绝原因放在 {@code rejected} 里返回——上传是批量动作，
     * 「3 个里有 1 个格式不支持」不该让整次请求失败。
     */
    @PostMapping("/upload")
    @OperLog(module = "题库采集", action = "文档上传采集", saveParams = false)
    public R<CrawlUploadVO> upload(@RequestParam("files") MultipartFile[] files) {
        return R.ok(crawlerService.upload(files));
    }

    /**
     * 前端 page/size → 服务层 PageQuery 的 pageNum/pageSize。
     * 只在这两个爬虫分页接口里翻译，其它模块仍按各自的 pageNum/pageSize 口径。
     */
    private void applyPage(PageQuery query, Integer page, Integer size) {
        if (page != null && page > 0) {
            query.setPageNum(page);
        }
        if (size != null && size > 0) {
            query.setPageSize(size);
        }
    }
}
