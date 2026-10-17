package com.zhikao.modules.workflow;
import com.zhikao.modules.crawler.ocr.*;
import com.zhikao.config.CrawlerProperties;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OcrSelectionTest {
 @Test void explicitAliasesAndUnavailableEngines(){
  var p=new CrawlerProperties();var local=mock(RapidOcrEngine.class);var cloud=mock(QwenVlOcrEngine.class);
  when(local.name()).thenReturn("rapidocr");when(cloud.name()).thenReturn("qwen-vl");
  when(local.available()).thenReturn(true);when(cloud.available()).thenReturn(true);
  var service=new OcrService(p,local,cloud);
  p.getOcr().setEngine("rapid");assertSame(local,service.choose());
  p.getOcr().setEngine("qwen");assertSame(cloud,service.choose());
  p.getOcr().setEngine("auto");assertSame(local,service.choose());
  when(local.available()).thenReturn(false);assertSame(cloud,service.choose());
  p.getOcr().setEngine("rapid");assertThrows(com.zhikao.common.BusinessException.class,service::choose);
  when(cloud.available()).thenReturn(false);p.getOcr().setEngine("auto");assertThrows(com.zhikao.common.BusinessException.class,service::choose);
 }
}
