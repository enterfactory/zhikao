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
 * 系统用户。
 * 使用 @Builder 构造：字段较多，链式写法比 10 参数构造器更易读、不易错位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    private Integer passwordChangeRequired;
    private LocalDateTime passwordExpiresAt;
    private Long authVersion;

    /** 真实姓名 */
    private String realName;

    /** 1管理员 2教师 3学生 */
    private Integer userType;

    /** 学生归属班级（非学生为空） */
    private Long classId;

    /** 手机号 */
    private String phone;

    /** 头像访问地址（由文件模块生成，形如 /api/file/view/12） */
    private String avatar;

    /** 1启用 0禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
