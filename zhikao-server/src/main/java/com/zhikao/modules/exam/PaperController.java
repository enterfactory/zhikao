package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.exam.dto.PaperManualDTO;
import com.zhikao.modules.exam.dto.PaperQueryDTO;
import com.zhikao.modules.exam.dto.PaperRandomDTO;
import com.zhikao.modules.exam.vo.PaperDetailVO;
import com.zhikao.modules.exam.vo.PaperVO;
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

import java.util.List;

/**
 * 试卷接口。
 *
 * <p><b>RBAC 改造后统一按 paper:manage 权限点控制。</b>组卷是教学业务动作，
 * 教师 / 学科负责人 / 教务管理员 / 超级管理员各自是否可组卷，完全由角色-权限关联决定，
 * 接口层不再写死角色名单——「谁能组卷」变更时只需要调整角色权限，不动代码。
 */
@RestController
@RequestMapping("/paper")
@SaCheckPermission("paper:manage")
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    /** 分页查询试卷 */
    @GetMapping("/page")
    public R<PageResult<PaperVO>> page(PaperQueryDTO query) {
        return R.ok(paperService.page(query));
    }

    /** 全部试卷（创建考试时选择用） */
    @GetMapping("/list")
    public R<List<PaperVO>> list() {
        return R.ok(paperService.list());
    }

    /** 试卷详情（含题目快照与标准答案，供教师预览） */
    @GetMapping("/{id}")
    public R<PaperDetailVO> detail(@PathVariable Long id) {
        return R.ok(paperService.detail(id));
    }

    /** 手工组卷 */
    @PostMapping("/genManual")
    @OperLog(module = "试卷组卷", action = "手工组卷")
    public R<Long> genManual(@Valid @RequestBody PaperManualDTO dto) {
        return R.ok(paperService.genManual(dto));
    }

    /** 随机组卷 */
    @PostMapping("/genRandom")
    @OperLog(module = "试卷组卷", action = "随机组卷")
    public R<Long> genRandom(@Valid @RequestBody PaperRandomDTO dto) {
        return R.ok(paperService.genRandom(dto));
    }

    /** 发布 / 撤回试卷 */
    @PutMapping("/{id}/status")
    @OperLog(module = "试卷组卷", action = "发布或撤回试卷")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        paperService.updateStatus(id, status);
        return R.ok();
    }

    /** 删除试卷（被考试引用时禁止删除） */
    @DeleteMapping("/{id}")
    @OperLog(module = "试卷组卷", action = "删除试卷")
    public R<Void> remove(@PathVariable Long id) {
        paperService.remove(id);
        return R.ok();
    }
}
