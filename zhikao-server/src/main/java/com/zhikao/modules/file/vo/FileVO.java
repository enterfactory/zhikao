package com.zhikao.modules.file.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件视图对象：前端只认这几个字段，磁盘与表结构的变化不外溢。
 */
@Data
public class FileVO {

    private Long id;

    /** 原始文件名（含扩展名） */
    private String name;

    private String ext;

    /** 字节数 */
    private Long size;

    /** 人类可读体积，如「1.2 MB」 */
    private String sizeText;

    private String category;

    private String bizType;

    private Long bizId;

    /** 内联访问地址（图片直接可用于 img src） */
    private String url;

    /** 下载地址（带 Content-Disposition） */
    private String downloadUrl;

    private String createBy;

    private LocalDateTime createTime;
}
