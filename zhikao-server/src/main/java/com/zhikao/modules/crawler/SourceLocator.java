package com.zhikao.modules.crawler;
import com.zhikao.entity.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.*;

/** Locate the start of a question; never invent page numbers or boxes for unmatched/scanned text. */
@Component
public class SourceLocator {
 public void locateAll(CrawlRaw raw,List<CrawlQuestion> questions) {
  for(int i=0;i<questions.size();i++) {
   var q=questions.get(i);q.setSourceOrdinal(i+1);q.setSourceAnchor(plain(q.getContent()).substring(0,Math.min(160,plain(q.getContent()).length())));
   q.setLocatorMethod("TEXT_ANCHOR");
  }
  if(raw.getSourceSnapshot()==null || !raw.getSourceSnapshot().toLowerCase(Locale.ROOT).endsWith(".pdf"))return;
  try(var pdf=Loader.loadPDF(Path.of(raw.getSourceSnapshot()).toFile())) {
   raw.setPageCount(pdf.getNumberOfPages());
   for(int page=1;page<=Math.min(300,pdf.getNumberOfPages());page++) {
    final StringBuilder text=new StringBuilder();final List<TextPosition> positions=new ArrayList<>();
    PDFTextStripper stripper=new PDFTextStripper(){
     @Override protected void writeString(String ignored,List<TextPosition> chars) {
      for(var c:chars)for(char letter:c.getUnicode().toCharArray())if(Character.isLetterOrDigit(letter)){
       text.append(Character.toLowerCase(letter));positions.add(c);
      }
     }
    };
    stripper.setSortByPosition(true);stripper.setStartPage(page);stripper.setEndPage(page);stripper.getText(pdf);
    for(var q:questions) {
     if(q.getSourcePage()!=null)continue;
     String anchor=normalize(q.getContent());anchor=anchor.substring(0,Math.min(anchor.length(),24));
     if(anchor.length()<5)continue;
     int index=text.indexOf(anchor);if(index<0)continue;
     float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=0,bottom=0;
     for(var c:positions.subList(index,index+anchor.length())) {
      left=Math.min(left,c.getXDirAdj());top=Math.min(top,c.getYDirAdj()-c.getHeightDir());
      right=Math.max(right,c.getXDirAdj()+c.getWidthDirAdj());bottom=Math.max(bottom,c.getYDirAdj());
     }
     var box=pdf.getPage(page-1).getCropBox();
     q.setSourcePage(page);q.setLocatorMethod("PDF_TEXT_START");
     q.setSourceBbox(String.format(Locale.ROOT,"%.6f,%.6f,%.6f,%.6f",left/box.getWidth(),top/box.getHeight(),(right-left)/box.getWidth(),(bottom-top)/box.getHeight()));
    }
   }
  }catch(Exception ignored){/* Raw source remains downloadable; unmatched anchors stay explicit. */}
  String[] pages=raw.getContent().split("\f",-1);
  if(pages.length>1)for(var q:questions)if(q.getSourcePage()==null) {
   String a=normalize(q.getContent());a=a.substring(0,Math.min(a.length(),24));
   for(int i=0;i<pages.length;i++)if(a.length()>=5 && normalize(pages[i]).contains(a)){
    q.setSourcePage(i+1);q.setLocatorMethod("OCR_PAGE_TEXT");break;
   }
  }
 }
 private String plain(String html){return org.springframework.web.util.HtmlUtils.htmlUnescape((html==null?"":html).replaceAll("<[^>]*>"," "));}
 private String normalize(String html){return plain(html).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
}
