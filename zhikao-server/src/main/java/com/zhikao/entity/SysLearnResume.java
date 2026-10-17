package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生练习断点：门户首页「继续练习」的数据源。
 *
 * <p>为什么每个学生只存一行：断点描述的是「这个人此刻练到哪」，是最新状态而不是历史流水；
 * 历史流水已由 exam_answer 承载，这里再存一份只会引入两边不一致的排查成本。
 * 因此按 user_id 唯一，POST /portal/resume 做 upsert。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_learn_resume")
public class SysLearnResume {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生 user_id（唯一） */
    private Long userId;

    /** 练习科目 id，可为空（自定义练习卷没有科目归属时） */
    private Long subjectId;

    /** 练习科目名（冗余存储：断点展示不依赖题库现状，题集被删也不影响「继续练习」卡片） */
    private String subjectName;

    /** 套题/练习集名称 */
    private String setName;

    /** 已做题数 */
    private Integer done;

    /** 本套总题数 */
    private Integer total;

    /** 正确率（百分比数值，如 86.50 表示 86.5%） */
    private BigDecimal accuracy;

    /** 最后一次练习时间（前端展示「X 小时前练过」） */
    private LocalDateTime lastAt;

    private LocalDateTime createTime;
}
