package com.zhikao.modules.crawler.extract;

import com.zhikao.common.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 纯文本类文档：md / txt / json / jsonl / csv。
 *
 * <p>这些是「已经有字、只是没结构化」的存量资源（开源题库、老师分享的笔记），
 * 走最快的通道：按 UTF-8 整篇读入，结构化交给解析器。
 */
@Component
public class PlainTextExtractor implements DocumentExtractor {

    private static final Set<String> EXTENSIONS =
            Set.of("md", "markdown", "txt", "json", "jsonl", "csv");

    @Override
    public boolean supports(String fileName) {
        return EXTENSIONS.contains(DocumentExtractor.extensionOf(fileName));
    }

    @Override
    public ExtractedText extract(Path file) {
        try {
            return ExtractedText.ofText(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BusinessException("文本文件读取失败：" + file.getFileName() + "（" + e.getMessage() + "）");
        }
    }
}

