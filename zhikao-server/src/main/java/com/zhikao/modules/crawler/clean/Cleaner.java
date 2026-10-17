package com.zhikao.modules.crawler.clean;

import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.modules.crawler.parse.ParsedQuestion;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 清洗器（六期）：把解析产物洗成「可入库形态」并做质量判定。
 *
 * <p>动作：<b>富文本白名单净化</b>（{@link RichTextSupport}：保留表格/图片/强调标签与数学定界符，
 * 只剥 script/style/iframe 与事件属性，<b>不再一刀切去标签</b>；顺手把 Markdown 表格转成 {@code <table>}）
 * → 全角转半角 → 连续空白压缩 → 题干非空且≥{@value #MIN_STEM_LENGTH}字
 * → 选项数量与题型匹配（按现有 {@link QuestionTypeEnum} 字典校验）→ 归一化内容 SHA-256 去重。
 *
 * <p>产出 {@link CleanResult}：归一化后的字段 + 质量标记列表
 * （SHORT_CONTENT / NO_ANSWER / OPTIONS_MISMATCH / DUPLICATE，另有流水线与镜像服务追加的 OCR_TEXT、IMG_MIRROR_FAILED）。
 * 图片镜像改写由 {@code ImageMirrorService} 在本步骤之后做，失败只追加 IMG_MIRROR_FAILED 标记，
 * 不影响这里算出的 contentHash（哈希基于净化后、镜像前的内容，保证同一题不会因为图源换域名被判成两题）。
 * 这里不打科目/难度标签——那是 {@code ClassifierTagger} 的职责，两者分开便于各自演进。
 */
@Component
public class Cleaner {

    /** 题干最短长度：短于此基本不构成一道完整的题 */
    public static final int MIN_STEM_LENGTH = 8;

    /** 质量标记常量 */
    public static final String FLAG_DUPLICATE = "DUPLICATE";
    public static final String FLAG_SHORT_CONTENT = "SHORT_CONTENT";
    public static final String FLAG_OPTIONS_MISMATCH = "OPTIONS_MISMATCH";
    /** 图片镜像失败（由 ImageMirrorService 追加）：保留原 URL，让人审时知道这题依赖外网 */
    public static final String FLAG_IMG_MIRROR_FAILED = "IMG_MIRROR_FAILED";
    /** 文本来自 OCR（由流水线追加）：识别结果有概率误差，审核时应重点核对 */
    public static final String FLAG_OCR_TEXT = "OCR_TEXT";
    /** 空白卷产物：卷面没有答案，答案留空待审核人补，绝不因为「没答案」就把整道题丢掉 */
    public static final String FLAG_NO_ANSWER = "NO_ANSWER";

    /**
     * 清洗一条解析产物。
     *
     * @param parsed   解析产物
     * @param seenHash 本任务内已出现的内容哈希集合（方法会就地追加，供后续条去重）
     */
    public CleanResult clean(ParsedQuestion parsed, Set<String> seenHash) {
        CleanResult result = new CleanResult();
        // 1) 字段级归一：剥 HTML、全角转半角、压缩空白
        String content = normalize(parsed.getContent());
        String answer = normalize(parsed.getAnswer());
        String analysis = normalize(parsed.getAnalysis());
        Map<String, String> options = new LinkedHashMap<>();
        parsed.getOptions().forEach((key, value) -> options.put(key.trim().toUpperCase(), normalize(value)));

        Integer type = parsed.getQuestionType();
        result.setContent(content);
        result.setAnswer(answer);
        result.setAnalysis(analysis);
        result.setOptions(options);
        result.setQuestionType(type);

        List<String> flags = new ArrayList<>();

        // 2) 题干非空且足够长
        if (content.isEmpty() || content.length() < MIN_STEM_LENGTH) {
            flags.add(FLAG_SHORT_CONTENT);
        }

        // 3) 空白卷（原题卷答案另册）解析出来就是没答案：照常入待审，只把「答案待补」标出来
        if (answer.isEmpty()) {
            flags.add(FLAG_NO_ANSWER);
        }

        // 4) 选项数量与题型匹配
        if (!optionsMatch(type, options)) {
            flags.add(FLAG_OPTIONS_MISMATCH);
        }

        // 5) 归一化内容 SHA-256 去重（跨条：任务内已存在即判重）
        String hash = sha256(type + "|" + content + "|" + answer);
        result.setContentHash(hash);
        if (!seenHash.add(hash)) {
            flags.add(FLAG_DUPLICATE);
        }

        result.setQualityFlags(flags);
        // 结构性硬伤（空题干）判为不可入库；DUPLICATE/MISMATCH 仍保留给人看，只是打标
        result.setPassed(content.length() >= MIN_STEM_LENGTH);
        return result;
    }

    /** 选项数量与题型匹配：单选/多选需≥2 选项，判断/填空/简答不需选项 */
    private boolean optionsMatch(Integer type, Map<String, String> options) {
        QuestionTypeEnum enumType = QuestionTypeEnum.of(type);
        if (enumType == null) {
            return false;
        }
        boolean hasOptions = options != null && !options.isEmpty();
        return switch (enumType) {
            case SINGLE, MULTIPLE -> hasOptions && options.size() >= 2;
            case JUDGE, BLANK, ESSAY -> true; // 这三类不强制选项
        };
    }

    /**
     * 归一化：Markdown 表格转 HTML → 白名单净化（保留富文本结构，剥可执行内容）
     * → 全角转半角 → 压缩连续空白（含换行）→ trim。
     * 顺序很关键：先补表格再净化，否则净化后表格标签会被空白压缩挤成一行但仍合法；
     * 空白压缩只作用于「标签之间的空白」，HTML 结构与 {@code $...$}、{@code \(...\)} 定界符都不受影响。
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withTables = RichTextSupport.markdownTablesToHtml(text);
        String sanitized = RichTextSupport.sanitize(withTables);
        String half = toHalfWidth(sanitized);
        return half.replaceAll("\\s+", " ").trim();
    }

    /** 全角 → 半角：ASCII 可打印区（空格~波浪号）的全角对应回半角；全角空格转普通空格 */
    private String toHalfWidth(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\u3000') { // 全角空格
                chars[i] = ' ';
            } else if (c >= '\uFF01' && c <= '\uFF5E') { // 全角 ! ~ 
                chars[i] = (char) (c - 0xFEE0);
            }
        }
        return new String(chars);
    }

    /** SHA-256 十六进制；异常时退回 hashCode 字符串（绝不影响主流程） */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
