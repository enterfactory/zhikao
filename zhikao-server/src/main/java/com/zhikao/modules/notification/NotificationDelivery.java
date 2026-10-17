package com.zhikao.modules.notification;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.cache.CacheService;
import com.zhikao.entity.NotificationOutbox;
import com.zhikao.mapper.NotificationOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class NotificationDelivery {
 private final NotificationOutboxMapper events;
 private final CacheService cache;
 private final PlatformTransactionManager manager;
 private final com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.beans.factory.annotation.Value("${zhikao.notification.delivery-enabled:true}") private boolean enabled;
 @Scheduled(initialDelay=5000,fixedDelay=2000)
 public void poll(){if(enabled)for(Long id:events.pending())deliverOne(id);}
 public void deliverOne(Long id) {
  TransactionTemplate tx=new TransactionTemplate(manager);
  tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  try {
   tx.executeWithoutResult(status->{
    NotificationOutbox row=events.lockPending(id);if(row==null)return;
    var recipients=recipients(row.getUserIds());
    for(Long userId:recipients)events.deliver(row,userId);
    events.delivered(id);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
     @Override public void afterCommit(){for(Long userId:recipients)try{cache.delete("notify:unread:"+userId);}catch(Exception ignored){}}
    });
   });
  } catch(Exception failure) {
   tx.executeWithoutResult(status->events.failed(id));
   org.slf4j.LoggerFactory.getLogger(getClass()).warn("通知待重试，eventId={}",id);
  }
 }
 private java.util.List<Long> recipients(String text) {
  try{return json.readValue(text,new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Long>>(){});}
  catch(Exception e){throw new IllegalStateException("Invalid notification recipient payload",e);}
 }
}
