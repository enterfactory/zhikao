package com.zhikao.modules.crawler.parse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 解析器注册表：按 order 升序遍历所有 {@link QuestionParser}，第一个 supports 命中的即负责解析。
 *
 * <p>用 Spring 注入 List&lt;QuestionParser&gt; 收集全部实现，新增解析器只要 @Component 即自动生效，
 * 不改调用方——这是 SPI 的核心价值。
 *
 * <p><b>认领了却没出题，就继续让下一个解析器试</b>（七期实测修正）：原来取第一个认领者解析完就收工，
 * 于是「兜底解析器抢下文档 → 逐块判定不合口味 → 返回空表」变成一次<b>静默失败</b>：
 * 任务显示 SUCCESS、采集到 1 份文档、候选题 0 道、错误栏空白（实测 2008 全国卷Ⅰ 就是这个形状）。
 * 现在空结果与解析异常都会记录下来并交还下一篇候选，最终 0 题时能给出原因。
 */
@Component
public class ParserRegistry {

    private final List<QuestionParser> parsers;

    public ParserRegistry(List<QuestionParser> parsers) {
        // 复制一份并按优先级排序，避免依赖注入顺序
        this.parsers = parsers.stream()
                .sorted(Comparator.comparingInt(QuestionParser::order))
                .toList();
    }

    /** 解析一篇文档，返回带原因的结论（绝不返回 null） */
    public ParseOutcome parse(String content) {
        List<String> claimedButEmpty = new ArrayList<>();
        for (QuestionParser parser : parsers) {
            if (!parser.supports(content)) {
                continue;
            }
            String name = ParseOutcome.nameOf(parser);
            List<ParsedQuestion> parsed;
            try {
                parsed = parser.parse(content);
            } catch (Exception e) {
                return new ParseOutcome(null, List.of(),
                        "解析器 " + name + " 运行异常：" + safeMessage(e), claimedButEmpty);
            }
            if (parsed != null && !parsed.isEmpty()) {
                return new ParseOutcome(name, parsed, null, claimedButEmpty);
            }
            claimedButEmpty.add(name);
        }
        return new ParseOutcome(null, List.of(), reason(claimedButEmpty), claimedButEmpty);
    }

    private String reason(List<String> claimed) {
        if (claimed.isEmpty()) {
            // 这是「卷面和现有解析器对不上」的提示，不是错误：补一个新解析器就是从这里下手的信号
            return "没有解析器认领这份文档（卷面缺题号或选项形态，可能不是试卷）";
        }
        return "认领的解析器未解析出题目：" + String.join("、", claimed) + "（卷面结构与解析规则不匹配）";
    }

    /** 异常摘要：只取 message，没有 message 就退到异常类名，绝不把堆栈写进库 */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
