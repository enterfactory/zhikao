package com.zhikao.modules.crawler.quality;

import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.modules.crawler.parse.ParsedQuestion;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档级识别质量评估（七期 P1）：把「这份文档认得怎么样」压成一个可比较的分数。
 *
 * <p><b>为什么是五个加权比率而不是单一判据</b>：每种判据都会误杀——
 * 「解析出题数」看不出题干残缺，「字符数」看不出取出来的是乱码，
 * 「OCR 置信度」对文本层来源根本不存在，「人工审核」一份几百题的卷子等不起。
 * 五个比率分别管住一段：清洗放行率、结构完整度、答案存在率、选项齐全度、识别置信度，
 * 分数用于横向比较与自动决策，明细串（{@link DocQuality#detail()}）用于让人看懂这个分怎么来的。
 *
 * <p><b>为什么算在文档级而不是题级</b>：题级已经有 {@code quality_flags}（NO_ANSWER、OCR_TEXT 等）；
 * 文档级缺的是「这一整份该不该换个更强的通道重认」——那是采集层的决策，
 * 也正是后续分层识别（文本层 → 本地 OCR → 云端 OCR）的升级判据。
 *
 * <p><b>权重是取舍不是真理</b>：入审率占最重（题都进不了待审，其它指标再好也没用），
 * 空槽嫌疑次之（公式/图形丢失是数学卷最常见的静默损失）；
 * 答案存在率对空白卷天然为 0，这是已知代价——明细里会写清楚，人一眼能看出来。
 */
public final class DocQualityEvaluator {

    private static final int WEIGHT_STAGED = 30;
    private static final int WEIGHT_INTACT = 25;
    private static final int WEIGHT_ANSWER = 15;
    private static final int WEIGHT_OPTION = 15;
    private static final int WEIGHT_CONFIDENCE = 15;

    /**
     * 空槽嫌疑：卷面上「本该有内容的位置是空的」。
     *
     * <p>来自实测的 2008 全国卷Ⅰ理数：文本层里公式是图片对象，取字后留下
     * {@code 在△ABC中, = , = .若点D满足 =2 ,则 =(} 这种「等号后面直接跟标点」的残骸。
     * 三个形态够用且都很好解释：等号后紧跟标点、空括号、连续逗号。
     */
    private static final Pattern EMPTY_SLOT = Pattern.compile(
            "[=＝]\\s*[，,、；;。）)？?．.]|[（(]\\s*[)）]|[，,]{2,}");

    private DocQualityEvaluator() {
    }

    /**
     * 评估一份文档的解析质量。
     *
     * @param questions 解析产物（非空）
     * @param staged    清洗打标后进入待审的条数
     * @param viaOcr    正文是否来自 OCR
     * @param ocrScore  OCR 平均置信度（0~1），文本层或引擎未给分时为 null
     */
    public static DocQuality evaluate(List<ParsedQuestion> questions, int staged, boolean viaOcr, Float ocrScore) {
        int total = questions.size();
        if (total == 0) {
            return DocQuality.unparsed();
        }
        int answered = 0;
        int broken = 0;
        int choiceTotal = 0;
        int choiceComplete = 0;
        for (ParsedQuestion question : questions) {
            String content = question.getContent() == null ? "" : question.getContent();
            if (isNotBlank(question.getAnswer())) {
                answered++;
            }
            if (EMPTY_SLOT.matcher(content).find()) {
                broken++;
            }
            if (isChoice(question.getQuestionType())) {
                choiceTotal++;
                if (question.getOptions() != null && question.getOptions().size() >= 2) {
                    choiceComplete++;
                }
            }
        }
        double stagedRate = ratio(staged, total);
        double intactRate = ratio(total - broken, total);
        double answerRate = ratio(answered, total);
        // 没有选择题的文档（纯填空/简答卷）不该因为「选项无从判断」被扣分，记为满分
        double optionRate = choiceTotal == 0 ? 1d : ratio(choiceComplete, choiceTotal);
        double confidenceRate = viaOcr && ocrScore != null ? clamp(ocrScore) : 1d;

        int score = (int) Math.round(WEIGHT_STAGED * stagedRate
                + WEIGHT_INTACT * intactRate
                + WEIGHT_ANSWER * answerRate
                + WEIGHT_OPTION * optionRate
                + WEIGHT_CONFIDENCE * confidenceRate);
        return new DocQuality(score, buildDetail(total, staged, broken, answered,
                choiceTotal, choiceComplete, viaOcr, ocrScore));
    }

    private static String buildDetail(int total, int staged, int broken, int answered,
                                      int choiceTotal, int choiceComplete,
                                      boolean viaOcr, Float ocrScore) {
        StringBuilder detail = new StringBuilder();
        detail.append("解析 ").append(total)
                .append(" · 入审 ").append(staged).append('/').append(total)
                .append(" · 空槽嫌疑 ").append(broken).append('/').append(total)
                .append(" · 有答案 ").append(answered).append('/').append(total);
        if (choiceTotal > 0) {
            detail.append(" · 选项齐全 ").append(choiceComplete).append('/').append(choiceTotal);
        }
        if (!viaOcr) {
            detail.append(" · 文本层取字（精确，无置信度）");
        } else if (ocrScore == null) {
            detail.append(" · OCR 未返回置信度（大模型通道）");
        } else {
            detail.append(" · OCR 置信度 ").append(Math.round(ocrScore * 1000) / 10d).append('%');
        }
        return detail.toString();
    }

    private static boolean isChoice(Integer typeCode) {
        return typeCode != null
                && (typeCode == QuestionTypeEnum.SINGLE.getCode() || typeCode == QuestionTypeEnum.MULTIPLE.getCode());
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static double ratio(int part, int whole) {
        return whole <= 0 ? 0d : clamp((double) part / whole);
    }

    private static double clamp(double rate) {
        return Math.max(0d, Math.min(1d, rate));
    }
}
