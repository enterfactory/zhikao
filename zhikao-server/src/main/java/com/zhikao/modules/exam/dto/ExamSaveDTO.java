package com.zhikao.modules.exam.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试新增 / 修改参数。
 */
@Data
public class ExamSaveDTO {

    /** 主键：为空表示新增 */
    private Long id;

    @NotBlank(message = "考试名称不能为空")
    private String examName;

    @NotNull(message = "请选择试卷")
    private Long paperId;

    @NotNull(message = "请选择参考班级")
    private Long classId;

    @NotNull(message = "请选择考试开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "请选择考试结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 答题时长（分钟），留空则用试卷建议时长 */
    @Min(value = 1, message = "答题时长至少 1 分钟")
    private Integer duration;

    /** 客观题自动阅卷 1开 0关，留空默认开启 */
    private Integer autoMark;

    /** 防作弊（切屏计数）1开 0关，留空默认关闭 */
    private Integer cheatCheck;

    /** 所属学期 id：透传保存，可空（不强制学期归类） */
    private Long semesterId;
}
