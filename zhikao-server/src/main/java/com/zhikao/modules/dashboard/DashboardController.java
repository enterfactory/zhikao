package com.zhikao.modules.dashboard;

import com.zhikao.common.R;
import com.zhikao.modules.dashboard.vo.DashboardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页看板接口（所有登录角色可用，内容按角色区分）。
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 首页看板：指标卡 + 近 7 天趋势 + 待办 */
    @GetMapping("/overview")
    public R<DashboardVO> overview() {
        return R.ok(dashboardService.overview());
    }
}
