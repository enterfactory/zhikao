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
 * 清洗中间/待审题（六期）：从原文解析、清洗、打标后得到的「准题目」，人审通过才入主库 question。
 *
 * <p>stage 逐阶段推进，一条待审记录的一生：
 * RAW（刚解析出结构）→ CLEANED（清洗校验通过）→ TAGGED（学段/科目/知识点/难度已打标，待审）
 * → PROMOTED（人审通过、已写入 question 主库）/ REJECTED（人审退回）。
 * 中间态 PARSED/REVIEWED 保留给更细的流程扩展。
 *
 * <p>字段语义与主库 question 对齐：question_type 即 question.type（1单选2多选3判断4填空5简答），
 * options 同为 JSON。promoted_question_id 在入库成功后回填，形成「待审题↔正式题」的双向可追溯。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_question")
public class CrawlQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String predictionJson;

    private Integer sourcePage;
    private String sourceAnchor;
    private String sourceBbox;
    private String locatorMethod;
    private Integer sourceOrdinal;
    private LocalDateTime reviewStartedAt;
    private Long reviewMilliseconds;

    private Integer revision;

    private Integer formulaConfirmed;

    /** 来源原文 crawl_raw.id */
    private Long rawId;

    /** 阶段：RAW|PARSED|CLEANED|TAGGED|REVIEWED|PROMOTED|REJECTED */
    private String stage;

    /** 学段：小学/初中/高中（幼小启蒙另计） */
    private String grade;

    /** 年级：如 三年级/初二/高一 */
    private String gradeLevel;

    /** 匹配到的 subject.id */
    private Long subjectId;

    /** 题型，与 question.type 同义：1单选2多选3判断4填空5简答 */
    private Integer questionType;

    /** 清洗后题干 */
    private String content;

    /** 清洗后选项 JSON，如 {"A":"","B":""} */
    private String options;

    /** 清洗后答案 */
    private String answer;

    /** 清洗后解析 */
    private String analysis;

    /** 启发式难度 1-5 */
    private Integer difficulty;

    /** 知识点标签，逗号分隔（与 knowledge_point.name 包含式匹配） */
    private String knowledgeTags;

    /** 质量标记，如 DUPLICATE / SHORT_CONTENT，逗号分隔 */
    private String qualityFlags;

    /** 审核备注 */
    private String reviewNote;

    /** 入主库后回填 question.id */
    private Long promotedQuestionId;

    /** 清洗完成时间 */
    private LocalDateTime cleanTime;

    private LocalDateTime createTime;
}
