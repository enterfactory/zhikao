package com.zhikao.modules.crawler;

import lombok.RequiredArgsConstructor;
import com.zhikao.mapper.CrawlTaskMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.RejectedExecutionException;

/** Pending work survives queue rejection; expired leases retry at most three times. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="zhikao.crawler.recovery-enabled",havingValue="true",matchIfMissing=true)
@RequiredArgsConstructor
public class CrawlTaskRecovery {
    private final CrawlTaskMapper mapper;
    private final CrawlerPipelineRunner runner;
    @Scheduled(initialDelay=10000,fixedDelay=10000)
    public void recover() {
        mapper.failExhausted();
        var ids=mapper.recoverable();
        for(Long id:ids)try{runner.run(id);}catch(RejectedExecutionException e){break;}
    }
}
