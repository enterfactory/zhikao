package com.zhikao.modules.workflow;

import com.zhikao.modules.crawler.parse.MarkdownTemplateParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {
    @Test void pdfCrlfWithoutBlankParagraphsPreservesQuestionBoundariesAndFormulas() {
        String text=String.join("\r\n","高等数学复习题","题干：函数 $f(x)=x^2$ 的导数。","A. x","B. 2x","答案：B","解析：幂函数求导。","题干：函数 $f(x)=3x$ 的导数。","A. 1","B. 3","答案：B","解析：一次函数求导。");
        var parsed=new MarkdownTemplateParser().parse(text);
        assertEquals(2,parsed.size());
        assertEquals("函数 $f(x)=x^2$ 的导数。",parsed.get(0).getContent());
        assertEquals("2x",parsed.get(0).getOptions().get("B"));
        assertEquals("3",parsed.get(1).getOptions().get("B"));
        assertEquals("B",parsed.get(0).getAnswer());
        assertEquals("一次函数求导。",parsed.get(1).getAnalysis());
    }
    @Test void implicitLegacyBlocksStillAcceptWindowsBlankLines() {
        String text="下列哪一个是偶数？\r\nA. 1\r\nB. 2\r\n答案：B\r\n\r\n下列哪一个是奇数？\r\nA. 3\r\nB. 4\r\n答案：A";
        var parsed=new MarkdownTemplateParser().parse(text);
        assertEquals(2,parsed.size());assertEquals("A",parsed.get(1).getAnswer());
    }
}
