package com.zhikao.modules.crawler.parse;

import java.util.List;

/**
 * 解析器 SPI：把一段原始内容（网页正文 / 文件文本）拆成若干 {@link ParsedQuestion}。
 *
 * <p>选择策略不是「按 source_type 一对一」那么粗——同一个 LOCAL_DIR 源里可能混着 json/md/txt，
 * 所以 SPI 以 supports(内容) 自适应：能认领这条内容的解析器才 parse。
 * 没有任何解析器认领时，原文留在 crawl_raw 不丢弃，可后续补解析器重跑。
 *
 * <p>order 越小优先级越高：结构化解析（JSON 行）先于模板解析（Markdown/txt 兜底）尝试。
 */
public interface QuestionParser {

    /** 是否认领这段内容 */
    boolean supports(String content);

    /** 优先级，越小越先 */
    default int order() {
        return 100;
    }

    /** 解析为题目雏形列表；内容能读但解析不出题时返回空列表（不抛，原文仍在 raw） */
    List<ParsedQuestion> parse(String content);
}
