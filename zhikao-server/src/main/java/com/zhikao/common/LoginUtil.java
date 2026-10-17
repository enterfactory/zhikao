package com.zhikao.common;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 登录态工具：读取当前登录用户信息。
 *
 * <p>登录时（{@code AuthServiceImpl}）已把 username / userType 写入 Sa-Token Session，
 * 业务代码从这里直接读取即可，无需每次操作都回查数据库。
 *
 * <p><b>三期修正：彻底消灭「SaTokenContext 上下文尚未初始化」这类异常外泄。</b>
 *
 * <p>问题成因（已在字节码层面核实）：Sa-Token 的登录态挂在
 * {@code cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff} 上，
 * 它是**线程绑定**的。以下三种线程上取不到上下文，方法会抛
 * {@code SaTokenContextException / NotWebContextException}：
 * <ol>
 *   <li>异步线程池线程（本项目操作日志落库用的 {@code oper-log-*} 线程池）；</li>
 *   <li>Reactor / Netty 事件循环线程（AgentScope 调模型、流式回调都跑在这里）；</li>
 *   <li>请求已结束、线程被回收到容器池后仍有残留任务在跑的场景。</li>
 * </ol>
 * 原来 {@code username()} / {@code userId()} 没有兜底，异常会一路冒到
 * {@code GlobalExceptionHandler}，被包装成「系统异常：SaTokenContext 上下文尚未初始化」
 * 返回给前端 —— 用户看到一句与业务毫无关系的内部术语，日志里也只有噪音。
 *
 * <p>现在做两件事：
 * <ul>
 *   <li><b>快照兜底</b>：请求线程上读过一次身份就缓存成 {@link Snapshot}，
 *       并经 {@code AsyncConfig} 的 TaskDecorator 传递到异步线程；
 *       拿不到实时上下文时回退到快照，而不是抛异常。</li>
 *   <li><b>语义化失败</b>：确实没有任何身份时，{@link #userId()} 抛
 *       {@code 40101 未登录}（这是事实：没有登录态就是未登录），
 *       而不是 50000 系统异常。</li>
 * </ul>
 */
public final class LoginUtil {

    private static final Logger log = LoggerFactory.getLogger(LoginUtil.class);

    /** 会话中存放用户名的 key，与 AuthServiceImpl 写入时保持一致 */
    public static final String SESSION_USERNAME = "username";

    /** 会话中存放用户类型的 key（粗粒度 persona），与 StpInterfaceImpl 兜底逻辑保持一致 */
    public static final String SESSION_USER_TYPE = "userType";

    /** 会话中存放细粒度角色码列表的 key，与 StpInterfaceImpl / AuthServiceImpl 写入时保持一致 */
    public static final String SESSION_ROLE_CODES = "roleCodes";

    /** 会话中存放权限码列表的 key，与 StpInterfaceImpl / AuthServiceImpl 写入时保持一致 */
    public static final String SESSION_PERMISSIONS = "permissions";

    /**
     * 身份快照，用于跨线程兜底。
     * 用 ThreadLocal 而不是全局变量：它描述的是「当前执行线程代表谁」。
     */
    private static final ThreadLocal<Snapshot> SNAPSHOT = new ThreadLocal<>();

    private LoginUtil() {
    }

    /**
     * 一次请求的身份快照。
     * role 是 persona 码（admin/teacher/student），roleCodes 是 RBAC 细粒度角色码——
     * 两者并存是刻意的：既有异步逻辑（操作日志、AI 工具）按 persona 分流，
     * 新代码才按细粒度角色判断；快照把两套语义原样带过线程边界，异步侧不需要重新查库。
     */
    public record Snapshot(Long userId, String username, String role, List<String> roleCodes) {
    }

    // ==================== 快照的读写（供过滤器 / 异步装饰器 / 切面使用）====================

    /** 把快照绑定到当前线程 */
    public static void bind(Snapshot snapshot) {
        if (snapshot == null) {
            SNAPSHOT.remove();
        } else {
            SNAPSHOT.set(snapshot);
        }
    }

    /** 读取当前线程的快照，可能为 null */
    public static Snapshot snapshot() {
        return SNAPSHOT.get();
    }

    /** 清理快照。线程池复用线程时**必须**清理，否则会串号 */
    public static void clear() {
        SNAPSHOT.remove();
    }

    /**
     * 在当前线程上尝试读取实时登录态并缓存为快照。
     * 请求线程上调用它，随后异步任务就能沿用同一身份。
     * 读取失败（非请求线程 / 未登录）时返回 null，不抛异常。
     */
    public static Snapshot capture() {
        Snapshot cached = SNAPSHOT.get();
        if (cached != null) {
            return cached;
        }
        try {
            Object username = StpUtil.getSession().get(SESSION_USERNAME);
            Object userType = StpUtil.getSession().get(SESSION_USER_TYPE);
            Snapshot fresh = new Snapshot(StpUtil.getLoginIdAsLong(), stringOf(username),
                    userType == null ? "" : UserTypeEnum.roleOf(intOf(userType)),
                    readRoleCodes());
            SNAPSHOT.set(fresh);
            return fresh;
        } catch (Exception e) {
            // 未登录或非请求线程：属预期情况，用 debug 级别，避免污染日志
            if (log.isDebugEnabled()) {
                log.debug("当前线程无法读取 Sa-Token 上下文（{}），将回退到快照或空值",
                        e.getClass().getSimpleName());
            }
            return null;
        }
    }

    // ==================== 业务读取入口 ====================

    /** 当前登录用户 id；确实没有身份时抛 40101（而不是 50000 系统异常） */
    public static Long userId() {
        Long id = userIdOrNull();
        if (id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录后再试");
        }
        return id;
    }

    /** 当前登录用户 id；无任何身份来源时返回 null（供可空场景使用） */
    public static Long userIdOrNull() {
        Snapshot snapshot = SNAPSHOT.get();
        if (snapshot != null && snapshot.userId() != null) {
            return snapshot.userId();
        }
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return snapshot == null ? null : snapshot.userId();
        }
    }

    /** 当前登录用户名；任何情况下都返回字符串（拿不到就是空串），**不抛异常** */
    public static String username() {
        String live = stringOf(sessionAttr(SESSION_USERNAME));
        if (!live.isEmpty()) {
            return live;
        }
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? "" : stringOf(snapshot.username());
    }

    /** 当前登录用户的角色标识（admin / teacher / student）；拿不到返回空串，**不抛异常** */
    public static String roleCode() {
        Object userType = sessionAttr(SESSION_USER_TYPE);
        if (userType != null) {
            return UserTypeEnum.roleOf(intOf(userType));
        }
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null ? "" : stringOf(snapshot.role());
    }

    /**
     * 当前登录用户的细粒度角色码集合（RBAC：super_admin / edu_admin / subject_lead / teacher / student）。
     *
     * <p>与 {@link #roleCode()} 的分工：roleCode 是 persona 码（大量既有分流逻辑在读），
     * 本方法返回 sys_user_role 分配的细粒度角色。任何情况下都返回列表
     * （拿不到就是空表），**不抛异常**——调用方多在异步链路与展示层，语义同 username()。
     */
    public static List<String> roleCodes() {
        List<String> live = readRoleCodes();
        if (!live.isEmpty()) {
            return live;
        }
        Snapshot snapshot = SNAPSHOT.get();
        return snapshot == null || snapshot.roleCodes() == null
                ? Collections.emptyList() : snapshot.roleCodes();
    }

    /** 从会话读角色码列表；非请求线程 / 未登录 / 形态不对时返回空列表，不抛异常 */
    private static List<String> readRoleCodes() {
        Object value = sessionAttr(SESSION_ROLE_CODES);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(item == null ? "" : item.toString());
        }
        return result;
    }

    /** 读取会话属性；非请求线程 / 未登录时返回 null 而不抛异常 */
    private static Object sessionAttr(String key) {
        try {
            return StpUtil.getSession().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOf(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
