package com.zhikao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置。
 *
 * <p>刻意不做「云存储抽象层」：本项目是单机演示与自建部署，
 * 一个本地目录就能满足，凭空加一层对象存储接口只会增加阅读成本。
 * 真换成 OSS/S3 时，只需替换 {@code FileStorageService} 的读写实现，
 * 表结构与接口契约都不用动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhikao.upload")
public class UploadProperties {

    /** 存储根目录（相对或绝对路径均可） */
    private String dir = "./data/uploads";

    /** 单文件大小上限的兜底值（各分类还有自己的更严格上限） */
    private long maxBytes = 20 * 1024 * 1024;

    /** 是否允许在响应里内联预览（图片头像需要）；关闭后一律走下载 */
    private boolean inlinePreview = true;
}
