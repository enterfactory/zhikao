package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.entity.Subject;
import com.zhikao.modules.system.dto.SubjectSaveDTO;
import com.zhikao.modules.system.vo.SubjectVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科目管理接口。
 *
 * <p>RBAC 口径：列表是「组卷/题库/考试共用的选项数据」，教务侧（system:subject:manage）与
 * 教学侧（exam:manage）都可查看（OR 模式）；增删改收敛到 system:subject:manage。
 */
@RestController
@RequestMapping("/subject")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    /** 查询全部科目（两类权限任一可查看；create_by 属内部审计列，不外放） */
    @GetMapping("/list")
    @SaCheckPermission(value = {"system:subject:manage", "exam:manage"}, mode = SaMode.OR)
    public R<List<SubjectVO>> list() {
        return R.ok(subjectService.list().stream().map(this::toVO).toList());
    }

    /** 新增或修改科目 */
    @PostMapping
    @SaCheckPermission("system:subject:manage")
    @OperLog(module = "科目管理", action = "保存科目")
    public R<Long> save(@Valid @RequestBody SubjectSaveDTO dto) {
        return R.ok(subjectService.save(dto));
    }

    /** 删除科目 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:subject:manage")
    @OperLog(module = "科目管理", action = "删除科目")
    public R<Void> remove(@PathVariable Long id) {
        subjectService.remove(id);
        return R.ok();
    }

    /** 实体 -> 轻量视图：去掉 create_by 等内部审计列，接口契约与库表解耦 */
    private SubjectVO toVO(Subject entity) {
        SubjectVO vo = new SubjectVO();
        vo.setId(entity.getId());
        vo.setSubjectName(entity.getSubjectName());
        vo.setSubjectCode(entity.getSubjectCode());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
