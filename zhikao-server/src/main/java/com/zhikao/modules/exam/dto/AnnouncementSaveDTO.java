package com.zhikao.modules.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 考试公告保存参数。
 */
@Data
public class AnnouncementSaveDTO {

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 1000, message = "公告内容不要超过 1000 字")
    private String content;

    /**
     * 是否同时推送给学生（默认推送）。
     * 保留这个开关是因为公告偶尔只是教师自己的备忘，硬推反而制造噪音。
     */
    private Integer push;
}
