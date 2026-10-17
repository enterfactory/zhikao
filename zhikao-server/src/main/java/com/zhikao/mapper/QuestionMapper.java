package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.Question;
import com.zhikao.modules.ai.agent.vo.QuestionDifficultyCountVO;
import com.zhikao.modules.ai.agent.vo.QuestionTypeCountVO;
import com.zhikao.modules.portal.vo.SubjectStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目 Mapper。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 按科目 + 题型（可选难度）随机抽取指定数量的题目 id，用于随机组卷。
     *
     * 在 SQL 层完成随机与限量：避免把整个题库拉进内存再打乱，题库大时差异明显。
     * excludeIds 用于跨规则去重（同一份试卷同一道题只出现一次）。
     *
     * @return 抽到的题目 id 列表，数量不足时返回实际能抽到的条数
     */
    @Select("""
            <script>
            SELECT id FROM question
            WHERE subject_id = #{subjectId} AND type = #{type}
            <if test="difficulty != null"> AND difficulty = #{difficulty} </if>
            <if test="excludeIds != null and excludeIds.size() > 0">
                AND id NOT IN
                <foreach collection="excludeIds" item="it" open="(" separator="," close=")">#{it}</foreach>
            </if>
            ORDER BY random() LIMIT #{count}
            </script>
            """)
    List<Long> selectRandomIds(@Param("subjectId") Long subjectId,
                               @Param("type") Integer type,
                               @Param("difficulty") Integer difficulty,
                               @Param("excludeIds") List<Long> excludeIds,
                               @Param("count") int count);

    /**
     * 题库按题型统计（AI 出题工具的只读输入）。
     * subject_id 为 null 时统计全库；用 &lt;script&gt; 动态拼接，避免「参数 IS NULL」依赖方言。
     */
    @Select("""
            <script>
            SELECT type AS type, COUNT(*) AS total FROM question
            <if test="subjectId != null"> WHERE subject_id = #{subjectId} </if>
            GROUP BY type ORDER BY type
            </script>
            """)
    List<QuestionTypeCountVO> countGroupByType(@Param("subjectId") Long subjectId);

    /** 题库按难度统计，用途同上 */
    @Select("""
            <script>
            SELECT difficulty AS difficulty, COUNT(*) AS total FROM question
            <if test="subjectId != null"> WHERE subject_id = #{subjectId} </if>
            GROUP BY difficulty ORDER BY difficulty
            </script>
            """)
    List<QuestionDifficultyCountVO> countGroupByDifficulty(@Param("subjectId") Long subjectId);

    /**
     * 门户「科目题库进度」：按科目分组计本题库总量（subjectName / 已刷数由服务层合并填充）。
     * GROUP BY 聚合天然只返回有题的科目，空库返回空列表。
     */
    @Select("""
            SELECT subject_id AS subject_id, COUNT(*) AS total_questions
            FROM question
            GROUP BY subject_id
            ORDER BY subject_id
            """)
    List<SubjectStatVO> countGroupBySubject();
}
