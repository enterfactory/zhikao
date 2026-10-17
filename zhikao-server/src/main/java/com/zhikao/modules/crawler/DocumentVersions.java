package com.zhikao.modules.crawler;
import com.zhikao.config.CrawlerProperties;
import com.zhikao.common.*;
import com.zhikao.modules.crawler.fetch.LocalPathGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Content identity and an immutable source copy, independent of its mutable import path. */
@Component @RequiredArgsConstructor
public class DocumentVersions {
 private final CrawlerProperties properties;
 private final LocalPathGuard guard;
 public String fingerprint() {
  var o=properties.getOcr();
  return "pipeline-b3-v1:"+hash(JsonHelper.toJson(Arrays.asList(o.getEngine(),o.getMaxPages(),o.getPdfDpi(),
   o.getPdfMinCharsPerPage(),o.getQwen().getModel(),o.getQwen().getPrompt(),o.getFormula()))
   .getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,24);
 }
 public String contentHash(Path source) {
  try {
   Path allowed=guard.requireAllowed(source.toString());
   if(Files.size(allowed)>properties.getMaxDocBytes())throw new BusinessException("文档超过体积上限");
   return hash(Files.readAllBytes(allowed));
  }catch(java.io.IOException e){throw new BusinessException("无法读取来源文档");}
 }
 public Path capture(Path source) {
  try {
   Path allowed=guard.requireAllowed(source.toString());
   if(Files.size(allowed)>properties.getMaxDocBytes())throw new BusinessException("文档超过体积上限");
   byte[] bytes=Files.readAllBytes(allowed);
   String ext=com.zhikao.modules.crawler.extract.DocumentExtractor.extensionOf(source.getFileName().toString());
   Path directory=Paths.get(properties.getIngestDir()).toAbsolutePath().normalize().resolve(".versions");
   Files.createDirectories(directory);Path target=directory.resolve(hash(bytes)+"."+ext);
   if(!Files.exists(target)) {
    Path temp=Files.createTempFile(directory,"source-",".part");
    try {Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}
    catch(FileAlreadyExistsException duplicate) { /* another worker captured the same immutable content */ }
    finally {Files.deleteIfExists(temp);}
   }
   return target;
  }catch(java.io.IOException e){throw new BusinessException("原文快照保存失败");}
 }
 public String identity(String contentHash,Long subjectId){return hash((contentHash+":"+fingerprint()+":"+subjectId).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
