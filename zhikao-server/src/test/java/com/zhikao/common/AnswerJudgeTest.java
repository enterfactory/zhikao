package com.zhikao.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判分逻辑单元测试。
 *
 * 为什么优先给这里写测试：判分是**纯函数**且**错了会被学生投诉**的地方。
 * 它不需要数据库、不依赖框架，是最适合用单元测试锁死行为的模块——
 * 一旦有人「顺手优化」了归一化逻辑，这里会立刻红。
 */
@DisplayName("客观题自动判分")
class AnswerJudgeTest {

    /** 单选 = 1、多选 = 2、判断 = 3、填空 = 4、简答 = 5 */
    private static final int SINGLE = 1;
    private static final int MULTIPLE = 2;
    private static final int JUDGE = 3;
    private static final int BLANK = 4;
    private static final int ESSAY = 5;

    @Nested
    @DisplayName("单选题")
    class Single {

        @Test
        @DisplayName("答案一致判对，大小写与空白不影响")
        void sameAnswer() {
            assertTrue(AnswerJudge.correct(SINGLE, "A", "A"));
            assertTrue(AnswerJudge.correct(SINGLE, "A", "a"));
            assertTrue(AnswerJudge.correct(SINGLE, "A", " a "));
        }

        @Test
        @DisplayName("答案不同判错")
        void differentAnswer() {
            assertFalse(AnswerJudge.correct(SINGLE, "A", "B"));
        }
    }

    @Nested
    @DisplayName("多选题")
    class Multiple {

        @Test
        @DisplayName("书写顺序不影响判分（这是最容易被改坏的一条）")
        void orderIgnored() {
            assertTrue(AnswerJudge.correct(MULTIPLE, "ABD", "DBA"));
            assertTrue(AnswerJudge.correct(MULTIPLE, "AB", "BA"));
            assertTrue(AnswerJudge.correct(MULTIPLE, "AC", "ca"));
        }

        @Test
        @DisplayName("逗号分隔与空白也应归一化")
        void separatorIgnored() {
            assertTrue(AnswerJudge.correct(MULTIPLE, "AB", "A,B"));
            assertTrue(AnswerJudge.correct(MULTIPLE, "ABC", " A , B , C "));
        }

        @Test
        @DisplayName("少选或多选都判错（多选不给部分分）")
        void wrongCombination() {
            assertFalse(AnswerJudge.correct(MULTIPLE, "ABD", "AB"));
            assertFalse(AnswerJudge.correct(MULTIPLE, "AB", "ABC"));
        }
    }

    @Nested
    @DisplayName("判断题")
    class Judge {

        @Test
        @DisplayName("对/正确/T/true/1 视为同一答案")
        void trueVariants() {
            assertTrue(AnswerJudge.correct(JUDGE, "对", "正确"));
            assertTrue(AnswerJudge.correct(JUDGE, "对", "T"));
            assertTrue(AnswerJudge.correct(JUDGE, "true", "对"));
            assertTrue(AnswerJudge.correct(JUDGE, "对", "1"));
        }

        @Test
        @DisplayName("错/False/0 视为同一答案")
        void falseVariants() {
            assertTrue(AnswerJudge.correct(JUDGE, "错", "F"));
            assertTrue(AnswerJudge.correct(JUDGE, "错", "false"));
            assertTrue(AnswerJudge.correct(JUDGE, "false", "0"));
        }

        @Test
        @DisplayName("对与错不互通")
        void mixed() {
            assertFalse(AnswerJudge.correct(JUDGE, "对", "错"));
        }
    }

    @Nested
    @DisplayName("填空题")
    class Blank {

        @Test
        @DisplayName("单空多解：命中任一写法即正确")
        void multiAcceptedAnswers() {
            assertTrue(AnswerJudge.correct(BLANK, "北京|首都", "首都"));
            assertTrue(AnswerJudge.correct(BLANK, "北京|首都", "北京"));
            assertFalse(AnswerJudge.correct(BLANK, "北京|首都", "上海"));
        }

        @Test
        @DisplayName("多空：逐空比对，全部命中才正确")
        void multiBlanks() {
            assertTrue(AnswerJudge.correct(BLANK, "80;443", "80;443"));
            assertTrue(AnswerJudge.correct(BLANK, "80;443", " 80 ; 443 "));
        }

        @Test
        @DisplayName("多空数量不一致直接判错（不猜学生意图）")
        void blankCountMismatch() {
            assertFalse(AnswerJudge.correct(BLANK, "80;443", "80"));
            assertFalse(AnswerJudge.correct(BLANK, "80", "80;443"));
        }

        @Test
        @DisplayName("多空中的某一空错误则整题错误")
        void oneBlankWrong() {
            assertFalse(AnswerJudge.correct(BLANK, "80;443", "80;8443"));
        }
    }

    @Nested
    @DisplayName("边界与防御")
    class Guard {

        @Test
        @DisplayName("主观题不参与自动判分")
        void essayNeverAutoScored() {
            assertFalse(AnswerJudge.correct(ESSAY, "参考答案", "参考答案"));
        }

        @Test
        @DisplayName("空作答不给分")
        void emptyAnswer() {
            assertFalse(AnswerJudge.correct(SINGLE, "A", ""));
            assertFalse(AnswerJudge.correct(BLANK, "80", "   "));
        }

        @Test
        @DisplayName("空答案与非法题型安全返回 false，不抛异常")
        void nullAndInvalid() {
            assertFalse(AnswerJudge.correct(SINGLE, null, "A"));
            assertFalse(AnswerJudge.correct(SINGLE, "A", null));
            assertFalse(AnswerJudge.correct(null, "A", "A"));
            assertFalse(AnswerJudge.correct(99, "A", "A"));
        }
    }
}
