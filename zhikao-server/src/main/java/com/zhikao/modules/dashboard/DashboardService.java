package com.zhikao.modules.dashboard;

import com.zhikao.modules.dashboard.vo.DashboardVO;

/**
 * 首页看板服务（按角色返回不同内容）。
 */
public interface DashboardService {

    /** 当前登录用户的首页看板 */
    DashboardVO overview();
}
