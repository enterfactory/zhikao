package com.zhikao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("notification_outbox")
public class NotificationOutbox {
 @TableId(type=IdType.AUTO) private Long id;
 private String eventKey,userIds,type,title,content,bizType;
 private Long bizId;
 private LocalDateTime createdAt,deliveredAt,nextAttemptAt;
 private Integer attempts;
 private String lastError;
}
