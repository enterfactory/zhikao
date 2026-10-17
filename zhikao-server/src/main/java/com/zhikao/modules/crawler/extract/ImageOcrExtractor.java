package com.zhikao.modules.crawler.extract;

import com.zhikao.modules.crawler.ocr.OcrService;
import com.zhikao.modules.crawler.ocr.OcrText;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;

/**
 * 图片文档抽取器：png / jpg / jpeg / bmp / gif / webp 直接进 OCR。
 *
 * <p>这是七期最主要的入口场景——老师手机拍的试卷、微信里传来传去的题目截图。
 * 图片没有「文本层」可走，识别不到字就是真的没字，所以这里不做任何降级：
 * OCR 通道不可用时由 {@link OcrService} 抛出带指引的业务异常，一路冒到任务错误信息里。
 */
@Component
public class ImageOcrExtractor implements DocumentExtractor {

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp");

    private final OcrService ocrService;

    public ImageOcrExtractor(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public boolean supports(String fileName) {
        return EXTENSIONS.contains(DocumentExtractor.extensionOf(fileName));
    }

    @Override
    public ExtractedText extract(Path file) {
        OcrText recognized = ocrService.recognize(file);
        return ExtractedText.ofOcr(recognized.text(), recognized.score(), recognized.engine());
    }
}
