package com.zhikao.modules.exam;
import com.zhikao.mapper.*;
import com.zhikao.modules.file.FileStorageService;
import com.zhikao.modules.system.RbacService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Service
@RequiredArgsConstructor
public class ExportWorker {
 private final SysExportTaskMapper tasks;
 private final ExportLeaseMapper leases;
 private final GradeService grades;
 private final FileStorageService files;
 private final SysUserMapper users;
 private final RbacService rbac;
 private final Map<Long,String> owned=new ConcurrentHashMap<>();
 @Async(com.zhikao.config.AsyncConfig.EXPORT_EXECUTOR)
 public void generate(Long id) {
  String token=UUID.randomUUID().toString();
  if(leases.claim(id,token)!=1)return;
  owned.put(id,token);
  try {
   var task=tasks.selectById(id);
   requireGrant(task.getCreateBy());
   byte[] content=grades.exportExamGrade(task.getBizId());
   requireGrant(task.getCreateBy());
   if(leases.heartbeat(id,token)!=1)return;
   var file=files.storeGenerated(content,task.getFileName(),ExportTaskService.BIZ_TYPE_GRADE,task.getCreateBy());
   leases.complete(id,token,1,file.getId(),null);
  } catch(Exception e) {
   leases.complete(id,token,2,null,"导出失败，请核对账号权限和考试数据后重新提交");
  } finally {owned.remove(id,token);}
 }
 private void requireGrant(String owner) {
  var user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,owner));
  if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || Integer.valueOf(1).equals(user.getPasswordChangeRequired())
   || !rbac.permissionCodesOf(rbac.roleCodesOf(user.getId())).contains("grade:export"))
   throw new IllegalStateException("导出授权已失效");
 }
 @Scheduled(fixedDelay=20000)
 public void heartbeat(){for(var e:owned.entrySet())if(leases.heartbeat(e.getKey(),e.getValue())!=1)owned.remove(e.getKey(),e.getValue());}
}
