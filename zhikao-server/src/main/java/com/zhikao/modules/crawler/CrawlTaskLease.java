package com.zhikao.modules.crawler;

import lombok.RequiredArgsConstructor;
import com.zhikao.mapper.CrawlTaskMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Database leases allow recovery after a process restart without Redis. */
@Component
@RequiredArgsConstructor
public class CrawlTaskLease {
    private final CrawlTaskMapper mapper;
    private final Map<Long,String> owned = new ConcurrentHashMap<>();

    public String claim(Long id) {
        String token=UUID.randomUUID().toString();
        int n=mapper.claim(id,token,until());
        if(n==0)return null;
        owned.put(id,token);return token;
    }
    public void fence(Long id,String token) {
        if(mapper.fence(id,token,until())!=1)
            throw new IllegalStateException("任务租约已失效，停止写入");
    }
    public void release(Long id,String token) {owned.remove(id,token);}
    @Scheduled(fixedDelay=20000)
    public void heartbeat() {
        for(var e:owned.entrySet())try{fence(e.getKey(),e.getValue());}catch(Exception ex){owned.remove(e.getKey(),e.getValue());}
    }
    private LocalDateTime until(){return LocalDateTime.now().plusMinutes(2);}
}
