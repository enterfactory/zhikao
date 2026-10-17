package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运营轮播帧：学生门户首页 banner 位的数据源。
 *
 * <p>与「公告」的分工：公告是一次性通知（sys_notification / exam_announcement），
 * banner 是长期在架的运营位——内容由运营（这里是管理端）维护，学生每次进首页都看得到。
 * link_type + link_target 拆两列而不是一个 link：前端按类型决定「站内路由跳转」还是
 * 「外链新开窗口」，混在一列里前端就得猜。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_banner")
public class SysBanner {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主标题 */
    private String title;

    /** 副标题/说明文案 */
    private String subtitle;

    /** 按钮文案，如「立即报名」 */
    private String cta;

    /** 视觉主题（exam / paper / ai 等，前端按主题上色） */
    private String theme;

    /** 跳转类型：route 站内路由，url 外链，none 纯展示 */
    private String linkType;

    /** 跳转目标（route 时为路径，url 时为完整链接；none 可为空） */
    private String linkTarget;

    /** 展示顺序，越小越靠前 */
    private Integer sort;

    /** 1上架 0下架：下架帧不删除，留运营痕迹 */
    private Integer status;

    private LocalDateTime createTime;
}
