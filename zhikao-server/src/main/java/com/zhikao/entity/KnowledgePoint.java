package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点树节点（P3 地基）：按科目组织的多级知识点。
 *
 * <p>与 question.knowledge_point（自由文本）并存：历史题的文本标注不迁移，
 * 新链路（题目录入 / AI 归因）逐步改挂 knowledge_point 结构化节点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_point")
public class KnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父节点 id，空为根节点 */
    private Long parentId;

    private String name;

    /** 归属科目 */
    private Long subjectId;

    /** 同级排序 */
    private Integer sort;
}
