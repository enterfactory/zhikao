package com.zhikao.modules.dashboard.vo;

import lombok.Data;

/**
 * 待办项。
 */
@Data
public class TodoItemVO {

    /** 待办类型：pending_mark / draft_exam / to_take_exam ... */
    private String type;

    private String title;

    private String description;

    /** 关联数量 */
    private Long count;

    /** 点击后跳转的前端路由 */
    private String path;

    /** 紧急程度：primary / warning / danger */
    private String level;
}
