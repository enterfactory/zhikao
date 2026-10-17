package com.zhikao.modules.ai.agent;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI 工具的角色闸门 —— 「三类角色能用什么」的唯一声明处。
 *
 * <p><b>为什么要有单独一层，而不是只靠提示词或工具内部判断？</b>
 *
 * <ol>
 *   <li><b>提示词不是权限。</b>模型完全可能在被诱导的情况下尝试调用越权工具。
 *       把不在白名单里的工具**从 Toolkit 中摘掉**，模型连「看得到工具名」的机会都没有，
 *       这是能力隔离而不是行为约束。</li>
 *   <li><b>工具内部的判断是第二道门。</b>两者都在，才叫纵深防御：
 *       外层保证「正常情况下调不到」，内层保证「万一调到了也会被拒」。</li>
 *   <li><b>声明集中在一处。</b>想知道「学生能不能导出成绩」，看这一个文件就够了，
 *       不用把 16 个工具方法逐个读完。</li>
 * </ol>
 *
 * <p>角色分层的业务含义：
 * <ul>
 *   <li><b>学生</b>：只看「关于我自己的数据」+ 可做题练习。
 *       成绩、考试、错题、学习画像都是本人维度；题库检索、阅卷、导出、操作日志全部不可见 ——
 *       因为学生既没有这些数据的归属权，也没有管理职责。</li>
 *   <li><b>教师</b>：教学管理侧的全部只读能力（题库、组卷、考试监控、批改参考、导出）。</li>
 *   <li><b>管理员</b>：教师能用的都能用，额外可查操作日志（审计面）。</li>
 * </ul>
 *
 * <p>注意：本表只解决「能不能调用」。调用之后「能看到谁的数据」由
 * {@link AiTools} 里的归属过滤负责（例如学生查成绩时强制注入本人的 student_id）。
 * 两件事必须分开，才不会有「能调工具 = 能看到别人数据」这种漏洞。
 */
public final class AiPermissions {

    public static final String ADMIN = "admin";
    public static final String TEACHER = "teacher";
    public static final String STUDENT = "student";

    /** 学生可用：全部是「本人维度」的只读查询 + 做题自测 */
    private static final Set<String> STUDENT_TOOLS = Set.of(
            "list_subjects",
            "list_exams",              // 内部按班级过滤，只返回本班已发布考试
            "get_student_grade",       // 强制本人 student_id
            "get_wrong_questions",     // 强制本人 student_id
            "get_my_profile",          // 本人的长期记忆画像
            "get_practice_questions",  // 练习取题（对学生隐藏答案）
            "check_answer",            // 自测判对错
            "search_knowledge");       // 本地考纲检索

    /** 教师额外可用：教学管理侧的只读能力 + 出题草稿 + 数据导出 */
    private static final Set<String> TEACHER_EXTRA = Set.of(
            "list_classes",
            "search_questions",
            "get_question_stats",
            "list_papers",
            "get_exam_monitor",
            "get_exam_stat",
            "get_knowledge_stats",
            "list_pending_mark",
            "get_mark_context",
            "draft_question",
            "export_data");

    /** 管理员额外可用：审计面 */
    private static final Set<String> ADMIN_EXTRA = Set.of(
            "list_oper_logs");

    private static final Set<String> TEACHER_TOOLS = union(STUDENT_TOOLS, TEACHER_EXTRA);

    private static final Set<String> ADMIN_TOOLS = union(TEACHER_TOOLS, ADMIN_EXTRA);

    private AiPermissions() {
    }

    /** 该角色可用的工具名集合；未知角色按最小权限（学生）处理 */
    public static Set<String> toolsFor(String role) {
        if (ADMIN.equals(role)) {
            return ADMIN_TOOLS;
        }
        if (TEACHER.equals(role)) {
            return TEACHER_TOOLS;
        }
        return STUDENT_TOOLS;
    }

    /** 该角色是否可用某个工具 */
    public static boolean canUseTool(String role, String toolName) {
        return toolsFor(role).contains(toolName);
    }

    /** 角色中文名，用于给用户写「为什么不能用」的理由 */
    public static String labelOf(String role) {
        if (ADMIN.equals(role)) {
            return "管理员";
        }
        if (TEACHER.equals(role)) {
            return "教师";
        }
        return "学生";
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return Set.copyOf(merged);
    }
}
