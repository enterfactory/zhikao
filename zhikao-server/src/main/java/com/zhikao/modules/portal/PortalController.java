package com.zhikao.modules.portal;

import com.zhikao.common.R;
import com.zhikao.modules.portal.dto.ResumeSaveDTO;
import com.zhikao.modules.portal.vo.PortalHomeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生门户接口。
 *
 * <p>鉴权口径：<b>登录即可，不挂角色/权限注解</b>。这三块数据（轮播、题库进度、
 * 断点、通知）全部是「本人维度」，不构成越权面；用 @SaCheckRole("student") 反而会把
 * 同样需要看首页的管理/教师账号挡在门外（他们登录学生视角预览时）。
 * 注意与 SysUser#userType 的区别：这里信任的是 Sa-Token 会话（全局拦截器已 checkLogin）。
 */
@RestController
@RequestMapping("/portal")
public class PortalController {

    private final PortalService portalService;

    public PortalController(PortalService portalService) {
        this.portalService = portalService;
    }

    /** 门户首页聚合：banners / subjectStats / leaderboard / resume / nextExam / notices */
    @GetMapping("/home")
    public R<PortalHomeVO> home() {
        return R.ok(portalService.home());
    }

    /** 上报练习断点（按当前登录用户 upsert） */
    @PostMapping("/resume")
    public R<Void> saveResume(@Valid @RequestBody ResumeSaveDTO dto) {
        portalService.saveResume(dto);
        return R.ok();
    }
}
