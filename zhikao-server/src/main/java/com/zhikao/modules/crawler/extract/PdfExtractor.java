package com.zhikao.modules.crawler.extract;

import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import com.zhikao.modules.crawler.extract.formula.InlineFormulaResolver;
import com.zhikao.modules.crawler.ocr.OcrService;
import com.zhikao.modules.crawler.ocr.OcrText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 抽取器：<b>先试文本层，认不出来再当扫描件处理</b>。
 *
 * <p>这是 PDF 采集的关键分岔，也是主流做法：
 * <ol>
 *   <li>用 PDFBox 逐页取文本层。导出的真题卷、Word 转的 PDF 都带完整文本层，
 *       这条路又快又准（字符与原文一致），且完全不依赖 OCR。</li>
 *   <li>如果平均每页字符数低于阈值（扫描版 PDF 的文本层通常只有水印或干脆为空），
 *       判定为扫描版：逐页 {@link PDFRenderer} 渲染成 PNG 交 OCR。</li>
 * </ol>
 * 判据用「平均每页字符数」而不是「有没有字」——只要有一行页眉/版权水印就非空了，
 * 那样扫描版会永远走不到 OCR 分支。
 *
 * <p>第一条路上还有一层<b>片段级</b>补充（{@link InlineFormulaResolver}）：数学公式在 PDF 里
 * 多是内联图片，文本层压根没有对应字符，于是「题干齐全、式子凭空消失」。
 * 这一层只重认那些小图并把结果插回原位，<b>文本层已有的一律不动</b>；
 * 认不出来的保持原样，所以没有公式的文档行为与加这层之前完全一致。
 *
 * <p>页数受 {@code ocr.max-pages} 限制：扫描版整本书的 OCR 成本是分钟级的，
 * 截断并留日志，好过一次采集挂死四十分钟。
 */
@Component
public class PdfExtractor implements DocumentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractor.class);

    private final CrawlerProperties properties;
    private final OcrService ocrService;
    private final InlineFormulaResolver formulaResolver;

    public PdfExtractor(CrawlerProperties properties, OcrService ocrService,
                        InlineFormulaResolver formulaResolver) {
        this.properties = properties;
        this.ocrService = ocrService;
        this.formulaResolver = formulaResolver;
    }

    @Override
    public boolean supports(String fileName) {
        return "pdf".equals(DocumentExtractor.extensionOf(fileName));
    }

    @Override
    public ExtractedText extract(Path file) {
        CrawlerProperties.Ocr ocr = properties.getOcr();
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                return ExtractedText.ofText("");
            }
            int pages = Math.min(totalPages, ocr.getMaxPages());
            String textLayer = readTextLayer(document, pages);
            if (isRealText(textLayer, totalPages, ocr.getPdfMinCharsPerPage())) {
                return withInlineFormulas(document, file, pages, textLayer);
            }
            log.info("PDF 无可用文本层，转 OCR：{}（{}页）", file.getFileName(), totalPages);
            return ocrPages(document, file, ocr);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PDF 抽取失败：" + file.getFileName() + "（" + e.getMessage() + "）");
        }
    }

    /**
     * 文本层可用时，再补一层「内联公式片段回插」。
     *
     * <p>这一步只对<b>有文本层</b>的 PDF 做，且只在真认回了公式时替换文本：
     * 没有候选图片、或全部认失败，都原样返回原来的文本层结果，
     * 也就是说这份文档的行为与加这段代码之前完全一致。扫描版不走这里——整页 OCR 出来的
     * 文本里公式已经混在里面了，再按图片对象重认只会重复插一遍。
     */
    private ExtractedText withInlineFormulas(PDDocument document, Path file, int pages, String textLayer) {
        if (!properties.getOcr().getFormula().isEnabled()) {
            return ExtractedText.ofText(textLayer);
        }
        try {
            InlineFormulaResolver.Outcome outcome = formulaResolver.resolve(document, pages);
            if (outcome == null) {
                return ExtractedText.ofText(textLayer);
            }
            if (outcome.recovered() == 0) {
                // 一处都没插回也要把「候选几处、哪层调用过」记下来：这正是「这份文档需要更强通道」的证据
                return ExtractedText.ofText(textLayer, ExtractTrace.formulas(outcome));
            }
            log.info("PDF 公式片段回插：{}（{}）", file.getFileName(), outcome.note());
            return ExtractedText.ofText(outcome.text(), ExtractTrace.formulas(outcome));
        } catch (Exception e) {
            // 补公式是锦上添花，文本层是本金：加了就崩的东西不能让它动本金
            log.warn("PDF 公式片段回插失败，保留原文本层：{}（{}）", file.getFileName(), e.getMessage());
            return ExtractedText.ofText(textLayer);
        }
    }

    /** 逐页取文本层，页内按坐标排序，保证跨页的题目段落顺序不乱 */
    private String readTextLayer(PDDocument document, int pages) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        // PDFBox 3 删掉了 2.x 的 setSortByPages；这里本就一页一次取文，
        // 需要的是页内按坐标排序（双栏卷面否则会把左右栏串行）
        stripper.setSortByPosition(true);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= pages; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            text.append(stripper.getText(document)).append('\f');
        }
        return text.toString();
    }

    /** 平均每页字符数达标才算「真有文本层」，否则按扫描版处理 */
    private boolean isRealText(String textLayer, int totalPages, int minCharsPerPage) {
        long meaningful = textLayer.chars().filter(c -> !Character.isWhitespace(c)).count();
        return meaningful / (double) Math.max(1, totalPages) >= minCharsPerPage;
    }

    /**
     * 逐页渲染成 PNG 交 OCR；单页失败只跳过该页，其余照常（一次坏页不该废掉整本）。
     *
     * <p>整本的置信度按<b>该页识别出的字符数加权</b>平均，不是按页数平均：
     * 只认出一个页眉水印的坏页往往分很高，按页算会把整本的可信度抬上去，
     * 反而掩盖了「这一页其实没认出来」这个真正需要被看见的问题。
     */
    private ExtractedText ocrPages(PDDocument document, Path source, CrawlerProperties.Ocr ocr) {
        PDFRenderer renderer = new PDFRenderer(document);
        int pages = Math.min(document.getNumberOfPages(), ocr.getMaxPages());
        StringBuilder text = new StringBuilder();
        double weightedScore = 0d;
        double weight = 0d;
        String channels = null;
        for (int index = 0; index < pages; index++) {
            Path pageImage = null;
            try {
                pageImage = Files.createTempFile("zk-pdf-page-", ".png");
                BufferedImage image = renderer.renderImageWithDPI(index, ocr.getPdfDpi(), ImageType.RGB);
                ImageIO.write(image, "png", pageImage.toFile());
                OcrText page = ocrService.recognize(pageImage);
                // 通道链逐页合并：一本混合扫描里有的页本地兜住、有的页只能靠云端，
                // 两个名字都要留下，否则「这份文档花了多少钱」无从算起
                channels = ExtractTrace.mergeChannels(channels, page.engine());
                if (!page.blank()) {
                    text.append(page.text()).append('\f');
                    if (page.score() != null) {
                        weightedScore += page.score() * page.text().length();
                        weight += page.text().length();
                    }
                }
            } catch (Exception e) {
                log.warn("PDF 第 {} 页 OCR 失败，跳过：{}（{}）", index + 1, source.getFileName(), e.getMessage());
            } finally {
                deleteQuietly(pageImage);
            }
        }
        return ExtractedText.ofOcr(text.toString(), weight == 0 ? null : roundScore(weightedScore / weight), channels);
    }

    private Float roundScore(double score) {
        return (float) (Math.round(score * 10000d) / 10000d);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 临时图删不掉是操作系统的事，不是采集的事
        }
    }
}

