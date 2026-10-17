package com.zhikao.modules.crawler.parse;

import com.zhikao.modules.crawler.clean.RichTextSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown / txt 模板解析器：按「题干 + 选项 A./B./C./D. + 答案 + 解析」的分段正则解析。
 *
 * <p>分段规则：以空行分隔题目块；块内按行识别——
 * 题干（{@code 题干：/题目：/N. 题干：} 起头，或选项之前的首行）、选项（{@code A./A、/A)}）、
 * 答案（{@code 答案：/正确答案：}）、解析（{@code 解析：/【解析】}）。
 * Markdown 的标题（#）、引用（>）行天然不含这些标记，会被跳过，正好当作注释/水印。
 *
 * <p>这里只负责「拆字段」，学段/科目/难度归类交给 ClassifierTagger，避免解析器背太多职责。
 * 解析阶段<b>不剥 HTML 标签</b>：题干里的 {@code <table>}、{@code <img src>}、{@code $公式$}
 * 原样透出，能净化成什么形态统一由 Cleaner 的白名单规则决定（一处规则，两处解析器共用）。
 */
@Component
public class MarkdownTemplateParser implements QuestionParser {

    /** 选项行：行首 A-D（半/全角）+ 分隔符 */
    private static final Pattern OPTION_LINE =
            Pattern.compile("^\\s*([A-Da-d])[\\.．、）\\)]\\s*(.+)$");
    /** 题干起头 */
    private static final Pattern STEM_LINE =
            Pattern.compile("^\\s*(?:\\d+[\\.、\\)])?\\s*(?:题干|题目|原题)\\s*[:：]\\s*(.+)$");
    /** 答案起头 */
    private static final Pattern ANSWER_LINE =
            Pattern.compile("^\\s*(?:答案|正确答案|参考答案|Answer)\\s*[:：]\\s*(.*)$");
    /** 解析起头 */
    private static final Pattern ANALYSIS_LINE =
            Pattern.compile("^\\s*(?:【?解析】?|答案解析|讲解)\\s*[:：]?\\s*(.*)$");

    @Override
    public boolean supports(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // 兜底解析器：不是 JSON 行，且【任意一行】出现题干/答案/选项标记就认领。
        // 这里必须逐行判定——三个正则都带 ^ 锚点，对整篇多行文本用 find() 只会命中首行，
        // 而 md 首行往往是标题（# …），那样会把整篇误判为「不认领」，一道题都出不来。
        if (content.trim().startsWith("{")) {
            return false;
        }
        for (String line : content.split("\\R")) {
            String trimmed = line.replaceAll("^\\s*[#>]\\s*", "").trim();
            if (STEM_LINE.matcher(trimmed).find()
                    || ANSWER_LINE.matcher(trimmed).find()
                    || OPTION_LINE.matcher(trimmed).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int order() {
        return 200; // 模板解析是兜底，优先级最低
    }

    @Override
    public List<ParsedQuestion> parse(String content) {
        List<ParsedQuestion> result = new ArrayList<>();
        // 先把 Markdown 表格压成一行 <table> HTML：表格天然多行，不先合并就会被下面的
        // 「空行分块 + 逐行识别」拆成碎片。转换只增不改语义，非表格行原样保留。
        String withTables = RichTextSupport.markdownTablesToHtml(content);
        // 再剥 Markdown 结构噪音（标题井号、引用符号），保留行结构
        String cleaned = withTables.replaceAll("(?m)^\\s*[#>]\\s*", "");
        // PDFBox emits CRLF on Windows. Normalize before finding blank lines: a
        // backtracking \R\s*\R can otherwise split a single CRLF into two breaks.
        cleaned = cleaned.replace("\r\n", "\n").replace('\r', '\n');
        boolean explicitStems = cleaned.lines().anyMatch(line -> STEM_LINE.matcher(line).matches());
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : cleaned.split("\n", -1)) {
            boolean boundary = explicitStems ? STEM_LINE.matcher(line).matches() : line.isBlank();
            if (boundary && !current.isEmpty()) {
                blocks.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) blocks.add(current.toString());
        for (String block : blocks) {
            ParsedQuestion parsed = parseBlock(block);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    /** 解析一个题目块；题干与答案都为空时视为噪音块，返回 null */
    private ParsedQuestion parseBlock(String block) {
        if (block == null || block.isBlank()) {
            return null;
        }
        String[] lines = block.split("\\R");
        ParsedQuestion q = new ParsedQuestion();
        Map<String, String> options = new LinkedHashMap<>();
        StringBuilder firstLineFallback = new StringBuilder();
        boolean stemFound = false;
        boolean answerFound = false;
        boolean inAnalysis = false;

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher answer = ANSWER_LINE.matcher(line);
            Matcher analysis = ANALYSIS_LINE.matcher(line);
            Matcher stem = STEM_LINE.matcher(line);
            Matcher option = OPTION_LINE.matcher(line);

            if (analysis.matches()) {
                inAnalysis = true;
                q.setAnalysis(join(q.getAnalysis(), analysis.group(1).trim()));
            } else if (answer.matches()) {
                inAnalysis = false;
                answerFound = true;
                q.setAnswer(answer.group(1).trim());
            } else if (stem.matches()) {
                inAnalysis = false;
                stemFound = true;
                q.setContent(stem.group(1).trim());
            } else if (option.matches()) {
                // 选项只出现在题干之后、答案之前；一旦进入答案/解析段就不再认选项
                if (!answerFound && !inAnalysis) {
                    options.put(option.group(1).toUpperCase(), option.group(2).trim());
                }
            } else if (!stemFound && !answerFound && !inAnalysis) {
                // 没有显式「题干：」标记时，把首行（非选项/答案/解析）当题干兜底
                firstLineFallback.append(firstLineFallback.isEmpty() ? line : " " + line);
            } else if (inAnalysis) {
                q.setAnalysis(join(q.getAnalysis(), line));
            } else if (stemFound && !answerFound) {
                // 题干跨行续写
                q.setContent(join(q.getContent(), line));
            }
        }

        // 题干：优先显式标记，其次首行兜底
        if (q.getContent() == null || q.getContent().isBlank()) {
            if (!firstLineFallback.isEmpty()) {
                q.setContent(firstLineFallback.toString().trim());
            } else {
                return null; // 既无标记题干也无首行，判定为噪音块
            }
        }
        // 无答案的块不成其为题，丢弃（如纯说明段）
        if (q.getAnswer() == null || q.getAnswer().isBlank()) {
            return null;
        }
        q.setOptions(options);
        q.setQuestionType(inferType(q));
        return q;
    }

    /**
     * 题型启发：有选项→单选/多选（按答案字母个数）；无选项但答案像「对/错/√/×」→判断；
     * 题干含填空标记→填空；其余→简答。最终以 Cleaner 的字典校验为准。
     */
    private Integer inferType(ParsedQuestion q) {
        boolean hasOptions = q.getOptions() != null && !q.getOptions().isEmpty();
        String answer = q.getAnswer() == null ? "" : q.getAnswer().replaceAll("\\s", "").toUpperCase();
        if (hasOptions) {
            // 答案里字母个数 > 1 视为多选
            long letters = answer.chars().filter(c -> c >= 'A' && c <= 'D').count();
            return letters > 1 ? 2 : 1;
        }
        if (answer.matches("^(对|错|√|×|T|F|TRUE|FALSE|正确|错误)$")
                || (q.getContent() != null && (q.getContent().contains("判断") || q.getContent().contains("正误")))) {
            return 3;
        }
        String stem = q.getContent() == null ? "" : q.getContent();
        if (stem.contains("____") || stem.contains("＿") || stem.contains("（　）") || stem.contains("( )")) {
            return 4;
        }
        return 5;
    }

    private String join(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " " + addition;
    }
}
