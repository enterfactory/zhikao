package com.zhikao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("quality_evaluation_run")
public class QualityEvaluationRun {
 @TableId(type=IdType.AUTO) private Long id;
 private String name,datasetKind,status,summaryJson,createdBy;
 private LocalDateTime createdAt;
}
