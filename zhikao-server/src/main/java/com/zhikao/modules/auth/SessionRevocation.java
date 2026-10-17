package com.zhikao.modules.auth;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
@Service
@lombok.RequiredArgsConstructor
public class SessionRevocation {
 private final com.zhikao.mapper.SysUserMapper users;
 public void afterCommit(Long userId) {
  users.advanceAuthVersion(userId);
  Runnable revoke=()->{
   try { StpUtil.kickout(userId); }
   catch(Exception e) {
    // Database version is authoritative: restored Redis tokens still fail the request guard.
    org.slf4j.LoggerFactory.getLogger(SessionRevocation.class).warn("会话清理暂未完成，数据库授权版本已推进，userId={}",userId);
   }
  };
  if(TransactionSynchronizationManager.isSynchronizationActive()) {
   TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
    @Override public void afterCommit(){revoke.run();}
   });
  } else revoke.run();
 }
}
