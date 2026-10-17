package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.ExamStudent;
import com.zhikao.modules.dashboard.vo.TrendPointVO;
import com.zhikao.modules.exam.vo.GradeItemVO;
import com.zhikao.modules.portal.vo.LeaderboardEntryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试-学生参与记录 Mapper。
 */
@Mapper
public interface ExamStudentMapper extends BaseMapper<ExamStudent> {

    @Select("SELECT * FROM exam_student WHERE id = #{id} FOR UPDATE")
    ExamStudent lockById(@Param("id") Long id);

    /**
     * 查询某场考试的成绩明细（仅已交卷学生），按总分降序。
     * 一次 JOIN 取回学生姓名与班级名，避免逐行回查。
     */
    @Select("""
            SELECT es.id                AS exam_student_id,
                   es.student_id        AS student_id,
                   su.username          AS username,
                   su.real_name         AS student_name,
                   COALESCE(er.class_name,cl.class_name) AS class_name,
                   es.status            AS status,
                   es.enter_time        AS enter_time,
                   es.submit_time       AS submit_time,
                   es.objective_score   AS objective_score,
                   es.subjective_score  AS subjective_score,
                   es.total_score       AS total_score,
                   es.cheat_count       AS cheat_count
            FROM exam_student es
            JOIN sys_user su      ON su.id = es.student_id
            LEFT JOIN exam_roster er ON er.exam_id=es.exam_id AND er.student_id=es.student_id
            LEFT JOIN sys_class cl ON cl.id = su.class_id
            WHERE es.exam_id = #{examId} AND es.status >= 2
            ORDER BY CASE WHEN es.status=3 THEN 0 ELSE 1 END, es.total_score DESC NULLS LAST, es.student_id ASC
            """)
    List<GradeItemVO> selectGrades(@Param("examId") Long examId);

    /**
     * 条件更新：仅当当前状态等于 expectStatus 时才推进为 newStatus。
     * 交卷用它做幂等保证——并发重复交卷时只有一次能更新成功（受影响行数 = 1），
     * 其余返回 0，从而避免重复判分（数据库层面的并发控制，不依赖外部锁组件）。
     *
     * @return 受影响行数；1 表示本次更新成功，0 表示状态已被其他请求改变
     */
    @Update("UPDATE exam_student SET status = #{newStatus} WHERE id = #{id} AND status = #{expectStatus}")
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expectStatus") Integer expectStatus,
                            @Param("newStatus") Integer newStatus);

    /**
     * 查出「仍在考试中」的参与记录，供定时任务判断是否已超时。
     *
     * <p>这里<b>故意不在 SQL 里算截止时间</b>：截止时间是
     * {@code min(进入时间 + 时长, 考试结束时间)}，两个数据库的时间加减语法不同
     * （H2 是 DATEADD，MySQL 是 DATE_ADD ... INTERVAL），写在 SQL 里就会变成「只能在一个库上跑」。
     * 所以 SQL 只做粗筛（已开始且未交卷），精确判断交给 Java 的 {@code ExamTimeSupport}——
     * 与前端倒计时、保存作答用的是同一套口径，不会出现「页面说还有 5 分钟，定时任务说他超时了」。
     *
     * @param limit 单次处理上限：一次扫描不拖成大事务，超出的留给下一轮
     */
    @Select("""
            SELECT es.id, es.exam_id, es.student_id, es.status, es.enter_time, es.submit_time,
                   es.objective_score, es.subjective_score, es.total_score, es.cheat_count, es.submit_type
            FROM exam_student es
            JOIN exam e ON e.id = es.exam_id
            WHERE es.status = #{running} AND e.published = 1 AND e.start_time <= #{now}
            ORDER BY es.id
            LIMIT #{limit}
            """)
    List<ExamStudent> selectRunningForTimeout(@Param("running") Integer running,
                                              @Param("now") LocalDateTime now,
                                              @Param("limit") int limit);

    /**
     * 某个学生近 N 天的平均得分率走势（逐份答卷折算百分比后取平均）。
     * 只统计已结束、已发布的终评（status = 3），满分无效的答卷不纳入。
     */
    @Select("""
            SELECT CAST(es.submit_time AS DATE) AS stat_day, AVG(es.total_score * 100.0 / p.total_score) AS avg_value
            FROM exam_student es JOIN exam e ON e.id=es.exam_id JOIN paper p ON p.id=e.paper_id
            WHERE e.release_status>=1 AND e.end_time<=CURRENT_TIMESTAMP AND es.student_id = #{studentId}
              AND es.submit_time >= #{from}
              AND es.status = 3 AND p.total_score > 0
            GROUP BY CAST(es.submit_time AS DATE)
            ORDER BY stat_day
            """)
    List<TrendPointVO> avgScoreByDayForStudent(@Param("studentId") Long studentId,
                                              @Param("from") LocalDateTime from);

    /**
     * 门户「近 7 天积分榜」：按学生聚合窗口内已阅卷考试的总分合计，全量返回（不截断）。
     *
     * <p>计分口径：
     * <ul>
     *   <li>窗口：submit_time 在近 7 天内且 status=3（已阅卷）——只认终值成绩，
     *       与「成绩走势」图同一口径，避免未批改的低分污染排名；</li>
     *   <li>得分：SUM(total_score)，多场累加，鼓励持续练习而非一场躺赢；</li>
     *   <li>排序：同分先交卷者靠前（MIN(submit_time)），再按 user_id 保证稳定序；</li>
     *   <li>名次由服务层按返回顺序编号并截取 top 10——SQL 不做 LIMIT，
     *       因为还要在「不在前 10 的当前用户」身上算真实名次，截断后名次要再查一次库。</li>
     * </ul>
     */
    @Select("""
            SELECT es.student_id       AS user_id,
                   u.real_name         AS name,
                   SUM(es.total_score) AS score
            FROM exam_student es
            JOIN sys_user u ON u.id = es.student_id
            JOIN exam e ON e.id=es.exam_id
            WHERE e.release_status>=1 AND e.end_time<=CURRENT_TIMESTAMP AND es.status = 3 AND es.submit_time >= #{from}
            GROUP BY es.student_id, u.real_name
            ORDER BY score DESC, MIN(es.submit_time) ASC, es.student_id ASC
            """)
    List<LeaderboardEntryVO> selectRecentScoreRanking(@Param("from") LocalDateTime from);
}
