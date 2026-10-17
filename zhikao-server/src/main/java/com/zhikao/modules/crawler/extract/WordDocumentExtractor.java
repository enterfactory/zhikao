package com.zhikao.modules.crawler.extract;

import com.zhikao.common.BusinessException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Word 文档抽取器：{@code .docx}（POI XWPF）与 {@code .doc}（POI HWPF）。
 *
 * <p>.docx 按<b>文档体顺序</b>遍历段落与表格，而不是「先所有段落再所有表格」——
 * 老师们常把选择题的选项或阅读理解的材料放进表格，顺序一乱，题干和选项就分家了。
 * 表格压成「一行一行、单元格以竖线分隔」的文本，交给解析器时仍能靠 A/B/C/D 标记认出选项。
 *
 * <p>Word 是文本格式，不需要 OCR：走 TEXT 通道，清洗后与直接编辑的 md 题没有区别。
 * 图片型正文（整页截图贴进 Word）不在此抽取范围内，识别成 0 字会被采集层记为「无文本」，
 * 需要 OCR 时应直接上传原图或转成 PDF 扫描件走 OCR 通道。
 */
@Component
public class WordDocumentExtractor implements DocumentExtractor {

    private static final Set<String> DOCX = Set.of("docx");
    private static final Set<String> DOC = Set.of("doc");

    @Override
    public boolean supports(String fileName) {
        String ext = DocumentExtractor.extensionOf(fileName);
        return DOCX.contains(ext) || DOC.contains(ext);
    }

    @Override
    public ExtractedText extract(Path file) {
        String ext = DocumentExtractor.extensionOf(file.getFileName().toString());
        return DOC.contains(ext) ? extractLegacyDoc(file) : extractDocx(file);
    }

    private ExtractedText extractDocx(Path file) {
        try (InputStream in = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    text.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    appendTable(table, text);
                }
            }
            return ExtractedText.ofText(text.toString());
        } catch (Exception e) {
            throw new BusinessException("Word(.docx) 抽取失败：" + file.getFileName() + "（" + e.getMessage() + "）");
        }
    }

    /** 表格按行展开，单元格以 | 分隔：既保住「同一行就是同一题的候选信息」这个位置关系，又是纯文本 */
    private void appendTable(XWPFTable table, StringBuilder text) {
        for (XWPFTableRow row : table.getRows()) {
            String line = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .map(cell -> cell == null ? "" : cell.replaceAll("\\s+", " ").trim())
                    .collect(Collectors.joining(" | "));
            if (!line.isBlank()) {
                text.append(line).append('\n');
            }
        }
        text.append('\n');
    }

    private ExtractedText extractLegacyDoc(Path file) {
        // WordExtractor.close() 会连带关掉底层 HWPFDocument，所以这里只登记 extractor 一个资源
        try (InputStream in = Files.newInputStream(file);
             WordExtractor extractor = new WordExtractor(new HWPFDocument(in))) {
            return ExtractedText.ofText(extractor.getText());
        } catch (Exception e) {
            throw new BusinessException("Word(.doc) 抽取失败：" + file.getFileName() + "（" + e.getMessage() + "）");
        }
    }
}
