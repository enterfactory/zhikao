package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.Exam;
import com.zhikao.modules.dashboard.vo.TrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试 Mapper。
 */
@Mapper
public interface ExamMapper extends BaseMapper<Exam> {

    @org.apache.ibatis.annotations.Select("SELECT * FROM exam WHERE id = #{id} FOR UPDATE")
    Exam lockById(@org.apache.ibatis.annotations.Param("id") Long id);

    /**
     * 状态推进（一）：已过结束时间的考试置为已结束。
     * 考试状态由时间窗推导，用批量 UPDATE 一次刷新，避免逐条读改写。
     */
    @Update("UPDATE exam SET status = 2 WHERE published = 1 AND end_time < #{now} AND status <> 2")
    int syncFinished(@Param("now") LocalDateTime now);

    /** 状态推进（二）：处于时间窗内的考试置为进行中 */
    @Update("UPDATE exam SET status = 1 WHERE published = 1 AND start_time <= #{now} AND end_time >= #{now} AND status <> 1")
    int syncRunning(@Param("now") LocalDateTime now);

    /** 状态推进（三）：开始时间未到的考试回到未开始（用于时间被修改的场景） */
    @Update("UPDATE exam SET status = 0 WHERE published = 1 AND start_time > #{now} AND status <> 0")
    int syncNotStarted(@Param("now") LocalDateTime now);

    /**
     * 近 N 天「开考场次」趋势：按开始时间所在日期聚合。
     * 用 CAST(... AS DATE) 而不是数据库专属函数，H2 与 MySQL 通用；
     * 别名避开 day / value 等保留字（见 TrendPointVO 的说明）。
     */
    @Select("""
            SELECT CAST(start_time AS DATE) AS stat_day, COUNT(*) AS total
            FROM exam
            WHERE start_time >= #{from} AND published = 1
              AND (CAST(#{owner} AS TEXT) IS NULL OR create_by=#{owner})
            GROUP BY CAST(start_time AS DATE)
            ORDER BY stat_day
            """)
    List<TrendPointVO> countByDayScoped(@Param("from") LocalDateTime from,@Param("owner") String owner);
    default List<TrendPointVO> countByDay(LocalDateTime from){return countByDayScoped(from,null);}

    /**
     * 近 N 天「平均得分率走势」：只统计终评，逐份答卷折算百分比后平均。
     * 教师/管理员查询可包含未发布成绩；学生查询另行执行发布策略。
     */
    @Select("""
            SELECT CAST(es.submit_time AS DATE) AS stat_day, AVG(es.total_score * 100.0 / p.total_score) AS avg_value
            FROM exam_student es
            JOIN exam e ON e.id = es.exam_id
            JOIN paper p ON p.id=e.paper_id
            WHERE es.submit_time >= #{from} AND es.status = 3 AND p.total_score > 0
              AND (CAST(#{owner} AS TEXT) IS NULL OR e.create_by=#{owner})
            GROUP BY CAST(es.submit_time AS DATE)
            ORDER BY stat_day
            """)
    List<TrendPointVO> avgScoreByDayScoped(@Param("from") LocalDateTime from,@Param("owner") String owner);
    default List<TrendPointVO> avgScoreByDay(LocalDateTime from){return avgScoreByDayScoped(from,null);}
}
