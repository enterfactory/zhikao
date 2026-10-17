package com.zhikao.modules.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本人账号的一条安全活动（个人设置页「账号与安全」）。
 *
 * <p>为什么不复用 {@code modules.system.vo.OperLogVO}：那个对象是管理端操作日志的口径，
 * 带着 params（可能含业务入参）、costMs、requestUri 等与「我自己的账号安全」无关的字段。
 * 自助页只需要「什么时候、发生了什么、从哪个 IP、成没成功」，字段收窄一点，
 * 也顺带把「个人设置页会不会看到别人的日志」这个问题从结构上排除掉。
 */
@Data
public class SecurityActivityVO {

    /** 事件发生时间 */
    private LocalDateTime operTime;

    /** 事件类型码：LOGIN / LOGIN_FAILED / LOGOUT / PASSWORD */
    private String kind;

    /** 人可读标题，如「登录成功」「修改密码」 */
    private String title;

    /** 来源 IP；取不到时为 null（例如异步审计没带上下文） */
    private String ip;

    /** 1=成功，0=失败 */
    private Integer resultStatus;

    /** 失败原因；成功时为 null。登录失败原因对本人是有用的安全信息（撞库线索） */
    private String errorMsg;
}
