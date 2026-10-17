package com.zhikao.modules.crawler.parse;

import java.util.List;

/**
 * 一篇文档的解析结论（七期「解析日志」的数据载体）。
 *
 * <p>为什么不返回一个 List 就完事：线上最常见的失败不是报错，而是<b>「取字成功、解析出 0 题」</b>——
 * 只返回题目列表时，这条路径和「这份文档本来就没题」长得一模一样，管理员只能对着 0 干瞪眼。
 * 所以这里把「谁认领的、出了几题、为什么是 0」一起带出来，由流水线落到 crawl_raw 的解析列上。
 *
 * @param parserName 实际产出题目的解析器名；null 表示没有任何解析器成功产出
 * @param parsed     解析产物（可能为空）
 * @param note       人可读的结果说明：0 题原因 / 解析异常；正常出题为 null
 * @param attempted  认领过但没出结果的解析器名（用于定位「被谁抢走了」）
 */
public record ParseOutcome(String parserName,
                           List<ParsedQuestion> parsed,
                           String note,
                           List<String> attempted) {

    public int parsedCount() {
        return parsed == null ? 0 : parsed.size();
    }

    public static String nameOf(QuestionParser parser) {
        if (parser == null) {
            return null;
        }
        String simple = parser.getClass().getSimpleName();
        return simple.replaceFirst("Parser$", "");
    }
}
