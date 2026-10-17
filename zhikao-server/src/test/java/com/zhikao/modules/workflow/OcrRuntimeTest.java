package com.zhikao.modules.workflow;

import com.zhikao.config.CrawlerProperties;
import com.zhikao.modules.crawler.ocr.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OcrRuntimeTest {
    @Test void explicitRapidModeRecognizesRealRenderedText() throws Exception {
        var image=new java.awt.image.BufferedImage(800,180,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,800,180);
            g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("Arial",java.awt.Font.PLAIN,60));
            g.drawString("Stage A OCR 12345",30,110);
        } finally {g.dispose();}
        var path=java.nio.file.Path.of("target/stage-a-ocr.png");
        javax.imageio.ImageIO.write(image,"png",path.toFile());
        var config=new CrawlerProperties();config.getOcr().setEngine("rapid");
        var local=new RapidOcrEngine();
        var cloud=org.mockito.Mockito.mock(QwenVlOcrEngine.class);
        org.mockito.Mockito.when(cloud.name()).thenReturn("qwen-vl");
        var service=new OcrService(config,local,cloud);
        assertTrue(local.available(),"RapidOCR native runtime is required for this validation");
        var result=service.recognize(path);
        assertEquals("rapidocr",result.engine());
        assertTrue(result.text().contains("12345"),result.text());
    }
}
