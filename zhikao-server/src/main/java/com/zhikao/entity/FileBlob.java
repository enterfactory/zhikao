package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("file_blob")
public class FileBlob {
    @TableId(type=IdType.AUTO) private Long id;
    private String sha256;
    private String accessPath;
    private String storedName;
    private Long sizeBytes;
    private String contentType;
    private LocalDateTime createdAt;
    private LocalDateTime garbageAfter;
}
