package com.zhikao.modules.dashboard.vo;

import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页看板数据（按角色返回不同内容）。
 */
@Data
public class DashboardVO {

    /** 当前角色：admin / teacher / student */
    private String role;

    private String greeting;

    /** 指标卡 */
    private List<MetricVO> metrics = new ArrayList<>();

    /** 近 7 天趋势（场次 + 平均得分率%，无成绩日期 avgValue=null） */
    private List<TrendPointVO> trend = new ArrayList<>();

    private String trendScoreBasis = "final_percentage";

    /** 待办 */
    private List<TodoItemVO> todos = new ArrayList<>();

    /** 学生专有：薄弱知识点（正确率升序前 5） */
    private List<KnowledgeStatVO> weakPoints = new ArrayList<>();
}
