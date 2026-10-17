package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.entity.SysClass;
import com.zhikao.modules.system.dto.ClassSaveDTO;
import com.zhikao.modules.system.vo.ClassVO;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班级管理接口。
 *
 * <p>RBAC 口径：列表是「管理考试/用户时的选项数据」，教务侧（system:class:manage）与
 * 教学侧（exam:manage，教师建考试要选班级）都可查看，用 OR 模式；
 * 增删改是系统管理能力，收敛到 system:class:manage。
 */
@RestController
@RequestMapping("/class")
public class SysClassController {

    private final SysClassService classService;

    public SysClassController(SysClassService classService) {
        this.classService = classService;
    }

    /** 查询全部班级（教务管理与考试管理两类权限任一即可；对外只出 VO，不外放实体） */
    @GetMapping("/list")
    @SaCheckPermission(value = {"system:class:manage", "exam:manage"}, mode = SaMode.OR)
    public R<List<ClassVO>> list() {
        return R.ok(classService.list().stream().map(this::toVO).toList());
    }

    /** 新增或修改班级 */
    @PostMapping
    @SaCheckPermission("system:class:manage")
    @OperLog(module = "班级管理", action = "保存班级")
    public R<Void> save(@Valid @RequestBody ClassSaveDTO dto) {
        classService.save(dto);
        return R.ok();
    }

    /** 删除班级 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:class:manage")
    @OperLog(module = "班级管理", action = "删除班级")
    public R<Void> remove(@PathVariable Long id) {
        classService.remove(id);
        return R.ok();
    }

    /** 实体 -> 轻量视图：SysClass 当前没有审计列，保留显式转换以固化对外契约 */
    private ClassVO toVO(SysClass entity) {
        ClassVO vo = new ClassVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
