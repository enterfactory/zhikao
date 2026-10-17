package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表 / 详情返回对象。
 * 刻意不返回 password 字段，避免密码哈希外泄。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;

    /** 1管理员 2教师 3学生（persona） */
    private Integer userType;

    /** 角色中文名，供表格直接展示 */
    private String userTypeName;

    /** RBAC：该账号被分配的细粒度角色码，如 ["teacher"] */
    private List<String> roleCodes;

    private Long classId;

    /** 班级名称，便于列表直接展示（避免前端二次查询） */
    private String className;

    private String phone;

    /** 头像地址（未上传时为空） */
    private String avatar;

    /** 1启用 0禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
