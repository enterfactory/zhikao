package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 首页通知行：取本人站内通知最近 3 条。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeVO {

    private Long id;

    private String title;

    /** 标签：通知类型（exam_publish / grade_published 等），前端按类型配色 */
    private String tag;

    private LocalDateTime time;
}
