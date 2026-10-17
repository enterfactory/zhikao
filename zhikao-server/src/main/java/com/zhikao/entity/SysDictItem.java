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
 * 字典项：一个字典类型下的一组可选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_dict_item")
public class SysDictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典类型编码 */
    private String dictType;

    /** 展示文案 */
    private String itemLabel;

    /** 取值（统一按字符串存取，前端比较时同样按字符串） */
    private String itemValue;

    /** 标签颜色：primary / success / warning / danger / info */
    private String color;

    private Integer sort;

    /** 1启用 0停用 */
    private Integer status;

    private LocalDateTime createTime;
}
