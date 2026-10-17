package com.zhikao.modules.system.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    /** 关键字：同时匹配用户名与真实姓名 */
    private String keyword;

    /** 用户类型：1管理员 2教师 3学生 */
    private Integer userType;

    /** 班级 id（学生筛选用） */
    private Long classId;

    /** 状态：1启用 0禁用 */
    private Integer status;
}
