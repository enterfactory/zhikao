package com.zhikao.common;

import java.util.Arrays;

/**
 * 客观题自动判分。
 * 判分规则集中在此，避免散落在 Service 中难以核对；后续加题型只需扩展这里。
 */
public final class AnswerJudge {

    private AnswerJudge() {
    }

    /**
     * 判断一道客观题的作答是否正确。
     *
     * @param type     题型（question.type）
     * @param standard 标准答案
     * @param actual   学生作答
     * @return 是否正确；主观题、空作答、非法题型一律返回 false（不自动给分）
     */
    public static boolean correct(Integer type, String standard, String actual) {
        QuestionTypeEnum questionType = QuestionTypeEnum.of(type);
        if (questionType == null || !questionType.isObjective()) {
            return false;
        }
        if (standard == null || actual == null || standard.isBlank() || actual.isBlank()) {
            return false;
        }
        return switch (questionType) {
            // 单选、判断：要求完全一致
            case SINGLE -> normalize(standard).equals(normalize(actual));
            // 多选：忽略书写顺序（AB 与 BA 视为相同）
            case MULTIPLE -> sortLetters(standard).equals(sortLetters(actual));
            case JUDGE -> normalizeJudge(standard) != null
                    && normalizeJudge(standard).equals(normalizeJudge(actual));
            // 填空：支持「一空多解」，见 matchBlank
            case BLANK -> matchBlank(standard, actual);
            case ESSAY -> false;
        };
    }

    /** 通用归一化：去掉所有空白并转大写 */
    private static String normalize(String text) {
        return text.replaceAll("\\s+", "").toUpperCase();
    }

    /** 多选归一化：去空白、去分隔符、转大写后按字符排序 */
    private static String sortLetters(String text) {
        String value = normalize(text).replace(",", "").replace("，", "");
        char[] chars = value.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    /** 判断归一化：只接受明确的正反判断词；无法识别的输入不匹配任何答案 */
    private static String normalizeJudge(String text) {
        String value = normalize(text);
        return switch (value) {
            case "对", "正确", "T", "TRUE", "1", "Y", "YES" -> "TRUE";
            case "错", "错误", "F", "FALSE", "0", "N", "NO" -> "FALSE";
            default -> null;
        };
    }

    /**
     * 填空判分。
     * - 单空：标准答案用 | 给出多个可接受写法（如「北京|首都」），命中其一即正确。
     * - 多空：标准答案各空用 ; 分隔，逐空比对，空数不一致直接判错。
     */
    private static boolean matchBlank(String standard, String actual) {
        String[] standardBlanks = standard.split(";", -1);
        if (standardBlanks.length > 1) {
            String[] studentBlanks = actual.split(";", -1);
            if (studentBlanks.length != standardBlanks.length) {
                return false;
            }
            for (int i = 0; i < standardBlanks.length; i++) {
                if (!hitAny(standardBlanks[i], studentBlanks[i])) {
                    return false;
                }
            }
            return true;
        }
        return hitAny(standard, actual);
    }

    /** 单个空：命中任一可接受写法即正确 */
    private static boolean hitAny(String standardOne, String actualOne) {
        String student = normalize(actualOne);
        if (student.isEmpty()) {
            return false;
        }
        for (String candidate : standardOne.split("\\|")) {
            if (normalize(candidate).equals(student)) {
                return true;
            }
        }
        return false;
    }
}
