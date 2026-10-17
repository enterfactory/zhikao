package com.zhikao.modules.workflow;
import com.zhikao.modules.file.FileTypeVerifier;
import com.zhikao.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class FileTypeVerifierTest {
 final FileTypeVerifier verifier=new FileTypeVerifier();
 @Test void decodablePngPassesButWrongExtensionFails()throws Exception{
  var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  assertEquals("image/png",verifier.verify(bytes.toByteArray(),"png"));
  assertThrows(BusinessException.class,()->verifier.verify(bytes.toByteArray(),"pdf"));
 }
 @Test void pngAndPdfHeadersAloneAreNotValidFiles(){
  assertThrows(BusinessException.class,()->verifier.verify(new byte[]{(byte)137,80,78,71,13,10,26,10},"png"));
  assertThrows(BusinessException.class,()->verifier.verify("%PDF-1.7\nnot a document".getBytes(),"pdf"));
 }
 @Test void officeMacroPayloadIsRejected()throws Exception{
  var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){
   for(String name:new String[]{"[Content_Types].xml","word/document.xml","word/vbaProject.bin"}){zip.putNextEntry(new ZipEntry(name));zip.write("data".getBytes());zip.closeEntry();}
  }
  assertThrows(BusinessException.class,()->verifier.verify(bytes.toByteArray(),"docx"));
 }
}
