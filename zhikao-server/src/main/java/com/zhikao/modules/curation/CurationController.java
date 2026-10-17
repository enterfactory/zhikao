package com.zhikao.modules.curation;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.*;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.zhikao.modules.crawler.CrawlerService;
import com.zhikao.modules.crawler.fetch.LocalPathGuard;
import com.zhikao.modules.crawler.promote.Promoter;
import com.zhikao.modules.crawler.vo.CrawlQuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/v2/curation")
@SaCheckPermission("crawler:manage")
@RequiredArgsConstructor
public class CurationController {
    private final CrawlRawMapper documents;
    private final CrawlerService crawler;
    private final CandidateEventMapper events;
    private final LocalPathGuard paths;
    private final CurationService service;

    @PostMapping("/tasks/{id}/retry")
    public R<Void> retry(@PathVariable Long id) {
        service.retry(id);return R.ok();
    }

    @GetMapping("/candidates/{id}") public R<CrawlQuestionVO> get(@PathVariable Long id){return R.ok(crawler.questionDetail(id));}

    @PostMapping("/candidates/{id}/start") public R<Void> start(@PathVariable Long id){service.start(id);return R.ok();}
    @PatchMapping("/candidates/{id}")
    public R<CrawlQuestionVO> update(@PathVariable Long id,@Valid @RequestBody CandidateUpdate body) {
        service.update(id,body);return R.ok(crawler.questionDetail(id));
    }
    @PostMapping("/candidates/{id}/publish")
    @OperLog(module="题目复核",action="审核入库")
    public R<Long> publish(@PathVariable Long id,@RequestBody Map<String,Integer> body){
        return R.ok(service.publish(id,body.get("expectedVersion"),null,null));
    }
    @PostMapping("/candidates/{id}/reject")
    public R<Void> reject(@PathVariable Long id,@RequestBody Map<String,Object> body){
        Object version=body.get("expectedVersion");
        service.reject(id,version instanceof Number n?n.intValue():null,Objects.toString(body.get("note"),"审核退回"));return R.ok();
    }
    @GetMapping("/candidates/{id}/events") public R<List<Map<String,Object>>> events(@PathVariable Long id){return R.ok(events.events(id));}

    @GetMapping("/documents/{id}/source") public ResponseEntity<byte[]> source(@PathVariable Long id) throws IOException {
        Path file=sourcePath(id);String name=documents.selectById(id).getSourceName();if(name==null)name=file.getFileName().toString();boolean pdf=name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").header("Cache-Control","private, no-store")
            .header("Content-Disposition",(pdf?ContentDisposition.inline():ContentDisposition.attachment()).filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .contentType(pdf?MediaType.APPLICATION_PDF:MediaType.APPLICATION_OCTET_STREAM).body(Files.readAllBytes(file));
    }
    @GetMapping("/documents/{id}/pages/{page}") public ResponseEntity<byte[]> page(@PathVariable Long id,@PathVariable int page) throws IOException {
        Path file=sourcePath(id);if(!file.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))throw new BusinessException("该资料暂无固定页预览，请下载 Word 原文核对");
        if(page<1||page>300)throw new BusinessException("页码超出预览范围");
        try(var pdf=Loader.loadPDF(file.toFile())){if(page>pdf.getNumberOfPages())throw new BusinessException("页码不存在");
            var box=pdf.getPage(page-1).getCropBox();
            if(box.getWidth()*box.getHeight()*(110d/72)*(110d/72)>20000000)throw new BusinessException("页面尺寸过大，请下载原文复核");
            var image=new PDFRenderer(pdf).renderImageWithDPI(page-1,110);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
            return ResponseEntity.ok().header("Cache-Control","private, no-store").contentType(MediaType.IMAGE_PNG).body(bytes.toByteArray());}
    }
    private Path sourcePath(Long id) throws IOException {
        CrawlRaw doc=documents.selectById(id);if(doc==null)throw new BusinessException("资料不存在");
        Path path=paths.requireAllowed(doc.getSourceSnapshot()==null?doc.getSourceUrl():doc.getSourceSnapshot()).toRealPath();
        boolean inside=false;for(Path root:paths.allowedRoots())if(Files.exists(root)&&path.startsWith(root.toRealPath()))inside=true;
        if(!inside||!Files.isRegularFile(path)||Files.size(path)>50L*1024*1024)throw new BusinessException("资料不可预览");return path;
    }
}
