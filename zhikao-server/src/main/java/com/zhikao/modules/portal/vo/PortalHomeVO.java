package com.zhikao.modules.portal.vo;

import lombok.Data;

import java.util.List;

/**
 * 学生门户首页聚合数据（前端契约，字段名不得偏离）。
 */
@Data
public class PortalHomeVO {

    /** 运营轮播帧（上架，按 sort） */
    private List<BannerVO> banners;

    /** 各科目题库进度 */
    private List<SubjectStatVO> subjectStats;

    /** 近 7 天积分榜 top 10 + 本人名次 */
    private List<LeaderboardEntryVO> leaderboard;

    /** 继续练习断点，无断点为 null */
    private ResumeVO resume;

    /** 最近一场未开始/进行中考试，无则为 null */
    private NextExamVO nextExam;

    /** 本人最近 3 条站内通知 */
    private List<NoticeVO> notices;
}
