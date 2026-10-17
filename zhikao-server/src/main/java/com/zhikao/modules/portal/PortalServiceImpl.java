package com.zhikao.modules.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.LoginUtil;
import com.zhikao.entity.Exam;
import com.zhikao.entity.Paper;
import com.zhikao.entity.Subject;
import com.zhikao.entity.SysBanner;
import com.zhikao.entity.SysLearnResume;
import com.zhikao.entity.SysNotification;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.mapper.SysBannerMapper;
import com.zhikao.mapper.SysLearnResumeMapper;
import com.zhikao.mapper.SysNotificationMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.portal.dto.ResumeSaveDTO;
import com.zhikao.modules.portal.vo.BannerVO;
import com.zhikao.modules.portal.vo.LeaderboardEntryVO;
import com.zhikao.modules.portal.vo.NextExamVO;
import com.zhikao.modules.portal.vo.NoticeVO;
import com.zhikao.modules.portal.vo.PortalHomeVO;
import com.zhikao.modules.portal.vo.ResumeVO;
import com.zhikao.modules.portal.vo.SubjectStatVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学生门户服务实现。
 *
 * <p>首页六块数据一次聚合返回：设计原则是「一次渲染一次请求」——首屏拆成 6 个接口
 * 会让前端串行/并行发 6 次请求、多倍鉴权与查库开销，而数据本身都是小结果集，
 * 聚合接口的总耗时约等于其中最慢的一块。任何一块为空都返回空集合/null，
 * 不阻塞其余块（门户首页不能因为题库没数据就整体报错）。
 */
@Service
public class PortalServiceImpl implements PortalService {

    /** 积分榜统计窗口（天） */
    private static final int LEADERBOARD_WINDOW_DAYS = 7;
    /** 积分榜展示条数 */
    private static final int LEADERBOARD_TOP_N = 10;
    /** 首页通知条数 */
    private static final int NOTICE_LIMIT = 3;

    private final com.zhikao.mapper.LearningProgressMapper progress;
    private final com.zhikao.mapper.ExamRosterMapper roster;
    private final SysBannerMapper bannerMapper;
    private final QuestionMapper questionMapper;
    private final ExamAnswerMapper answerMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamMapper examMapper;
    private final PaperMapper paperMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final SysNotificationMapper notificationMapper;
    private final SysLearnResumeMapper resumeMapper;

    public PortalServiceImpl(com.zhikao.mapper.LearningProgressMapper progress,com.zhikao.mapper.ExamRosterMapper roster,SysBannerMapper bannerMapper, QuestionMapper questionMapper,
                             ExamAnswerMapper answerMapper, ExamStudentMapper examStudentMapper,
                             ExamMapper examMapper, PaperMapper paperMapper,
                             SubjectMapper subjectMapper, SysUserMapper userMapper,
                             SysNotificationMapper notificationMapper,
                             SysLearnResumeMapper resumeMapper) {
        this.roster=roster;this.progress=progress;
        this.bannerMapper = bannerMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.examStudentMapper = examStudentMapper;
        this.examMapper = examMapper;
        this.paperMapper = paperMapper;
        this.subjectMapper = subjectMapper;
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
        this.resumeMapper = resumeMapper;
    }

    @Override
    public PortalHomeVO home() {
        // 全局拦截器已保证登录态；这里再取一次 userId，语义上也把「本人」钉死
        StpUtil.checkLogin();
        Long studentId = LoginUtil.userId();

        PortalHomeVO vo = new PortalHomeVO();
        vo.setBanners(banners());
        vo.setSubjectStats(subjectStats(studentId));
        vo.setLeaderboard(leaderboard(studentId));
        vo.setResume(resume(studentId));
        vo.setNextExam(nextExam(studentId));
        vo.setNotices(notices(studentId));
        return vo;
    }

    // ==================== banners ====================

    /** 上架轮播帧，按 sort 升序（同 sort 按 id，保证顺序稳定） */
    private List<BannerVO> banners() {
        List<SysBanner> list = bannerMapper.selectList(Wrappers.<SysBanner>lambdaQuery()
                .eq(SysBanner::getStatus, 1)
                .orderByAsc(SysBanner::getSort)
                .orderByAsc(SysBanner::getId));
        return list.stream()
                .map(b -> BannerVO.builder()
                        .id(b.getId())
                        .title(b.getTitle())
                        .subtitle(b.getSubtitle())
                        .cta(b.getCta())
                        .theme(b.getTheme())
                        .link(b.getLinkTarget())
                        .build())
                .collect(Collectors.toList());
    }

    // ==================== subjectStats ====================

    /**
     * 各科目「题库总量 + 本人已刷数」。
     * 两次分组查询（题库计数 / 本人作答计数）在内存按 subjectId 合并，
     * 不做一条大 LEFT JOIN——两条口径独立演化时（比如已刷数改按知识点维度）不会互相牵扯。
     */
    private List<SubjectStatVO> subjectStats(Long studentId) {
        return progress.progress(studentId);
    }

    // ==================== leaderboard ====================

    /**
     * 近 7 天积分榜（口径见 ExamStudentMapper#selectRecentScoreRanking 注释）：
     * 全量聚合结果按分数降序编号名次，展示前 10；当前用户若不在前 10，
     * 仍按其真实名次附在末尾（无窗口内成绩则记 0 分、名次 = 参考人数 + 1），
     * 保证「我的名次」永远可见——排行榜看不到自己，比没有排行榜更挫伤参与感。
     */
    private List<LeaderboardEntryVO> leaderboard(Long studentId) {
        LocalDateTime from = LocalDateTime.now().minusDays(LEADERBOARD_WINDOW_DAYS);
        List<LeaderboardEntryVO> rows = examStudentMapper.selectRecentScoreRanking(from);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setRank(i + 1);
        }
        List<LeaderboardEntryVO> result = new ArrayList<>(
                rows.subList(0, Math.min(LEADERBOARD_TOP_N, rows.size())));
        boolean selfInTop = result.stream().anyMatch(r -> studentId.equals(r.getUserId()));
        if (!selfInTop) {
            var self = rows.stream().filter(r -> studentId.equals(r.getUserId())).findFirst();
            if (self.isPresent()) {
                result.add(self.get());
                return result;
            }
            SysUser me = userMapper.selectById(studentId);
            result.add(LeaderboardEntryVO.builder()
                    .userId(studentId)
                    .name(me == null ? "" : me.getRealName())
                    .score(BigDecimal.ZERO)
                    .rank(rows.size() + 1)
                    .build());
        }
        return result;
    }

    // ==================== resume ====================

    /** 读本人练习断点；从未上报过则整块为 null（前端隐藏「继续练习」卡片） */
    private ResumeVO resume(Long studentId) {
        SysLearnResume entity = findResume(studentId);
        if (entity == null) {
            return null;
        }
        return toResumeVO(entity);
    }

    private SysLearnResume findResume(Long studentId) {
        return resumeMapper.selectOne(Wrappers.<SysLearnResume>lambdaQuery()
                .eq(SysLearnResume::getUserId, studentId)
                .last("limit 1"));
    }

    private ResumeVO toResumeVO(SysLearnResume entity) {
        return ResumeVO.builder()
                .subjectName(entity.getSubjectName())
                .setName(entity.getSetName())
                .done(entity.getDone())
                .total(entity.getTotal())
                .accuracy(entity.getAccuracy())
                .lastAt(entity.getLastAt())
                .build();
    }

    @Override
    public void saveResume(ResumeSaveDTO dto) {
        StpUtil.checkLogin();
        Long studentId = LoginUtil.userId();
        LocalDateTime now = LocalDateTime.now();
        SysLearnResume existing = findResume(studentId);
        if (existing == null) {
            resumeMapper.insert(SysLearnResume.builder()
                    .userId(studentId)
                    .subjectId(dto.getSubjectId())
                    .subjectName(dto.getSubjectName().trim())
                    .setName(trimToNull(dto.getSetName()))
                    .done(dto.getDone())
                    .total(dto.getTotal())
                    .accuracy(dto.getAccuracy())
                    .lastAt(now)
                    .createTime(now)
                    .build());
            return;
        }
        // 断点只有一行，直接覆盖为最新状态；done/total 的单调性交给前端练习逻辑约束
        existing.setSubjectId(dto.getSubjectId());
        existing.setSubjectName(dto.getSubjectName().trim());
        existing.setSetName(trimToNull(dto.getSetName()));
        existing.setDone(dto.getDone());
        existing.setTotal(dto.getTotal());
        existing.setAccuracy(dto.getAccuracy());
        existing.setLastAt(now);
        resumeMapper.updateById(existing);
    }

    // ==================== nextExam ====================

    /**
     * 最近一场「未开始 / 进行中」考试：与 myExams（ExamServiceImpl#studentExamList）同源口径——
     * 本人班级 + 已发布；先按时间窗刷新状态（复用 ExamMapper 的三条状态推进 SQL），
     * 再取 start_time 最早且尚未结束的一场。
     */
    private NextExamVO nextExam(Long studentId) {
        LocalDateTime now = LocalDateTime.now();
        examMapper.syncFinished(now);
        examMapper.syncRunning(now);
        examMapper.syncNotStarted(now);

        List<Long> eligible=roster.exams(studentId);if(eligible.isEmpty())return null;
        List<Exam> exams=examMapper.selectList(Wrappers.<Exam>lambdaQuery().eq(Exam::getPublished,1)
                .in(Exam::getId,eligible).in(Exam::getStatus,List.of(0,1)).ge(Exam::getEndTime,now)
                .orderByAsc(Exam::getStartTime).last("LIMIT 1"));
        if (exams.isEmpty()) {
            return null;
        }
        Exam exam = exams.get(0);
        Paper paper = paperMapper.selectById(exam.getPaperId());
        String subjectName = null;
        if (paper != null) {
            Subject subject = subjectMapper.selectById(paper.getSubjectId());
            subjectName = subject == null ? null : subject.getSubjectName();
        }
        return NextExamVO.builder()
                .examId(exam.getId())
                .examName(exam.getExamName())
                .subjectName(subjectName)
                .startAt(exam.getStartTime())
                .build();
    }

    // ==================== notices ====================

    /** 本人最近 3 条站内通知（不区分已读未读——首页要的是「最近发生」，不是「待办」） */
    private List<NoticeVO> notices(Long studentId) {
        LambdaQueryWrapper<SysNotification> wrapper = Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getUserId, studentId)
                .orderByDesc(SysNotification::getId)
                .last("limit " + NOTICE_LIMIT);
        return notificationMapper.selectList(wrapper).stream()
                .map(n -> NoticeVO.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .tag(n.getType())
                        .time(n.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
