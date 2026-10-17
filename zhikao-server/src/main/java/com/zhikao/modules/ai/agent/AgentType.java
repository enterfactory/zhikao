package com.zhikao.modules.ai.agent;

import java.util.List;
import java.util.Set;

/**
 * Agent 类型：二次开发的「四个专家 + 一个主控」。
 *
 * 设计取舍（重要）：五个 Agent **共用同一套运行时**（ReActAgent + Toolkit），
 * 差别在「系统提示词 + 可用工具白名单 + 可使用的角色」。为什么不写成五个继承层次？
 * 因为它们的差异是「配置」而不是「行为」——用一个枚举描述配置，
 * 比五个近乎相同的类更容易维护，也不会出现「改了 A 忘了改 B」。
 *
 * 主控 Agent 额外挂载四个专家的 {@code SubAgentTool}，
 * 于是「意图识别 → 委派专家」成为模型的一次工具调用，而不是我们硬编码的路由 if-else。
 *
 * <p><b>角色可见性（三期新增）</b>：{@link #getRoles()} 声明每个 Agent 对哪些角色开放。
 * 学生看不到「出题助手 / 阅卷助手」——不是把它们藏起来装作不存在，
 * 而是学生确实没有题库管理与批改的职责；把入口给出去只会让模型给出
 * 「请到题库管理页新建题目」这种学生点不开的建议。
 */
public enum AgentType {

    /** 主控：默认入口，可自行查数据，也可委派给专家 */
    COORDINATOR("coordinator", "主控助手",
            "通用教学助手，能查询题库、试卷、考试、成绩等数据，并把专项问题委派给专家",
            """
                    你是「智考在线」教学系统的主控助手。你可以直接调用只读工具查询系统数据，
                    也可以把专项任务委派给专家：出题找 QuestionAgent、阅卷找 MarkAgent、学情分析找 AnalyticsAgent。
                    硬性要求：
                    1. 只依据工具返回的数据作答；数据不足就明确说「系统里查不到」，绝不编造；
                    2. 涉及具体数字时必须来自工具结果，不要凭印象估计；
                    3. 回答简洁分点，不要寒暄，不要复述工具原始输出；
                    4. 你没有任何写权限，需要创建/修改数据时，只能给出建议并提示用户到对应页面操作。
                    """,
            Tools.ALL_READ,
            AiPermissions.STUDENT, AiPermissions.TEACHER, AiPermissions.ADMIN),

    /** 出题专家 */
    QUESTION("question", "出题助手",
            "按科目、知识点、题型、难度生成题目草稿，并做质量自检",
            """
                    你是出题专家。职责：按用户给定的科目 / 知识点 / 题型 / 难度 / 数量生成题目。
                    工作流程：
                    1. 先用 search_questions 查重，避免与题库已有题目雷同；
                    2. 可用 search_knowledge 检索该知识点的定义与典型考法，让题目贴合大纲；
                    3. 生成后**自检**：选项数是否合法（单选/多选至少 2 个）、答案是否命中选项、
                       判断题答案是否只有「对/错」、难度是否与要求一致；不合格就重写；
                    4. 最后用 draft_question 把结果登记为草稿返回。
                    硬性要求：你只能产出草稿，**绝对不能声称已经入库**——入库必须教师在题库页面确认。
                    """,
            Tools.QUESTION,
            AiPermissions.TEACHER, AiPermissions.ADMIN),

    /** 阅卷专家 */
    MARK("mark", "阅卷助手",
            "为主观题给出评分建议与扣分点，并结合同题得分分布做一致性检查",
            """
                    你是主观题阅卷助手。职责：为主观题给出评分建议。
                    工作流程：
                    1. 先调用 get_mark_context 拿到该题的满分、班级得分分布与已有批改情况；
                    2. 若学生的分数与同题分布明显偏离（例如超过均值 ±20%），必须在理由中主动提示
                       「该分数偏离同题水平，建议复核」——这是你最重要的价值；
                    3. 给出建议分（不超过满分）+ 评分依据 + 具体扣分点。
                    硬性要求：你只给建议，**不写库**；最终分数由教师确认。
                    """,
            Tools.MARK,
            AiPermissions.TEACHER, AiPermissions.ADMIN),

    /** 学情分析专家 */
    ANALYTICS("analytics", "学情分析助手",
            "分析班级或个人学情，输出薄弱知识点与可执行的复习建议",
            """
                    你是学情分析专家。职责：把统计数据变成可执行的教学/复习建议。
                    工作流程：
                    1. 用 list_exams 定位考试，再用 get_exam_stat / get_knowledge_stats 取到事实数据；
                    2. 分析个人时用 get_student_grade、get_wrong_questions；
                    3. 正确率低于 60% 视为薄弱；
                    4. 结论要落到「下一步做什么」：具体知识点 + 建议的练习方式与题量。
                    硬性要求：**所有数字必须来自工具**；数据为空时直接说明「暂无作答数据」，
                    不要用「一般来说」「通常」来填补空白。
                    """,
            Tools.ANALYTICS,
            AiPermissions.STUDENT, AiPermissions.TEACHER, AiPermissions.ADMIN),

    /** 系统助手 */
    ASSISTANT("assistant", "系统助手",
            "用自然语言查询系统数据、解释状态、指引操作路径",
            """
                    你是「智考在线」的系统助手，面向已登录的老师或管理员。
                    职责：回答「有哪些考试」「某场考试什么状态」「题库有多少题」这类查询，
                    并给出对应的操作路径指引（例如「到『考试管理』页面发布」）。
                    硬性要求：只依据工具返回的数据回答；查不到就说明查不到；
                    不要编造页面路径或功能。
                    """,
            Tools.ASSISTANT,
            AiPermissions.TEACHER, AiPermissions.ADMIN);

    /** 工具白名单：把工具名收敛在一处声明，避免「提示词让模型别用、但工具其实注册了」的错配 */
    public static final class Tools {
        static final Set<String> ALL_READ = Set.of(
                "list_subjects", "list_classes", "search_questions", "get_question_stats",
                "list_papers", "list_exams", "get_exam_monitor", "get_exam_stat",
                "get_student_grade", "get_knowledge_stats", "get_wrong_questions",
                "list_pending_mark", "get_mark_context", "search_knowledge", "get_my_profile");
        static final Set<String> QUESTION = Set.of(
                "list_subjects", "search_questions", "get_question_stats",
                "search_knowledge", "draft_question");
        static final Set<String> MARK = Set.of(
                "get_mark_context", "list_pending_mark", "search_knowledge", "get_exam_stat");
        static final Set<String> ANALYTICS = Set.of(
                "list_exams", "get_exam_stat", "get_knowledge_stats", "get_student_grade",
                "get_wrong_questions", "get_exam_monitor", "list_subjects", "search_knowledge");
        static final Set<String> ASSISTANT = Set.of(
                "list_subjects", "list_classes", "list_exams", "get_exam_monitor",
                "get_question_stats", "list_papers", "list_pending_mark", "search_questions");

        private Tools() {
        }
    }

    /** 前端传参用的短标识 */
    private final String code;

    private final String label;

    /** 供子 Agent 工具的描述使用：模型据此判断何时委派 */
    private final String description;

    private final String sysPrompt;

    /** 该 Agent 可用的工具名集合（角色维度会再过滤一次） */
    private final Set<String> toolNames;

    /** 该 Agent 对哪些角色开放。列表内角色才可能看到这个入口 */
    private final Set<String> roles;

    AgentType(String code, String label, String description, String sysPrompt,
              Set<String> toolNames, String... roles) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.sysPrompt = sysPrompt;
        this.toolNames = toolNames;
        this.roles = Set.of(roles);
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getSysPrompt() {
        return sysPrompt;
    }

    public Set<String> getToolNames() {
        return toolNames;
    }

    public Set<String> getRoles() {
        return roles;
    }

    /** 该角色能否使用这个 Agent（前端只展示能用的，后端也会再校验一次） */
    public boolean visibleTo(String role) {
        return role != null && roles.contains(role);
    }

    /** 该角色可用的 Agent 列表：前端「智能助手」页据此渲染能力清单 */
    public static List<AgentType> forRole(String role) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.visibleTo(role))
                .toList();
    }

    /** 按 code 解析，未识别时回退到主控（不抛异常：前端传错不该让对话直接失败） */
    public static AgentType of(String code) {
        if (code != null) {
            for (AgentType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
        }
        return COORDINATOR;
    }

    /** 四个可被主控委派的专家（主控自己不是专家） */
    public static List<AgentType> specialists() {
        return List.of(QUESTION, MARK, ANALYTICS, ASSISTANT);
    }

    /** 是否专家（可被委派） */
    public boolean isSpecialist() {
        return this != COORDINATOR;
    }
}
