package com.zhikao.modules.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.KnowledgePoint;
import com.zhikao.mapper.KnowledgePointMapper;
import com.zhikao.modules.knowledge.vo.KnowledgeNodeVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点树服务（P3 地基）：只提供读侧的树形查询，写入侧随「题目录入改造」另行接入。
 *
 * <p>组装策略：一次全量取回（按科目过滤后仍是小结果集），内存建树；
 * 不做递归 SQL——MySQL 的 CTE 与 H2 写法存在差异，而本表层级最多三四层，
 * 内存两趟循环比方言化的递归查询更好维护。
 */
@Service
public class KnowledgeService {

    private final KnowledgePointMapper knowledgePointMapper;

    public KnowledgeService(KnowledgePointMapper knowledgePointMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /** 按科目取知识点树；subjectId 为空返回全量（各科目混排为多根森林） */
    public List<KnowledgeNodeVO> tree(Long subjectId) {
        List<KnowledgePoint> rows = knowledgePointMapper.selectList(
                Wrappers.<KnowledgePoint>lambdaQuery()
                        .eq(subjectId != null, KnowledgePoint::getSubjectId, subjectId)
                        .orderByAsc(KnowledgePoint::getSort)
                        .orderByAsc(KnowledgePoint::getId));

        Map<Long, KnowledgeNodeVO> index = new LinkedHashMap<>();
        for (KnowledgePoint row : rows) {
            KnowledgeNodeVO node = new KnowledgeNodeVO();
            node.setId(row.getId());
            node.setParentId(row.getParentId());
            node.setName(row.getName());
            node.setSubjectId(row.getSubjectId());
            node.setSort(row.getSort());
            index.put(row.getId(), node);
        }

        List<KnowledgeNodeVO> roots = new ArrayList<>();
        for (KnowledgeNodeVO node : index.values()) {
            KnowledgeNodeVO parent = node.getParentId() == null
                    ? null : index.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                // 父节点缺失（孤儿）也当根处理：树查询接口不该因为脏数据吞掉子树
                roots.add(node);
            }
        }
        return roots;
    }
}
