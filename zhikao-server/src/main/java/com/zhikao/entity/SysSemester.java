package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 学期：考试的组织维度（一场考试属于哪个学期）。
 *
 * <p>is_current 全表至多一行为 1；切换当前学期时由服务层先把旧的清零再置新的，
 * 而不是在库里堆多个「当前学期」让查询方自己猜哪个是真的。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_semester")
public class SysSemester {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学期名称，如「2026-2027学年 第一学期」 */
    private String name;

    /** 学年标识，如 2026-2027 */
    private String schoolYear;

    /** 学期序号：1 第一学期 2 第二学期（3 小学期） */
    private Integer termNo;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 1当前学期 0历史/未来学期（全表至多一个 1） */
    private Integer isCurrent;
}
