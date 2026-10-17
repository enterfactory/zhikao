package com.zhikao.modules.crawler.extract;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 抽取器注册表：按文件名挑出负责它的抽取器。
 *
 * <p>与 {@code ParserRegistry} 同一个套路（Spring 注入 List 收集全部实现），
 * 新增格式只要加一个 @Component，采集主流程与上传校验都不用改。
 */
@Component
public class ExtractorRegistry {

    private final List<DocumentExtractor> extractors;

    public ExtractorRegistry(List<DocumentExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    /** 选出负责该文件的抽取器；没有则返回 null（调用方按「不支持的格式」处理） */
    public DocumentExtractor select(String fileName) {
        for (DocumentExtractor extractor : extractors) {
            if (extractor.supports(fileName)) {
                return extractor;
            }
        }
        return null;
    }

    /** 是否支持该文件（遍历目录时的过滤器、上传时的前置校验共用） */
    public boolean supports(String fileName) {
        return select(fileName) != null;
    }
}
