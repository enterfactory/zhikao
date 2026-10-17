package com.zhikao.modules.crawler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 本地解压目录直入流水线参数（POST /crawler/import/local）。
 *
 * <p>dirPath 必须是绝对路径，且落在白名单根目录之下（项目「题目资源/」+ 配置 zhikao.crawler.allowed-roots），
 * 校验不通过直接拒绝——这是「读取服务器任意文件」越权的硬闸门，见 LocalPathGuard。
 */
@Data
public class CrawlImportLocalDTO {

    /** 本地目录绝对路径 */
    @NotBlank(message = "目录路径不能为空")
    private String dirPath;

    /** 可选：已登记的来源 id；为空时以 dirPath 临时建一个 LOCAL_DIR 源 */
    private Long sourceId;
}
