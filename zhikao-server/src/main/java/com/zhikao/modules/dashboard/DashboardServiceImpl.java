package com.zhikao.modules.dashboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.ExamStatusEnum;
import com.zhikao.common.ExamStudentStatusEnum;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import com.zhikao.modules.dashboard.vo.DashboardVO;
import com.zhikao.modules.dashboard.vo.MetricVO;
import com.zhikao.modules.dashboard.vo.TodoItemVO;
import com.zhikao.modules.dashboard.vo.TrendPointVO;
import com.zhikao.modules.exam.ExamService;
import com.zhikao.modules.exam.GradeService;
import com.zhikao.modules.exam.vo.StudentExamVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 首页看板服务实现。
 *
 * 设计要点：指标一律从**既有 Service / Mapper 聚合**而来，不新写一套口径。
 * 这与 Agent 工具层遵守同一条原则——「看板看到的数字 = 页面上看到的数字 = Agent 查到的数字」。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 趋势图统计天数 */
    private static final int TREND_DAYS = 7;

    /** 学生首页展示的薄弱知识点条数 */
    private static final int WEAK_POINT_LIMIT = 5;

    /** 薄弱知识点阈值：正确率低于该值才算薄弱 */
    private static final BigDecimal WEAK_ACCURACY = new BigDecimal("60");

    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionMapper questionMapper;
    private final ExamService examService;
    private final GradeService gradeService;

    public DashboardServiceImpl(ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                                ExamAnswerMapper examAnswerMapper, QuestionMapper questionMapper,
                                ExamService examService, GradeService gradeService) {
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.questionMapper = questionMapper;
        this.examService = examService;
        this.gradeService = gradeService;
    }

    @Override
    public DashboardVO overview() {
        String role = LoginUtil.roleCode();
        return UserTypeEnum.STUDENT.getRole().equals(role) ? studentDashboard() : staffDashboard(role);
    }

    // ==================== 教师 / 管理员 ====================

    /** 教师与管理员共用一套聚合，差别只在「待批改」是否按创建人过滤 */
    private DashboardVO staffDashboard(String role) {
        DashboardVO vo = new DashboardVO();
        vo.setRole(role);
        String username = LoginUtil.username();
        vo.setGreeting("欢迎回来，" + displayName(role));

        String scope = UserTypeEnum.ADMIN.getRole().equals(role) ? null : username;
        long running = examMapper.selectCount(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getPublished, 1)
                .eq(Exam::getStatus, ExamStatusEnum.RUNNING.getCode()).eq(scope!=null,Exam::getCreateBy,scope));
        long draft = examMapper.selectCount(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getPublished, 0).eq(scope!=null,Exam::getCreateBy,scope));
        // 管理员看全量待批改；教师只看自己创建的考试（与越权修复后的口径保持一致）
        long pendingMark = examAnswerMapper.countPendingMark(scope);
        long questionCount = questionMapper.selectCount(null);

        vo.getMetrics().add(metric("running", "进行中考试", running, "场",
                "已发布且当前处于考试时间窗内", running > 0 ? "warning" : "primary"));
        vo.getMetrics().add(metric("pending_mark", "待批改主观题", pendingMark, "条",
                "已交卷但主观题尚未打分的作答", pendingMark > 0 ? "danger" : "success"));
        vo.getMetrics().add(metric("draft", "草稿考试", draft, "场",
                "已创建但尚未发布，学生不可见", draft > 0 ? "warning" : "success"));
        vo.getMetrics().add(metric("question", "题库题量", questionCount, "题",
                "全部科目的题目总数", "primary"));

        vo.setTrend(buildTrend(examMapper.countByDayScoped(daysAgo(),scope), examMapper.avgScoreByDayScoped(daysAgo(),scope)));

        if (pendingMark > 0) {
            vo.getTodos().add(todo("pending_mark", "有 " + pendingMark + " 条主观题待批改",
                    "建议在考试结束后尽快完成批改，学生才能看到终评成绩",
                    pendingMark, "/teacher/marking", "danger"));
        }
        if (draft > 0) {
            vo.getTodos().add(todo("draft_exam", "有 " + draft + " 场考试还是草稿",
                    "确认时间窗与班级后发布，学生即可看到", draft, "/teacher/exam", "warning"));
        }
        if (running > 0) {
            vo.getTodos().add(todo("monitor", "有 " + running + " 场考试正在进行",
                    "可查看实时监控：已进入 / 已交卷人数与得分概览", running, "/teacher/exam", "primary"));
        }
        if (vo.getTodos().isEmpty()) {
            vo.getTodos().add(todo("idle", "暂无待办", "所有考试与批改都已处理完毕", 0L, "/teacher/question", "success"));
        }
        return vo;
    }

    // ==================== 学生 ====================

    private DashboardVO studentDashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setRole(UserTypeEnum.STUDENT.getRole());
        vo.setGreeting("欢迎回来，同学");

        // 复用考试服务：看板与「我的考试」页面天然同一口径
        List<StudentExamVO> exams = examService.studentExamList();
        long toTake = exams.stream()
                .filter(item -> item.getMyStatus() == null)
                .filter(item -> !Objects.equals(item.getStatus(), ExamStatusEnum.FINISHED.getCode()))
                .count();
        long done = exams.stream()
                .filter(item -> item.getMyStatus() != null
                        && item.getMyStatus() >= ExamStudentStatusEnum.SUBMITTED.getCode())
                .count();
        // Released history is independent of the student's current class; normalize before averaging.
        List<BigDecimal> myScores = gradeService.releasedGrades(LoginUtil.userId()).stream()
                .filter(g -> g.getTotalScore() != null && g.getPaperTotalScore() != null && g.getPaperTotalScore().signum() > 0)
                .map(g -> g.getTotalScore().multiply(BigDecimal.valueOf(100)).divide(g.getPaperTotalScore(), 10, RoundingMode.HALF_UP))
                .toList();
        BigDecimal avg = myScores.isEmpty()
                ? null
                : myScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(myScores.size()), 2, RoundingMode.HALF_UP);

        vo.getMetrics().add(metric("to_take", "待参加考试", toTake, "场",
                "已发布、尚未交卷的考试", toTake > 0 ? "warning" : "success"));
        vo.getMetrics().add(metric("done", "已完成考试", done, "场",
                "已交卷（含已阅卷）的考试", "primary"));
        vo.getMetrics().add(metric("avg", "平均得分率", avg == null ? "—" : avg.toPlainString(), "%",
                "已结束、已发布的终评成绩逐场折算百分比后平均；无成绩显示 —",
                avg == null ? "primary" : avg.compareTo(new BigDecimal("60")) >= 0 ? "success" : "danger"));

        // 薄弱知识点：正确率升序的前 N 个
        List<KnowledgeStatVO> stats = examAnswerMapper.selectStudentKnowledgeStats(LoginUtil.userId());
        List<KnowledgeStatVO> weak = new ArrayList<>();
        int weakTotal = 0;
        for (KnowledgeStatVO stat : stats) {
            stat.setAccuracy(accuracy(stat));
            if (stat.getAccuracy().compareTo(WEAK_ACCURACY) < 0) {
                weakTotal++;
                if (weak.size() < WEAK_POINT_LIMIT) weak.add(stat);
            }
        }
        vo.setWeakPoints(weak);
        vo.getMetrics().add(metric("weak", "薄弱知识点", weakTotal, "个",
                "正确率低于 60% 的知识点，建议优先复习", weak.isEmpty() ? "success" : "danger"));

        vo.setTrend(buildTrend(null,
                examStudentMapper.avgScoreByDayForStudent(LoginUtil.userId(), daysAgo())));

        for (StudentExamVO exam : exams) {
            if (toTake == 0) {
                break;
            }
            boolean pending = exam.getMyStatus() == null
                    && !Objects.equals(exam.getStatus(), ExamStatusEnum.FINISHED.getCode());
            if (pending) {
                vo.getTodos().add(todo("to_take_exam",
                        "待参加：" + exam.getExamName(),
                        exam.getStartTime() + " 开始，答题时长 " + exam.getDuration() + " 分钟",
                        1L, "/student/exam", "warning"));
            }
        }
        if (vo.getTodos().isEmpty()) {
            vo.getTodos().add(todo("idle", "暂无待参加考试", "可以到「我的成绩」回顾错题与解析", 0L,
                    "/student/grade", "success"));
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 把「场次」与「平均分」两条序列按日期对齐，前端拿到一组日期 + 两个数组即可直接画图 */
    private List<TrendPointVO> buildTrend(List<TrendPointVO> counts, List<TrendPointVO> scores) {
        Map<LocalDate, TrendPointVO> merged = new LinkedHashMap<>();
        // 先铺满 7 天，保证没有数据的日期也有点（否则折线会跳日期）
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate statDay = today.minusDays(i);
            TrendPointVO point = new TrendPointVO();
            point.setStatDay(statDay);
            point.setTotal(0L);
            point.setAvgValue(null);
            merged.put(statDay, point);
        }
        if (counts != null) {
            for (TrendPointVO item : counts) {
                TrendPointVO point = merged.get(item.getStatDay());
                if (point != null && item.getTotal() != null) {
                    point.setTotal(item.getTotal());
                }
            }
        }
        if (scores != null) {
            for (TrendPointVO item : scores) {
                TrendPointVO point = merged.get(item.getStatDay());
                if (point != null && item.getAvgValue() != null) {
                    point.setAvgValue(item.getAvgValue().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private LocalDateTime daysAgo() {
        return LocalDate.now().minusDays(TREND_DAYS - 1L).atStartOfDay();
    }

    private BigDecimal accuracy(KnowledgeStatVO stat) {
        int total = stat.getTotal() == null ? 0 : stat.getTotal();
        int correct = stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private MetricVO metric(String key, String label, Object value, String unit,
                            String hint, String color) {
        MetricVO vo = new MetricVO();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setValue(String.valueOf(value));
        vo.setUnit(unit);
        vo.setHint(hint);
        vo.setColor(color);
        return vo;
    }

    private TodoItemVO todo(String type, String title, String description,
                            Long count, String path, String level) {
        TodoItemVO vo = new TodoItemVO();
        vo.setType(type);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setCount(count);
        vo.setPath(path);
        vo.setLevel(level);
        return vo;
    }

    private String displayName(String role) {
        return switch (role) {
            case "admin" -> "管理员";
            case "teacher" -> "老师";
            default -> "同学";
        };
    }
}
