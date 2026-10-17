package com.zhikao.modules.crawler.extract;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 文档抽取器 SPI：把一种文档格式变成纯文本。
 *
 * <p>与解析器（{@code QuestionParser}）的分工要分清：抽取器管「文件 → 文字」，
 * 解析器管「文字 → 题目字段」。所以抽取器完全不关心题号、选项、答案，
 * 只负责把文档里的字老老实实取出来（该 OCR 就 OCR）。
 * 新增一种格式（比如 pptx、ofd）只要加一个 @Component 实现，采集主流程不用改。
 */
public interface DocumentExtractor {

    /** 是否负责这个文件名（一般按扩展名判断） */
    boolean supports(String fileName);

    /** 抽取文本；失败抛 {@link com.zhikao.common.BusinessException}，由采集层记录并跳过该文件 */
    ExtractedText extract(Path file);

    /** 取小写扩展名（无扩展名返回空串）：各实现的 supports 共用同一口径 */
    static String extensionOf(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? "" : lower.substring(dot + 1);
    }
}

