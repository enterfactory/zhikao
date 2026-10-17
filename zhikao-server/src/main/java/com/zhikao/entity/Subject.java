package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 科目：题目与试卷的归属维度。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("subject")
public class Subject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科目名称（唯一） */
    private String subjectName;

    /** 科目编码，如 JAVA */
    private String subjectCode;

    /** 创建人用户名 */
    private String createBy;

    private LocalDateTime createTime;
}
