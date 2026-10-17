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
 * 考试公告：挂在某场考试下，学生在「我的考试」可见。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam_announcement")
public class ExamAnnouncement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private String content;

    private String createBy;

    private LocalDateTime createTime;
}
