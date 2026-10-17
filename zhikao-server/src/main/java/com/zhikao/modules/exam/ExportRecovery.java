package com.zhikao.modules.exam;
import com.zhikao.mapper.ExportLeaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="zhikao.export.recovery-enabled",havingValue="true",matchIfMissing=true)
public class ExportRecovery {
 private final ExportLeaseMapper leases;
 private final ExportWorker worker;
 @Scheduled(initialDelay=10000,fixedDelay=10000)
 public void recover(){
  leases.failExhausted();
  for(Long id:leases.recoverable())try{worker.generate(id);}catch(org.springframework.core.task.TaskRejectedException e){break;}
 }
}
