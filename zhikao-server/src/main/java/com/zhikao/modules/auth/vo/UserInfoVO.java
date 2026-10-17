package com.zhikao.modules.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录用户简要信息。
 *
 * <p>前端契约（/auth/login 与 /auth/info 的 userInfo）：
 * role 沿用 persona 码（super_admin/edu_admin→admin、subject_lead/teacher→teacher、student→student），
 * roleCodes / permissions 为 RBAC 细粒度数据，供菜单与按钮级渲染。
 */
@Data
public class UserInfoVO {

    private boolean passwordChangeRequired;
    private Long id;
    private String username;
    private String realName;
    private Integer userType;   // 1管理员 2教师 3学生
    private String role;        // persona 码：admin / teacher / student

    /** 细粒度角色码（sys_user_role），例如 ["teacher"]；无关联时是按 persona 兜底的默认角色 */
    private List<String> roleCodes;

    /** 权限码集合（@SaCheckPermission 同源）；学生为空列表 */
    private List<String> permissions;

    /** 头像地址；未上传时为空，前端回退到姓名首字 */
    private String avatar;
}
