package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件中心记录（三期新增）。
 *
 * <p>表的职责很窄：只描述「这份文件是什么、放在哪、谁传的」。
 * 真正的字节存在磁盘上，库里只存元数据 —— 这样备份数据库不会把几百 MB 附件一起搬走。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Shared bytes; the remaining columns describe this user's business reference. */
    private Long blobId;

    /** 客户端原始文件名（仅用于展示与下载命名，绝不用于磁盘路径） */
    private String originalName;

    /** 磁盘文件名：uuid.扩展名 */
    private String storedName;

    /** 访问相对路径：category/yyyy/MM/uuid.ext */
    private String accessPath;

    private String ext;

    private String contentType;

    private Long sizeBytes;

    /** 内容哈希（SHA-256），用于秒传与去重 */
    private String sha256;

    /** 分类：avatar / excel / paper / question / export / other */
    private String category;

    /** 业务标签：avatar / question-import / paper-attachment ... */
    private String bizType;

    /** 业务主键（如试卷 id），用于附件关联 */
    private Long bizId;

    private String createBy;

    private LocalDateTime createTime;
}
