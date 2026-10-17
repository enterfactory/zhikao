package com.zhikao.modules.system.vo;

import lombok.Data;

/**
 * 字典项返回结构。
 * value 统一以字符串下发：前端 select 的取值与 label 比较都在字符串层面完成，
 * 避免「Java 里是 Integer、前端是 number、字典表里是 varchar」三处类型打架。
 */
@Data
public class DictItemVO {

    private Long id;

    private String dictType;

    private String label;

    private String value;

    /** 标签颜色：primary / success / warning / danger / info */
    private String color;

    private Integer sort;

    private Integer status;
}
