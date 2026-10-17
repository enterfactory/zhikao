package com.zhikao.cache;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MemoryCounterTest {
 @Test void expiredCounterResetsAtomicallyBeforeCleanup(){
  var now=new AtomicLong(0);
  Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(now.get());}};
  var cache=new MemoryCacheService(clock);
  assertEquals(1,cache.increment("window",Duration.ofSeconds(1)));
  now.set(999);assertEquals(2,cache.increment("window",Duration.ofSeconds(1)));
  now.set(1000);assertEquals(0,cache.counter("window"));
  java.util.stream.IntStream.range(0,100).parallel().forEach(i->cache.increment("window",Duration.ofSeconds(1)));
  assertEquals(100,cache.counter("window"));
  cache.cleanup();assertEquals(100,cache.counter("window"));
 }
}
