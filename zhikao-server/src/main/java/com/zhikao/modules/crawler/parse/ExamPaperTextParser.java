package com.zhikao.modules.crawler.parse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷文本解析器（七期）：把 OCR/文本层取出的一整份试卷拆成一道道题。
 *
 * <p>它存在的理由是<b>真实试卷的写法和人工整理的模板不一样</b>。扫描卷、Word 卷的典型形态：
 * <pre>
 * 一、单项选择题（每题 2 分，共 40 分）
 * 1．下列说法正确的是（　　）
 * A．选项一　　B．选项二　　C．选项三　　D．选项四
 * 【答案】B
 * 【解析】略
 * 2．……
 * </pre>
 * 三个特征把原有的模板解析器顶掉了：答案用<b>方括号标记</b>（{@code 【答案】}）而不是「答案：」；
 * 选项<b>多个挤在同一行</b>；题目之间<b>不一定有空行</b>，真正的分界是题号。
 *
 * <p>所以这里按<b>题号</b>切块而不是按空行切块；块内先按标记定位答案与解析，
 * 剩下「答案之前」的正文再剥选项。
 *
 * <p><b>空白卷也能解析</b>（七期实测修正）：历年高考原题卷的答案另册，全文没有任何答案标记，
 * 旧认领条件「有题号 + 有答案标记」让这类文档出 0 题且不报错。现在认不出答案的块
 * 只在「本文档确实带答案标记（解析版）」时判为讲义片段丢弃；空白卷里它是正常题目，
 * 保留空答案、由 {@code Cleaner} 打 {@code NO_ANSWER} 标记进待审，交审核人补答案或退回。
 *
 * <p><b>与模板解析器的分工</b>：一见到显式的 {@code 题干：} 就主动让位给 {@code MarkdownTemplateParser}
 * （order 更大），因此六期那套人工整理的 md 语料行为完全不变，不会因为本次改动而回归。
 */
@Component
public class ExamPaperTextParser implements QuestionParser {

    /** 标号上限：A-H（超过 8 个选项的题不存在，出现即视为噪声） */
    private static final int MAX_OPTION_INDEX = 7;

    /** 题号行：{@code 1．} / {@code 1、} / {@code 1)} / {@code 1.} 起头，且后面还有内容 */
    private static final Pattern STEM_NUMBER =
            Pattern.compile("^\\s*(\\d{1,3})\\s*[\\.．、)）]\\s*(\\S.*)$");
    /** 答案标记：方括号形态与冒号形态都认；用于块内定位，不要求整行匹配 */
    private static final Pattern ANSWER_LABEL = Pattern.compile(
            "(?:【\\s*(?:参考答案|正确答案|答案区|答案)\\s*】|(?:^|(?<=\\s))(?:正确答案|参考答案|答案)\\s*[:：])\\s*");
    /** 解析标记 */
    private static final Pattern ANALYSIS_LABEL = Pattern.compile(
            "(?:【\\s*(?:答案解析|解析|详解|解答)\\s*】|(?:^|(?<=\\s))(?:答案解析|解析|详解|解答)\\s*[:：])\\s*");
    /** 选项标号：A-H + 分隔符，且必须出现在行首、空白或左括号之后 */
    private static final Pattern OPTION_KEY =
            Pattern.compile("(?:(?<=^)|(?<=[\\s\\u3000])|(?<=[（(]))\\s*([A-H])\\s*[\\.．、)）]\\s*");
    /** 卷面噪声行：页眉页脚、分值说明、大题名——不该进题干 */
    private static final Pattern PAPER_NOISE = Pattern.compile(
            "^\\s*(?:"
                    + "第\\s*\\d+\\s*页.*|共\\s*\\d+\\s*页.*|共\\s*\\d+\\s*小题.*|满分.*|考试时间.*|命题.*|注意事项.*"
                    + "|(?:[一二三四五六七八九十]+)[、．.]\\s*(?:卷|答题|听力|选择题|单选题|多选题|填空题|判断题|简答题|完形填空|阅读理解).*"
                    + "|(?:每题|每空|每小题)[^\\n]{0,10}分[^\\n]*|本卷共[^\\n]*|\\D*\\d+\\s*分\\s*）?\\s*$"
                    + ")");
    /** 模板解析器的显式标记：出现即让位 */
    private static final Pattern TEMPLATE_MARKER =
            Pattern.compile("(?m)^\\s*(?:\\d+[\\.、\\)])?\\s*(?:题干|题目|原题)\\s*[:：]");

    /**
     * 卷面选项标号出现次数（点号形态）：用于判断「这是一份有 A/B/C/D 的卷子」。
     * 与 GLUED_OPTION 同源，只是这里数出现次数，不做粘连分裂。
     */
    private static final Pattern OPTION_MARK = Pattern.compile("(?<![A-Za-z])[A-H][\\.．]");

    /** 题号后面紧跟的分值标注：{@code （5分）} / {@code (5 分)} —— 卷面排版，不属于题干 */
    private static final Pattern SCORE_TAG = Pattern.compile("^\\s*[（(]\\s*\\d+\\s*分\\s*[)）]\\s*");

    /**
     * 粘连选项标号：只认「点号」形态（{@code A.} / {@code A．}），不认顿号。
     *
     * <p>这是为 OCR 准备的：识别引擎常把整行选项当成一个文本框返回，得到
     * {@code A.13B.26C.40D.15} 这种没有空格的粘连串。而「图中 A、B 两地」这类
     * 叙述用的是顿号，把它排除在分裂规则之外，就不会把一句题干切成四个选项。
     */
    private static final Pattern GLUED_OPTION = Pattern.compile("(?<![A-Za-z])([A-H])[\\.．]");

    @Override
    public boolean supports(String content) {
        if (content == null || content.isBlank() || content.trim().startsWith("{")) {
            return false;
        }
        // 人工整理过的模板语料（带「题干：」）交给模板解析器，避免两套规则认领同一篇文档
        if (TEMPLATE_MARKER.matcher(content).find()) {
            return false;
        }
        boolean hasAnswerMark = ANSWER_LABEL.matcher(content).find();
        int stems = 0;
        for (String line : content.split("\\R")) {
            if (STEM_NUMBER.matcher(line.strip()).matches()) {
                stems++;
            }
        }
        if (stems < 2) {
            return false;
        }
        // 认领条件不再要求「全文有答案标记」。空白卷（历年高考原题卷，答案另册）通篇只有
        // 「1．题干（ ）+ A．B．C．D．」的卷面结构，旧条件让这类文档一份题都进不来
        // （实测 2008 全国卷Ⅰ 取字 4885 字、解析出 0 题）。改为卷面结构成立即认领，
        // 缺答案的题目保留为空答案并由 Cleaner 打 NO_ANSWER 标记交人审。
        return hasAnswerMark || OPTION_MARK.matcher(content).results().count() >= 4;
    }

    @Override
    public int order() {
        return 30; // 结构化 JSON(10) 之后、模板兜底(200) 之前
    }

    @Override
    public List<ParsedQuestion> parse(String content) {
        boolean docHasAnswer = ANSWER_LABEL.matcher(content).find();
        List<ParsedQuestion> result = new ArrayList<>();
        for (String block : splitByNumber(spreadGluedOptions(content))) {
            ParsedQuestion parsed = parseBlock(block, docHasAnswer);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    /**
     * 把粘连在一行的选项拆成每行一个（只在必要时动手）。
     *
     * <p>判据是两条同时成立：字母连续递增（A→B→C…）且这一段里至少有一个标号前面顶着非空白字符
     * （真的粘上了）。只看字母会把「A、B 两地」当选项——所以标号只认点号形态；
     * 只看粘连会把本来就分好行的卷面改坏。
     * 分行之后，原有的 {@code OPTION_KEY}（要求行首/空白/左括号之后）就能正常认领了。
     */
    private String spreadGluedOptions(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder out = new StringBuilder(content.length() + 64);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                out.append('\n');
            }
            out.append(spreadLine(lines[index]));
        }
        return out.toString();
    }

    private String spreadLine(String line) {
        List<Match> candidates = new ArrayList<>();
        List<Integer> letters = new ArrayList<>();
        List<Boolean> tight = new ArrayList<>();
        Matcher matcher = GLUED_OPTION.matcher(line);
        while (matcher.find()) {
            char letter = Character.toUpperCase(matcher.group(1).charAt(0));
            candidates.add(new Match(matcher.start(1), matcher.end(), letter - 'A', false));
            letters.add(letter - 'A');
            // 「粘连」= 标号前面直接顶着非空白字符，正常排版的选项标号前面是空白或行首
            tight.add(matcher.start(1) > 0 && !Character.isWhitespace(line.charAt(matcher.start(1) - 1)));
        }
        List<Integer> run = longestIncreasingRun(letters);
        if (run.size() < 2 || run.stream().noneMatch(tight::get)) {
            return line;
        }
        // 以标号位置切成片段：[标号前的内容, A选项, B选项, …]，逐片段回行
        List<String> pieces = new ArrayList<>();
        int cursor = 0;
        for (int position : run) {
            int at = candidates.get(position).start();
            pieces.add(line.substring(cursor, at));
            cursor = at;
        }
        pieces.add(line.substring(cursor));
        StringBuilder spread = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;   // 第一个标号正好在行首时切出来的空片段
            }
            if (spread.length() > 0) {
                spread.append('\n');
            }
            spread.append(piece);
        }
        return spread.toString();
    }

    /** 按题号行切块：题号起头即开新块，其余行（含 OCR 断行）续写进当前块 */
    private List<String> splitByNumber(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = null;
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || PAPER_NOISE.matcher(line).matches()) {
                continue;
            }
            if (STEM_NUMBER.matcher(line).matches()) {
                if (current != null && !current.isEmpty()) {
                    blocks.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                current.append('\n').append(line);
            }
            // 第一道题之前的卷面说明（大题名、分值）没有题号，天然被丢弃
        }
        if (current != null && !current.isEmpty()) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    /**
     * 解析一个题块。
     *
     * @param docHasAnswer 整篇文档是否带答案标记——决定「本块找不到答案」该怎么处理：
     *                     带答案的解析版里那是讲义片段（丢弃），空白卷里那是正常题目（保留空答案）
     */
    private ParsedQuestion parseBlock(String block, boolean docHasAnswer) {
        String text = block.strip();
        // 题号本身不进正式题库，只作分块依据。
        // 只拿块首行去匹配：STEM_NUMBER 的 (\S.*)$ 不跨行，整块多行文本 matches() 恒为 false，
        // 于是「1．」会原样漏进题干——这是本次实测（2008 全国卷）里发现的真实缺陷。
        int lineBreak = text.indexOf('\n');
        String firstLine = lineBreak < 0 ? text : text.substring(0, lineBreak);
        String tail = lineBreak < 0 ? "" : text.substring(lineBreak + 1);
        Matcher number = STEM_NUMBER.matcher(firstLine);
        String stemBody = number.matches()
                ? (tail.isEmpty() ? number.group(2).strip() : number.group(2).strip() + '\n' + tail)
                : text;
        stemBody = SCORE_TAG.matcher(stemBody).replaceFirst("");

        Matcher answer = ANSWER_LABEL.matcher(stemBody);
        String beforeAnswer;
        String answerText;
        String analysisText;
        if (answer.find()) {
            beforeAnswer = stemBody.substring(0, answer.start());
            String afterAnswer = stemBody.substring(answer.end());
            Matcher analysis = ANALYSIS_LABEL.matcher(afterAnswer);
            answerText = analysis.find() ? afterAnswer.substring(0, analysis.start()) : afterAnswer;
            analysisText = analysis.find() ? afterAnswer.substring(analysis.end()) : null;
        } else if (docHasAnswer) {
            return null; // 解析版里认不出答案：那是讲义片段，不是题
        } else {
            beforeAnswer = stemBody;   // 空白卷：整块都是题干，答案留空交人审补
            answerText = "";
            analysisText = null;
        }

        Map<String, String> options = new LinkedHashMap<>();
        String stem = extractOptions(beforeAnswer, options);
        if (stem.isBlank() || (docHasAnswer && answerText.isBlank())) {
            return null;
        }
        ParsedQuestion parsed = new ParsedQuestion();
        parsed.setContent(stem);
        parsed.setOptions(options);
        parsed.setAnswer(answerText.isBlank() ? null : cleanup(answerText));
        parsed.setAnalysis(analysisText == null || analysisText.isBlank() ? null : cleanup(analysisText));
        parsed.setQuestionType(inferType(parsed));
        return parsed;
    }

    /**
     * 从「答案之前」的正文里剥出选项，返回剩余题干。
     *
     * <p>选项标号不能见一个认一个：地理题的「图中 A、B 两地」、数学题的「射线 AB」都会撞上
     * {@code A、} 这种形态。这里的规则是——把全部候选标号按出现顺序排好，
     * 取<b>相邻且字母连续递增</b>的最长一段（真选项天然是 A B C D 连着出现），
     * 并且要求至少 3 个标号；只有当这 2 个标号各行首独立成行（真·一行一选项）才放宽到 2 个。
     */
    private String extractOptions(String body, Map<String, String> options) {
        List<Match> matches = new ArrayList<>();
        List<Integer> letters = new ArrayList<>();
        Matcher matcher = OPTION_KEY.matcher(body);
        while (matcher.find()) {
            int letter = Character.toUpperCase(matcher.group(1).charAt(0)) - 'A';
            matches.add(new Match(matcher.start(), matcher.end(), letter, isAtLineStart(body, matcher.start())));
            letters.add(letter);
        }
        List<Integer> run = longestIncreasingRun(letters);
        boolean twoPerLine = run.size() == 2 && matches.get(run.get(0)).lineStart
                && matches.get(run.get(1)).lineStart;
        if (run.size() < 3 && !twoPerLine) {
            return cleanup(body); // 没有可信的选项序列：整段都是题干
        }
        String stem = body.substring(0, matches.get(run.get(0)).start);
        for (int i = 0; i < run.size(); i++) {
            Match here = matches.get(run.get(i));
            int valueEnd = i + 1 < run.size() ? matches.get(run.get(i + 1)).start : body.length();
            options.put(String.valueOf((char) ('A' + here.letter)), cleanup(body.substring(here.end, valueEnd)));
        }
        return cleanup(stem);
    }

    /** 相邻位置且字母递增 1 的最长连续段（下标序列）；等长时保留更靠前的那段 */
    private List<Integer> longestIncreasingRun(List<Integer> letters) {
        List<Integer> best = List.of();
        for (int start = 0; start < letters.size(); start++) {
            List<Integer> run = new ArrayList<>();
            run.add(start);
            for (int cursor = start + 1; cursor < letters.size()
                    && letters.get(cursor) == letters.get(cursor - 1) + 1
                    && letters.get(cursor) <= MAX_OPTION_INDEX; cursor++) {
                run.add(cursor);
            }
            if (run.size() > best.size()) {
                best = run;
            }
        }
        return best;
    }

    private boolean isAtLineStart(String body, int offset) {
        int index = offset - 1;
        while (index >= 0 && (body.charAt(index) == ' ' || body.charAt(index) == '\t')) {
            index--;
        }
        return index < 0 || body.charAt(index) == '\n';
    }

    /** 题型启发：有选项按答案字母数分单选/多选；无选项看答案形态与题干关键词 */
    private Integer inferType(ParsedQuestion parsed) {
        boolean hasOptions = !parsed.getOptions().isEmpty();
        String answer = parsed.getAnswer() == null ? "" : parsed.getAnswer().replaceAll("\\s", "").toUpperCase();
        if (hasOptions) {
            long letters = answer.chars().filter(c -> c >= 'A' && c <= 'H').count();
            return letters > 1 ? 2 : 1;
        }
        if (answer.matches("^(对|错|√|×|T|F|TRUE|FALSE|正确|错误)$")
                || (parsed.getContent() != null
                && (parsed.getContent().contains("判断") || parsed.getContent().contains("正误")))) {
            return 3;
        }
        String stem = parsed.getContent() == null ? "" : parsed.getContent();
        if (stem.contains("____") || stem.contains("＿") || stem.contains("（　）") || stem.contains("( )")) {
            return 4;
        }
        return 5;
    }

    /** 收尾清理：压掉 OCR 常见的连续空白与换行，去掉空方括号残留 */
    private String cleanup(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("【\\s*】", " ")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n+", " ")
                .trim();
    }

    /** 一个候选选项标号：位置、字母下标、是否位于行首 */
    private record Match(int start, int end, int letter, boolean lineStart) {
    }
}
