package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 班级：学生归属单位，也是后续「按班发布考试、按班统计成绩」的依据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_class")
public class SysClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级名称（唯一） */
    private String className;

    /** 年级，如 2024级 */
    private String grade;

    /** 专业 */
    private String major;

    private LocalDateTime createTime;
}
