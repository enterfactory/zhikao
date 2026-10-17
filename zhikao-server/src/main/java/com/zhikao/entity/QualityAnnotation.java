package com.zhikao.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("quality_annotation")
public class QualityAnnotation {
 @TableId(type=IdType.AUTO) private Long id;
 private Long rawId;
 private Integer revision;
 private Integer datasetFrozen;
 private String family,split,licenseNote,referenceText,questionsJson,status,firstReviewer,secondReviewer;
 private LocalDateTime updatedAt;
}
