package com.zhikao.modules.curation;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Map;
@Data public class CandidateUpdate {
        @NotNull @Min(1) private Integer expectedVersion;
        @NotNull private Long subjectId;
        @NotNull @Min(1) @Max(5) private Integer questionType;
        @NotBlank @Size(max=50000) private String content;
        private Map<String,String> options;
        @NotBlank @Size(max=20000) private String answer;
        @Size(max=50000) private String analysis;
        @Min(1) @Max(5) private Integer difficulty;
        @Size(max=255) private String knowledgeTags;
        private boolean formulaConfirmed;
        @Min(1) private Integer sourcePage;
    }
