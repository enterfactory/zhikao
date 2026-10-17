package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 门户首页 banner 帧（对外契约：id,title,subtitle,cta,theme,link）。
 * link_type/link_target 两列在前端只需要一个 link 字段，route 与 url 的语义由前端按 theme/配置处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerVO {

    private Long id;

    private String title;

    private String subtitle;

    /** 按钮文案 */
    private String cta;

    /** 视觉主题 */
    private String theme;

    /** 跳转目标（link_target 原值） */
    private String link;
}
