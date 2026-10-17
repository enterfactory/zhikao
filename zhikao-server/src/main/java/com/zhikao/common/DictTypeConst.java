package com.zhikao.common;

import java.util.List;

/**
 * 内置字典类型编码常量。
 *
 * 内置字典由 {@code DataInitializer} 初始化，允许改文案/排序/颜色，
 * 但**禁止删除类型本身**——它们的取值被前端展示层直接引用，删掉会让页面出现空白选项。
 */
public final class DictTypeConst {

    /** 题型 */
    public static final String QUESTION_TYPE = "question_type";

    /** 难度 */
    public static final String DIFFICULTY = "difficulty";

    /** 用户角色 */
    public static final String USER_ROLE = "user_role";

    /** 考试状态 */
    public static final String EXAM_STATUS = "exam_status";

    /** 考试发布状态 */
    public static final String EXAM_PUBLISHED = "exam_published";

    /** 学生作答状态 */
    public static final String EXAM_STUDENT_STATUS = "exam_student_status";

    /** 试卷生成方式 */
    public static final String PAPER_GEN_TYPE = "paper_gen_type";

    /** 试卷状态 */
    public static final String PAPER_STATUS = "paper_status";

    /** AI 任务类型 */
    public static final String AI_TASK_TYPE = "ai_task_type";

    /** AI 任务状态 */
    public static final String AI_TASK_STATUS = "ai_task_status";

    /** 内置类型集合，删除时据此拦截 */
    private static final List<String> BUILT_IN = List.of(
            QUESTION_TYPE, DIFFICULTY, USER_ROLE, EXAM_STATUS, EXAM_PUBLISHED,
            EXAM_STUDENT_STATUS, PAPER_GEN_TYPE, PAPER_STATUS, AI_TASK_TYPE, AI_TASK_STATUS);

    private DictTypeConst() {
    }

    /** 是否内置字典 */
    public static boolean isBuiltIn(String dictType) {
        return BUILT_IN.contains(dictType);
    }

    /** 全部内置类型 */
    public static List<String> all() {
        return BUILT_IN;
    }
}
