package com.zhikao.common;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * 数据归属校验（修复水平越权 / IDOR）。
 *
 * 背景：一期所有教师都能改「别人创建的」题目、试卷、考试——这是真实的水平越权漏洞。
 * 二期补上归属校验，规则：
 * <ul>
 *   <li><b>管理员</b>不受限制（需要能处理任何人的数据）；</li>
 *   <li><b>教师</b>只能改自己创建的数据；</li>
 *   <li><b>读取</b>不设归属限制——题库与试卷是共享资产，教师之间应当能互相借鉴，
 *       但「别人建的东西你只能看、不能改」，这条边界既安全又符合真实教务场景。</li>
 * </ul>
 *
 * 为什么用 create_by（用户名）而不是 ID：现有表已冗余了 create_by 字段，
 * 且它是创建时刻的不可变快照，不随用户改名而漂移。
 */
@Component
public class DataScope {

    /**
     * 当前登录用户是否为管理员。
     *
     * <p>RBAC 改造后角色码细分成 5 个，「管理员」的语义对应 super_admin 与 edu_admin
     * 两个角色码（两者 persona 均为 1）；按角色码判断而不是按 user_type，
     * 是为了让「未来给某个 persona 拆出新管理角色」只需要改这一行。
     */
    public boolean isAdmin() {
        // hasRoleOr：sa-token 1.45.0 的 StpUtil 没有 hasAnyRole，OR 语义的 API 名是 hasRoleOr
        return StpUtil.hasRoleOr("super_admin", "edu_admin");
    }

    /**
     * 校验当前用户对某条数据是否有写权限。
     *
     * @param createBy 数据创建人用户名
     * @param target   数据名词（用于拼出可读的报错，如「题目」「试卷」）
     */
    public void requireOwner(String createBy, String target) {
        if (isAdmin()) {
            return;
        }
        String current = LoginUtil.username();
        if (createBy == null || !createBy.equals(current)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "只能操作自己创建的" + target + "（该" + target + "由 " + describe(createBy) + " 创建）");
        }
    }

    private String describe(String createBy) {
        return createBy == null || createBy.isBlank() ? "系统" : createBy;
    }
}
