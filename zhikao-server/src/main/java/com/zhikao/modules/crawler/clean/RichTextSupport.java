package com.zhikao.modules.crawler.clean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本保留工具（六期任务#19）：把「采集来的题干/解析」洗成<b>可展示但不含可执行内容</b>的形态。
 *
 * <p>与旧的「一刀切剥标签」相比，这里改成<b>白名单净化</b>：
 * <ul>
 *   <li>保留 {@code table/thead/tbody/tr/td/th/img/b/i/em/strong/sub/sup/br/p/span}，
 *       其中 img 只留 {@code src/alt/width}，其余标签的属性一律丢弃（{@code on*} 事件属性、
 *       {@code style}、{@code href} 天然没了落点，不必再逐个拉黑名单）；</li>
 *   <li>{@code script/style} 连内容一起删，{@code iframe} 只删标签——这三个是 XSS 与
 *       「把题目变成外部页面」的主要入口；</li>
 *   <li>HTML 注释删除，防止 {@code <!--[if IE]><script>} 这类老写法绕过正则；</li>
 *   <li>数学定界符 {@code $...$} 与 {@code \(...\)} 原样保留：本类只操作 {@code <tag>} 结构，
 *       不碰普通字符，定界符自然存活。</li>
 * </ul>
 *
 * <p>另外提供 Markdown 表格 → {@code <table>} HTML 的最小转换：采集源里表格多以
 * {@code | a | b | / | --- | --- |} 形态出现，不转换就会在「压缩空白」后变成一行竖线噪音。
 *
 * <p>为什么写成静态工具而不是 Bean：Cleaner 与 MarkdownTemplateParser 都要用，
 * 且它没有任何外部依赖，注册成 Bean 只会多一层注入。
 */
public final class RichTextSupport {

    /** 保留的标签白名单（小写比较） */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "table", "thead", "tbody", "tr", "td", "th", "img",
            "b", "i", "em", "strong", "sub", "sup", "br", "p", "span");

    /** img 上允许保留的属性（其余全丢，从源头挡掉事件属性与样式注入） */
    private static final Set<String> IMG_ATTRIBUTES = Set.of("src", "alt", "width");

    /** 整个 script/style 元素连内容一起删（?s 让 . 匹配换行，?i 忽略大小写） */
    private static final Pattern DANGEROUS_ELEMENT =
            Pattern.compile("(?is)<\\s*(script|style)\\b.*?<\\s*/\\s*\\1\\s*>");
    /** HTML 注释 */
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    /** iframe 标签（开始与结束都删；内容不是元素体，逐标签清理更稳） */
    private static final Pattern IFRAME_TAG = Pattern.compile("(?i)<\\s*/?\\s*iframe\\b[^>]*>");
    /** 任意标签 */
    private static final Pattern TAG = Pattern.compile("(?is)<\\s*(/?)([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)>");
    /** 标签内的单个属性 */
    private static final Pattern ATTRIBUTE =
            Pattern.compile("(?i)([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    /** 可疑协议：img src 命中就丢掉该属性（javascript: 是老式 XSS 载荷载体） */
    private static final Pattern BAD_PROTOCOL = Pattern.compile("(?i)^\\s*(javascript|vbscript|data:text/html)");

    /** Markdown 表格的分隔行：| --- | :--: | 之类 */
    private static final Pattern TABLE_SEPARATOR =
            Pattern.compile("^\\s*\\|?[\\s:|-]*-{2,}[\\s:|-]*\\|?\\s*$");

    private RichTextSupport() {
    }

    /**
     * 白名单净化：保留富文本结构，剥掉可执行与不可信内容。
     *
     * @param text 原始文本（可含 HTML 片段、Markdown 表格、数学定界符）
     * @return 净化后的文本；入参为 null 时返回空串
     */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String noDangerous = DANGEROUS_ELEMENT.matcher(text).replaceAll(" ");
        String noComment = HTML_COMMENT.matcher(noDangerous).replaceAll(" ");
        String noIframe = IFRAME_TAG.matcher(noComment).replaceAll("");
        Matcher matcher = TAG.matcher(noIframe);
        StringBuilder out = new StringBuilder(noIframe.length());
        while (matcher.find()) {
            String closing = matcher.group(1);
            String name = matcher.group(2).toLowerCase();
            String replacement = ALLOWED_TAGS.contains(name)
                    ? rebuildTag(name, "/".equals(closing), matcher.group(3))
                    : ""; // 非白名单：只删标签本身，保留其中的文字
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Markdown 表格转 HTML 表格（最小实现，够用于「表头 + | --- | + 数据行」这一种写法）。
     * 逐行扫描：命中「表格行 + 分隔行」就吃掉整段表格，压成一行 {@code <table>} HTML，
     * 这样后续按行/按空行分块的解析逻辑不会把表格拆散。
     */
    public static String markdownTablesToHtml(String text) {
        if (text == null || text.indexOf('|') < 0) {
            return text;
        }
        String[] lines = text.split("\\R", -1);
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < lines.length) {
            boolean header = isTableRow(lines[index]);
            boolean separator = index + 1 < lines.length && isTableRow(lines[index + 1])
                    && TABLE_SEPARATOR.matcher(lines[index + 1]).matches();
            if (header && separator) {
                int end = index + 2;
                while (end < lines.length && isTableRow(lines[end])) {
                    end++;
                }
                out.append(buildTable(Arrays.copyOfRange(lines, index, end)));
                index = end;
            } else {
                out.append(lines[index]);
                index++;
            }
            if (index < lines.length) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** 是否表格行（trim 后以竖线起头即可，Markdown 允许结尾竖线省略） */
    private static boolean isTableRow(String line) {
        return line != null && line.trim().startsWith("|");
    }

    /** 组装 table HTML：首行做 thead/th，其余数据行统一包进一个 tbody（多个 tbody 虽合法但没必要） */
    private static String buildTable(String[] tableLines) {
        StringBuilder html = new StringBuilder("<table>");
        html.append("<thead>").append(rowHtml(splitCells(tableLines[0]), true)).append("</thead>");
        StringBuilder body = new StringBuilder();
        for (int i = 2; i < tableLines.length; i++) {
            if (isTableRow(tableLines[i]) && !TABLE_SEPARATOR.matcher(tableLines[i]).matches()) {
                body.append(rowHtml(splitCells(tableLines[i]), false));
            }
        }
        if (body.length() > 0) {
            html.append("<tbody>").append(body).append("</tbody>");
        }
        return html.append("</table>").toString();
    }

    /** 一行 → {@code <tr>}；header 用 th，普通单元格用 td */
    private static String rowHtml(List<String> cells, boolean header) {
        StringBuilder row = new StringBuilder("<tr>");
        String tag = header ? "th" : "td";
        for (String cell : cells) {
            row.append("<").append(tag).append(">").append(cell.trim()).append("</").append(tag).append(">");
        }
        return row.append("</tr>").toString();
    }

    /** 拆一行表格的单元格：去掉首尾竖线后按竖线切分 */
    private static List<String> splitCells(String line) {
        String body = line.trim();
        if (body.startsWith("|")) {
            body = body.substring(1);
        }
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : body.split("\\|", -1)) {
            cells.add(cell);
        }
        return cells;
    }

    /**
     * 按白名单重建标签：只保留该标签允许的属性，属性值里的引号与尖括号做实体化，避免拼回新标签。
     *
     * @param name    标签名（已小写）
     * @param closing 是否闭合标签
     * @param rawAttr 原标签的属性串
     */
    private static String rebuildTag(String name, boolean closing, String rawAttr) {
        if (closing) {
            return "</" + name + ">";
        }
        StringBuilder tag = new StringBuilder("<").append(name);
        if ("img".equals(name)) {
            Matcher matcher = ATTRIBUTE.matcher(rawAttr);
            while (matcher.find()) {
                String key = matcher.group(1).toLowerCase();
                if (!IMG_ATTRIBUTES.contains(key)) {
                    continue;
                }
                String value = unquote(matcher.group(2));
                if ("src".equals(key) && BAD_PROTOCOL.matcher(value).find()) {
                    continue; // 危险协议：整个 src 丢掉，宁可让图不显示也不留执行入口
                }
                tag.append(" ").append(key).append("=\"").append(escapeAttributeValue(value)).append("\"");
            }
        }
        // 其余白名单标签不带属性（表格/文本标签的展示样式不需要 class/style 也能还原）
        return "br".equals(name) ? "<br>" : tag.append(">").toString();
    }

    /** 取属性值：去掉外层引号 */
    private static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() >= 2 && (raw.startsWith("\"") || raw.startsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /** 属性值实体化：引号与尖括号必须转义，否则会提前闭合属性或标签 */
    private static String escapeAttributeValue(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
