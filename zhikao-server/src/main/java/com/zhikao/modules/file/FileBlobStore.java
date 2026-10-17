package com.zhikao.modules.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.config.UploadProperties;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Shared-byte lifecycle; callers own the transaction and their individual SysFile reference. */
@Service @RequiredArgsConstructor
public class FileBlobStore {
    private final FileBlobMapper blobs;
    private final SysFileMapper references;
    private final UploadProperties properties;
    private final PlatformTransactionManager transactions;

    /** Writers and deletions share the blob lock so an attachment cannot disappear mid-save. */
    public void lockReferences(String... fields) {
        Set<Long> ids=new TreeSet<>();
        var pattern=java.util.regex.Pattern.compile("/api/file/view/(\\d+)(?![0-9])");
        for(String field:fields)if(field!=null){var matcher=pattern.matcher(field);while(matcher.find()){
            try{ids.add(Long.parseLong(matcher.group(1)));}catch(NumberFormatException e){throw new BusinessException("文件引用不合法");}
        }}
        Map<String,List<Long>> hashes=new TreeMap<>();
        for(Long id:ids){var ref=references.selectById(id);if(ref==null)throw new BusinessException("引用的文件已不存在，请重新上传");
            hashes.computeIfAbsent(ref.getSha256(),key->new ArrayList<>()).add(id);}
        for(var entry:hashes.entrySet()){
            blobs.lockContent(entry.getKey());
            for(Long id:entry.getValue())if(references.selectById(id)==null)throw new BusinessException("文件已被删除，请重新上传");
        }
    }

    public FileBlob attach(byte[] bytes,String extension,String mime) {
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            blobs.lockContent(hash);
            FileBlob blob=blobs.selectOne(Wrappers.<FileBlob>lambdaQuery().eq(FileBlob::getSha256,hash));
            boolean isNew=blob==null;
            if(isNew) {
                String name=UUID.randomUUID().toString().replace("-","")+"."+extension;
                LocalDate date=LocalDate.now();
                blob=FileBlob.builder().sha256(hash).storedName(name)
                        .accessPath("other/"+date.getYear()+"/"+String.format("%02d",date.getMonthValue())+"/"+name)
                        .contentType(mime).sizeBytes((long)bytes.length).createdAt(LocalDateTime.now()).build();
            }
            Path target=path(blob.getAccessPath());
            if(!Files.isRegularFile(target)) {
                Files.createDirectories(target.getParent());
                Path staging=Files.createTempFile(target.getParent(),".upload-",".part");
                try {Files.write(staging,bytes);Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE);}
                finally {Files.deleteIfExists(staging);}
                if(isNew) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                    @Override public void afterCompletion(int status) {
                        if(status!=STATUS_COMMITTED)try {Files.deleteIfExists(target);}catch(Exception ignored){}
                    }
                });
            }
            if(isNew) blobs.insert(blob);
            else blobs.update(null,Wrappers.<FileBlob>lambdaUpdate().eq(FileBlob::getId,blob.getId()).set(FileBlob::getGarbageAfter,null));
            return blob;
        } catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("文件写入失败，请重试");}
    }

    public void release(SysFile ref,boolean checkBusiness) {
        blobs.lockContent(ref.getSha256());
        if(checkBusiness && (blobs.businessReferences(ref.getId())>0 || ref.getBizId()!=null))
            throw new BusinessException("文件仍被头像、题目或业务附件使用，请先解除引用");
        references.deleteById(ref.getId());
        if(references.selectCount(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getBlobId,ref.getBlobId()))==0)
            blobs.update(null,Wrappers.<FileBlob>lambdaUpdate().eq(FileBlob::getId,ref.getBlobId())
                    .set(FileBlob::getGarbageAfter,LocalDateTime.now().plusDays(1)));
    }

    /** Keep a 24h grace period, re-check references under the same lock before reclaiming bytes. */
    @Scheduled(initialDelay=60000,fixedDelay=3600000)
    public void collectGarbage() {
        var expired=blobs.selectList(Wrappers.<FileBlob>lambdaQuery().lt(FileBlob::getGarbageAfter,LocalDateTime.now()).last("LIMIT 100"));
        for(FileBlob candidate:expired) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status->{
                    blobs.lockContent(candidate.getSha256());
                    FileBlob current=blobs.selectById(candidate.getId());
                    if(current==null || current.getGarbageAfter()==null || current.getGarbageAfter().isAfter(LocalDateTime.now()))return;
                    if(references.selectCount(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getBlobId,current.getId()))!=0)return;
                    try {Files.deleteIfExists(path(current.getAccessPath()));}
                    catch(Exception e){throw new BusinessException("文件回收失败，稍后重试");}
                    blobs.deleteById(current.getId());
                });
            } catch(Exception e) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("文件回收待重试，blobId={}",candidate.getId());}
        }
    }
    public Path path(String relative) {
        Path root=Paths.get(properties.getDir()).toAbsolutePath().normalize();
        Path resolved=root.resolve(relative).normalize();
        if(!resolved.startsWith(root))throw new BusinessException("文件路径越界");
        return resolved;
    }
}
