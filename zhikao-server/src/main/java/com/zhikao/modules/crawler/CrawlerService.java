package com.zhikao.modules.crawler;

import com.zhikao.common.ImportResultVO;
import com.zhikao.common.PageResult;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 题库文档采集与清洗服务（六期建模块，七期把「网页爬虫」换成「文档摄取」）。
 * 来源管理、任务编排（异步跑流水线）、文档上传、待审分页、审核入库、漏斗统计。
 * 遵循分层约定：Controller → 本 Service → Mapper；耗时的抽取与 OCR 委托给 {@link CrawlerPipelineRunner}。
 */
public interface CrawlerService {

    /** 新增或修改来源（id 空为新增）；返回来源 id */
    Long saveSource(CrawlSourceSaveDTO dto);

    /** 启用/停用切换 */
    void toggleSource(Long id);

    /** 读取当前启用状态（1/0），供 toggle 之后回传前端 */
    Integer sourceEnabled(Long id);

    /** 删除来源：来源是「配置」，历史任务与待审题是「事实」，删配置不连带删事实 */
    void deleteSource(Long id);

    /** 全部来源列表（带默认科目名） */
    List<CrawlSourceVO> listSources();

    /** 对某来源发起一次采集任务：建 PENDING 任务并异步执行，立即返回 taskId */
    Long runTask(Long sourceId);

    /** 任务分页 */
    PageResult<CrawlTaskVO> taskPage(CrawlTaskQueryDTO query);

    /** 文档解析日志分页：一份文档一行，带取字方式、认领的解析器、出题数与未出题原因 */
    PageResult<CrawlDocumentVO> documentPage(CrawlDocumentQueryDTO query);

    /** 单份文档的解析详情：原文片段 + 该文档产出的候选题（不存在时抛业务异常） */
    CrawlDocumentVO documentDetail(Long id);

    /** 待审/各阶段题目分页 */
    PageResult<CrawlQuestionVO> questionPage(CrawlQuestionQueryDTO query);

    /** 单条候选题详情（不存在时抛业务异常） */
    CrawlQuestionVO questionDetail(Long id);

    /** 批量审核：PROMOTE 入库 / REJECT 退回 */
    ImportResultVO review(CrawlReviewDTO dto);

    /** 本地解压目录直入流水线：校验白名单后建/复用 LOCAL_DIR 源并发起任务，返回 taskId */
    Long importLocal(CrawlImportLocalDTO dto);

    /**
     * 文档上传采集（七期）：docx/doc/pdf/图片/文本类文件落进摄取目录，再走同一条「抽取→解析→清洗→打标」流水线。
     * 上传不直接建任务队列里的新来源——所有上传共用「摄取目录」这一个 LOCAL_DIR 源，
     * 靠 dedup_hash 保证第二次上传不会把第一批文档重新 OCR 一遍。
     */
    CrawlUploadVO upload(MultipartFile[] files);

    /** 各阶段计数与概览，供面板 */
    CrawlStatsVO stats();

    /**
     * OCR 通道状态：当前配置的选取策略（auto/rapid/qwen）+ 两条通道各自的可用性。
     * 采集能不能认出扫描件，取决于这个而不是「按钮有没有点」——所以把它做成可查的，
     * 让管理页能在发起采集之前就说明白「云端通道未配置」这类问题。
     */
    Map<String, Object> ocrStatus();

    /**
     * 「识别与成本」概览（S3）：把 S2 落在文档行上的逐层计数汇总成口径。
     *
     * <p>单独一个接口而不是塞进 {@link #stats()}：stats 是每 30 秒轮询的轻量计数，
     * 这里的聚合要扫 crawl_raw 的时间窗，两者代价与刷新节奏都不一样，混在一起只会让轮询变慢。
     *
     * @param days 时间窗天数；0 或 null 走默认口径，超过一年按「全部」处理
     */
    CrawlOcrSummaryVO ocrSummary(Integer days);
}


