package com.zhikao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智考在线后端启动类。
 *
 * @EnableScheduling：用于内存限流桶与登录失败记录的定时清理（轻量自研方案，不引入 Redis）。
 */
@EnableScheduling
@SpringBootApplication
public class ZkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZkApplication.class, args);
    }
}
