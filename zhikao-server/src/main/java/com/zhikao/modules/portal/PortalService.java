package com.zhikao.modules.portal;

import com.zhikao.modules.portal.dto.ResumeSaveDTO;
import com.zhikao.modules.portal.vo.PortalHomeVO;

/**
 * 学生门户服务：首页聚合与练习断点。
 */
public interface PortalService {

    /** 当前登录学生的门户首页聚合数据 */
    PortalHomeVO home();

    /** 上报/更新当前登录学生的练习断点（按用户 upsert） */
    void saveResume(ResumeSaveDTO dto);
}
