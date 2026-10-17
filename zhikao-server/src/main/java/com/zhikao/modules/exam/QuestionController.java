package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.exam.dto.QuestionQueryDTO;
import com.zhikao.modules.exam.dto.QuestionSaveDTO;
import com.zhikao.modules.exam.vo.QuestionSimilarVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 题库接口：RBAC 改造后按权限点控制——查看 question:view，
 * 编辑/删除/查重 question:edit，导入 question:import（学科负责人另有 question:audit 审核位）。
 */
@RestController
@RequestMapping("/question")
@SaCheckPermission("question:view")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 分页查询题目 */
    @GetMapping("/page")
    public R<PageResult<QuestionVO>> page(QuestionQueryDTO query) {
        return R.ok(questionService.page(query));
    }

    /** 题目详情 */
    @GetMapping("/{id}")
    public R<QuestionVO> detail(@PathVariable Long id) {
        return R.ok(questionService.detail(id));
    }

    /** 新增或修改题目 */
    @PostMapping
    @SaCheckPermission("question:edit")
    @OperLog(module = "题库管理", action = "保存题目")
    public R<Void> save(@Valid @RequestBody QuestionSaveDTO dto) {
        questionService.save(dto);
        return R.ok();
    }

    /**
     * 批量新增题目（AI 出题草稿勾选入库、多题补录等场景）。
     *
     * <p>注意：这个接口本身与 AI 无关，是一条通用的批量写入通道；
     * 它内部逐条复用 {@link QuestionService#save}，所以校验强度与手工新建完全一致。
     * 草稿是否是 AI 生成的，对后端没有区别——最终决定入库的始终是人。
     */
    @PostMapping("/batch")
    @SaCheckPermission("question:edit")
    @OperLog(module = "题库管理", action = "批量新增题目", saveParams = false)
    public R<ImportResultVO> saveBatch(@RequestBody @Valid List<@Valid QuestionSaveDTO> items) {
        return R.ok(questionService.saveBatch(items));
    }

    /** 删除题目 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("question:edit")
    @OperLog(module = "题库管理", action = "删除题目")
    public R<Void> remove(@PathVariable Long id) {
        questionService.remove(id);
        return R.ok();
    }

    /**
     * Excel 批量导入题目到指定科目。
     *
     * @param preview 传 true 表示只做校验并回显将要导入的内容，**不写库**。
     *                默认 false，保持原有「点了就是导入」的语义不变（脚本与老前端不受影响）。
     */
    @PostMapping("/import")
    @SaCheckPermission("question:import")
    @OperLog(module = "题库管理", action = "批量导入题目", saveParams = false)
    public R<ImportResultVO> importQuestions(@RequestParam Long subjectId,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean preview) {
        return R.ok(questionService.importQuestions(subjectId, file, preview));
    }

    /**
     * 入库前查重：找出与待入库题目字面高度重合的已有题目。
     *
     * <p>只返回提示、不阻止入库——「重复」在很多场景下是合理的
     * （同一个知识点出两道不同问法的题很正常），所以决定权留给教师。
     */
    @PostMapping("/similarity")
    @SaCheckPermission("question:edit")
    public R<List<QuestionSimilarVO>> similarity(@RequestBody QuestionSaveDTO dto) {
        return R.ok(questionService.findSimilar(dto));
    }

    /** 下载题目导入模板 */
    @GetMapping("/import/template")
    @SaCheckPermission("question:import")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = questionService.importTemplate();
        String filename = URLEncoder.encode("题目导入模板.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
