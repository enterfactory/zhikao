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
 * 字典类型。
 *
 * 定位：只承载「展示型」枚举（标签文案 / 排序 / 颜色），
 * 业务判分逻辑仍走 Java 枚举（类型安全、可编译期校验），二者分层不越界。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dict_type")
public class SysDictType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典名称，如「题型」 */
    private String dictName;

    /** 字典类型编码，如 question_type */
    private String dictType;

    private String remark;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;
}
