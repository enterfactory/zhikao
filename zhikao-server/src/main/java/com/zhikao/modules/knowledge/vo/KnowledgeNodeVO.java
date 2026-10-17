package com.zhikao.modules.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识点树节点：children 为空列表而非 null，前端递归渲染不用判空分支。
 */
@Data
public class KnowledgeNodeVO {

    private Long id;

    private Long parentId;

    private String name;

    private Long subjectId;

    private Integer sort;

    private List<KnowledgeNodeVO> children = new ArrayList<>();
}
