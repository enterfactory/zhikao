package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhikao.entity.ExamAnswer;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import com.zhikao.modules.exam.vo.MarkItemVO;
import com.zhikao.modules.learn.vo.WrongQuestionVO;
import com.zhikao.modules.portal.vo.SubjectStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 答题明细 Mapper。
 */
@Mapper
public interface ExamAnswerMapper extends BaseMapper<ExamAnswer> {

    /**
     * 带乐观锁的打分更新：仅当版本号仍是调用方读到的那个值才写入，并把版本号 +1。
     *
     * <p>为什么不用 {@code selectById → updateById}：两位教师同时批改同一道题时，
     * 两次读到的都是「未批改」，随后各自写一次，后写的把先写的分数盖掉，
     * 双方都收到「打分成功」——这是最糟糕的一类并发 bug，因为它不报错。
     * 条件更新把「谁抢到了」交给数据库裁决，返回 0 行时明确告诉后到的那位「请刷新」。
     *
     * @return 受影响行数；1 表示本次打分生效，0 表示期间已被其他人改过
     */
    @Update("""
            UPDATE exam_answer
               SET score = #{score}, marked = 1, correct = #{correct}, mark_version = mark_version + 1
             WHERE id = #{id} AND mark_version = #{expectedVersion}
            """)
    int markWithVersion(@Param("id") Long id,
                        @Param("expectedVersion") Integer expectedVersion,
                        @Param("score") BigDecimal score,
                        @Param("correct") Integer correct);

    /**
     * 查询某场考试中「已交卷学生」的全部主观题作答（含学生、题干、参考答案），供教师批改。
     * 已批改的记录也会返回（marked=1），便于教师回看与调整分数；
     * 只统计已交卷（es.status >= 2）的学生，避免把还在答题的学生算进待批改。
     */
    @Select("""
            SELECT ea.id            AS answer_id,
                   es.student_id    AS student_id,
                   su.real_name     AS student_name,
                   pq.id            AS paper_question_id,
                   pq.sort          AS sort,
                   pq.content       AS content,
                   pq.answer        AS standard_answer,
                   pq.score         AS full_score,
                   ea.answer        AS student_answer,
                   ea.score         AS score,
                   ea.marked        AS marked
            FROM exam_answer ea
            JOIN exam_student es   ON es.id = ea.exam_student_id
            JOIN sys_user su       ON su.id = es.student_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            WHERE es.exam_id = #{examId} AND es.status >= 2 AND (ea.is_objective = 0 OR ea.marked = 0)
            ORDER BY su.real_name, pq.sort
            """)
    List<MarkItemVO> selectSubjective(@Param("examId") Long examId);

    /**
     * 按知识点聚合某场考试的作答正确率（错题分析的数据来源）。
     * 只统计终评答卷中已评分的作答；主观题以「是否得满分」判定为对。
     * 结果按正确率升序，最薄弱的知识点排在最前面。
     */
    @Select("""
            SELECT p.subject_id, s.subject_name,
                   k.kp_id AS knowledge_point_id, k.name AS knowledge_point,
                   COUNT(*) AS total,
                   SUM(CASE WHEN ea.marked = 1 AND ea.correct = 1 THEN 1 ELSE 0 END) AS correct_count
            FROM exam_answer ea
            JOIN exam_student es   ON es.id = ea.exam_student_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            JOIN paper p ON p.id=pq.paper_id
            LEFT JOIN subject s ON s.id=p.subject_id
            LEFT JOIN LATERAL (
                SELECT kp_id,name FROM paper_question_knowledge WHERE paper_question_id=pq.id
                UNION ALL
                SELECT NULL::BIGINT,COALESCE(NULLIF(TRIM(t.label),''),'未标注')
                FROM regexp_split_to_table(COALESCE(pq.knowledge_point,''),'[,，;；|]') t(label)
                WHERE NOT EXISTS(SELECT 1 FROM paper_question_knowledge WHERE paper_question_id=pq.id)
            ) k ON TRUE
            WHERE es.exam_id = #{examId} AND es.status = 3 AND ea.marked = 1
            GROUP BY p.subject_id, s.subject_name,k.kp_id,k.name
            ORDER BY (SUM(CASE WHEN ea.marked = 1 AND ea.correct = 1 THEN 1 ELSE 0 END) * 1.0 / COUNT(*)) ASC
            """)
    List<KnowledgeStatVO> selectKnowledgeStats(@Param("examId") Long examId);

    /**
     * 某个学生的跨考试知识点掌握度（学生错题本 / 学情分析用）。
     * 只统计已判定（marked=1）的作答，未批改的主观题不计入，避免拉低正确率。
     */
    @Select("""
            SELECT p.subject_id, s.subject_name,
                   k.kp_id AS knowledge_point_id, k.name AS knowledge_point,
                   COUNT(*) AS total,
                   SUM(CASE WHEN ea.correct = 1 THEN 1 ELSE 0 END) AS correct_count
            FROM exam_answer ea
            JOIN exam_student es   ON es.id = ea.exam_student_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            JOIN exam e ON e.id = es.exam_id
            JOIN paper p ON p.id=pq.paper_id
            LEFT JOIN subject s ON s.id=p.subject_id
            LEFT JOIN LATERAL (
                SELECT kp_id,name FROM paper_question_knowledge WHERE paper_question_id=pq.id
                UNION ALL
                SELECT NULL::BIGINT,COALESCE(NULLIF(TRIM(t.label),''),'未标注')
                FROM regexp_split_to_table(COALESCE(pq.knowledge_point,''),'[,，;；|]') t(label)
                WHERE NOT EXISTS(SELECT 1 FROM paper_question_knowledge WHERE paper_question_id=pq.id)
            ) k ON TRUE
            WHERE es.student_id = #{studentId} AND es.status = 3 AND ea.marked = 1 AND e.release_status = 2 AND e.end_time <= CURRENT_TIMESTAMP
            GROUP BY p.subject_id, s.subject_name,k.kp_id,k.name
            ORDER BY (SUM(CASE WHEN ea.correct = 1 THEN 1 ELSE 0 END) * 1.0 / COUNT(*)) ASC
            """)
    List<KnowledgeStatVO> selectStudentKnowledgeStats(@Param("studentId") Long studentId);

    /**
     * 待批改的主观题数量（教师首页）。管理员查全量，教师只看自己创建的考试。
     * 用 &lt;script&gt; 做条件拼接，避免用「参数 IS NULL」这种依赖方言的写法。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM exam_answer ea
            JOIN exam_student es ON es.id = ea.exam_student_id
            JOIN exam e          ON e.id = es.exam_id
            WHERE ea.is_objective = 0 AND ea.marked = 0 AND es.status &gt;= 2
            <if test="createBy != null"> AND e.create_by = #{createBy} </if>
            </script>
            """)
    long countPendingMark(@Param("createBy") String createBy);

    /**
     * 学生错题本：跨考试收集做错的题（含主观题得分未满）。
     *
     * 判定口径：客观题看 correct = 0；主观题看「已批改且得分 < 满分」——
     * 只按 correct 判定会漏掉「主观题拿了部分分」的情况，那不是掌握牢固。
     * 分页由 MyBatis-Plus 分页插件完成（第一个参数为 IPage）。
     */
    @Select("""
            <script>
            SELECT ea.id                AS answer_id,
                   e.id                 AS exam_id,
                   e.exam_name          AS exam_name,
                   pq.id                AS paper_question_id,
                   pq.sort              AS sort,
                   pq.type              AS type,
                   pq.content           AS content,
                   pq.options           AS options,
                   ea.answer            AS my_answer,
                   pq.answer            AS standard_answer,
                   pq.analysis          AS analysis,
                   pq.knowledge_point   AS knowledge_point,
                   pq.score             AS full_score,
                   ea.score             AS got_score,
                   es.submit_time       AS submit_time
            FROM exam_answer ea
            JOIN exam_student es   ON es.id = ea.exam_student_id
            JOIN exam e            ON e.id = es.exam_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            WHERE es.student_id = #{studentId}
              AND es.status &gt;= 2 AND e.release_status = 2 AND e.end_time &lt;= CURRENT_TIMESTAMP
              AND (
                    (ea.is_objective = 1 AND ea.correct = 0)
                 OR (ea.is_objective = 0 AND ea.marked = 1 AND ea.score &lt; pq.score)
              )
            <if test="knowledgePoint != null and knowledgePoint != ''">
              AND (EXISTS(SELECT 1 FROM paper_question_knowledge pk WHERE pk.paper_question_id=pq.id AND pk.name=#{knowledgePoint}) OR EXISTS(SELECT 1 FROM regexp_split_to_table(COALESCE(pq.knowledge_point,''),'[,，;；|]') t(label) WHERE TRIM(t.label)=#{knowledgePoint}))
            </if>
            ORDER BY es.submit_time DESC, pq.sort ASC
            </script>
            """)
    IPage<WrongQuestionVO> selectWrongQuestions(IPage<WrongQuestionVO> page,
                                                @Param("studentId") Long studentId,
                                                @Param("knowledgePoint") String knowledgePoint);

    /**
     * 门户「科目题库进度」之已刷数：某学生跨全部考试作答过的题目数（按源题去重）。
     *
     * <p>口径说明：exam_answer 关联的是 paper_question 快照，同一源题可能出现在多份卷子里，
     * 因此 COUNT(DISTINCT pq.question_id) 回落到 question 维度——与 totalQuestions 同一口径，
     * 否则「已刷 15 / 共 10 题」这种荒谬数字会直接出现在首页。
     * 「作答过」= 留下过非空答案（不管是否批改），断点续练语义即「刷没刷过」。
     */
    @Select("""
            SELECT q.subject_id                  AS subject_id,
                   COUNT(DISTINCT pq.question_id) AS done_count
            FROM exam_answer ea
            JOIN exam_student es   ON es.id = ea.exam_student_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            JOIN question q        ON q.id = pq.question_id
            WHERE es.student_id = #{studentId}
              AND ea.answer IS NOT NULL AND ea.answer <> ''
            GROUP BY q.subject_id
            """)
    List<SubjectStatVO> selectStudentDoneBySubject(@Param("studentId") Long studentId);
}
