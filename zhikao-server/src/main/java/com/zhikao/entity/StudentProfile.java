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
 * 学生画像：长期记忆的持久化载体。
 *
 * 记忆边界：只存「不变的、归纳出来的」结论（薄弱知识点、学习偏好）；
 * 成绩、题目这类实时数据一律走工具实时查询，绝不记忆，避免答出过期答案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("student_profile")
public class StudentProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 薄弱知识点，顿号分隔 */
    private String weakPoints;

    /** 学习偏好 */
    private String preferences;

    /** 画像摘要 */
    private String summary;

    private LocalDateTime updateTime;
}
