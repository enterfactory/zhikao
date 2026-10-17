package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.modules.system.vo.SemesterVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学期管理接口。
 *
 * <p>鉴权口径：列表登录即可查（任何角色建考试都要选学期，这是选项数据）；
 * 切换当前学期是教务动作，收敛到 system:semester:manage 权限点。
 */
@RestController
@RequestMapping("/system/semester")
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    /** 学期列表（最新在前），登录可查 */
    @GetMapping("/list")
    public R<List<SemesterVO>> list() {
        return R.ok(semesterService.list());
    }

    /** 切换当前学期 */
    @PostMapping("/{id}/current")
    @SaCheckPermission("system:semester:manage")
    @OperLog(module = "系统管理", action = "切换当前学期")
    public R<Void> makeCurrent(@PathVariable Long id) {
        semesterService.makeCurrent(id);
        return R.ok();
    }
}
