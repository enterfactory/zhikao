package com.zhikao.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.DictTypeConst;
import com.zhikao.entity.CrawlSource;
import com.zhikao.entity.KnowledgePoint;
import com.zhikao.entity.Question;
import com.zhikao.entity.Subject;
import com.zhikao.entity.SysBanner;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysPermission;
import com.zhikao.entity.SysRole;
import com.zhikao.entity.SysRolePermission;
import com.zhikao.entity.SysSemester;
import com.zhikao.entity.SysUser;
import com.zhikao.entity.SysDictItem;
import com.zhikao.entity.SysDictType;
import com.zhikao.entity.SysUserRole;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.mapper.CrawlSourceMapper;
import com.zhikao.mapper.KnowledgePointMapper;
import com.zhikao.mapper.SysBannerMapper;
import com.zhikao.mapper.SysClassMapper;
import com.zhikao.mapper.SysDictItemMapper;
import com.zhikao.mapper.SysDictTypeMapper;
import com.zhikao.mapper.SysPermissionMapper;
import com.zhikao.mapper.SysRoleMapper;
import com.zhikao.mapper.SysRolePermissionMapper;
import com.zhikao.mapper.SysSemesterMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.mapper.SysUserRoleMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 初始化演示数据：首次启动写入 **RBAC 角色/权限点/角色权限关联**、班级、科目、
 * 演示账号与用户角色关联、**内置字典**，便于开箱演示完整的系统管理功能。
 *
 * <p>演示账号（密码统一取 {@code zhikao.security.default-password} 配置，与
 * PasswordPolicy / 重置密码同一来源，不再在代码里写死）：
 * admin(super_admin) / eduadmin(edu_admin) / lead(subject_lead) / teacher(teacher)
 * / student / student02 / student03(student)。
 *
 * <p>说明：本类只做幂等初始化；由 zhikao.demo-data.enabled 开关控制，
 * **仅开发 profile 允许为 true**。关闭演示数据后仍初始化基础权限与字典，首位管理员由环境变量创建。
 *
 * <p>幂等策略从「roleMapper.selectCount>0 整体跳过」改为**逐行判缺、缺则补**：
 * 半初始化的老库（例如只建了角色、没建权限点）重启后能自愈，
 * 而不是因为「角色表已有数据」把后面的权限点/关联全部跳过——那种库看起来初始化过，
 * 实际鉴权全靠 persona 兜底，是排查起来最费劲的状态。
 */
@Component

public class DataInitializer implements CommandLineRunner {

    // ==================== RBAC 目录（与 Controller 上的 @SaCheckPermission 一一对应） ====================

    /** role_code -> [role_name, persona, sort] */
    private static final Map<String, Object[]> ROLES = new LinkedHashMap<>();

    /** perm_code -> [perm_name, module, sort]；LinkedHashMap 保序即 seed 顺序 */
    private static final Map<String, String[]> PERMISSIONS = new LinkedHashMap<>();

    /** role_code -> 该角色拥有的权限点；super_admin 在 seed 时同样落全量（代码里还有短路，双保险） */
    private static final Map<String, List<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();

    /** username -> [realName, persona(user_type), role_code]；学生账号的班级在建班后补 */
    private static final Map<String, Object[]> DEMO_USERS = new LinkedHashMap<>();

    static {
        ROLES.put("super_admin", new Object[]{"超级管理员", 1, 1});
        ROLES.put("edu_admin", new Object[]{"教务管理员", 1, 2});
        ROLES.put("subject_lead", new Object[]{"学科负责人", 2, 3});
        ROLES.put("teacher", new Object[]{"教师", 2, 4});
        ROLES.put("student", new Object[]{"学生", 3, 5});

        perm("system:user:manage", "用户管理", "system", 1);
        perm("system:class:manage", "班级管理", "system", 2);
        perm("system:subject:manage", "科目管理", "system", 3);
        perm("system:role:view", "角色查看", "system", 4);
        perm("system:role:assign", "角色分配", "system", 5);
        perm("system:dict:manage", "字典管理", "system", 6);
        perm("system:log:view", "日志查看", "system", 7);
        perm("system:semester:manage", "学期管理", "system", 8);
        perm("question:view", "题库查看", "question", 10);
        perm("question:edit", "题目编辑", "question", 11);
        perm("question:audit", "题目审核", "question", 12);
        perm("question:import", "题目导入", "question", 13);
        // 六期：题库采集（爬虫抓取→清洗打标→人工复核入库）。
        // 「抓外部内容」与「把它写进正式题库」是同一条风险链，所以整条链用一个权限点管住，
        // 只给题库管理员（super_admin），不下放给学科负责人/教师。
        perm("crawler:manage", "题库采集", "crawler", 14);
        perm("paper:manage", "组卷管理", "paper", 20);
        perm("exam:manage", "考试管理", "exam", 30);
        perm("exam:publish", "考试发布", "exam", 31);
        perm("marking:do", "阅卷", "marking", 40);
        perm("grade:view", "成绩查看", "grade", 50);
        perm("grade:export", "成绩导出", "grade", 51);
        perm("analysis:view", "学情分析", "analysis", 60);
        perm("ai:chat", "智能对话", "ai", 70);
        perm("ai:gen:question", "智能出题", "ai", 71);
        perm("ai:assist:mark", "辅助阅卷", "ai", 72);
        perm("file:manage", "全站文件管理", "file", 80);
        perm("notification:send", "通知发送", "notification", 90);
        perm("announcement:manage", "公告管理", "announcement", 100);
        // 六期：爬虫与题库清洗（第 26 个权限点，sort 排尾部）
        perm("crawler:manage", "爬虫管理", "crawler", 110);

        // super_admin：全部权限点（与 StpInterfaceImpl 的短路返回一致，seed 兜底保证库内自洽）
        ROLE_PERMISSIONS.put("super_admin", List.copyOf(PERMISSIONS.keySet()));
        ROLE_PERMISSIONS.put("edu_admin", List.of(
                "system:user:manage", "system:class:manage", "system:subject:manage", "system:role:view",
                "system:semester:manage",
                "exam:manage", "exam:publish", "grade:view", "grade:export", "analysis:view",
                "question:view", "paper:manage", "ai:chat", "file:manage",
                "notification:send", "announcement:manage", "crawler:manage"));
        ROLE_PERMISSIONS.put("subject_lead", List.of(
                "crawler:manage", "question:view", "question:edit", "question:audit", "question:import",
                "paper:manage", "exam:manage", "exam:publish", "marking:do",
                "grade:view", "grade:export", "analysis:view", "ai:chat",
                "ai:gen:question", "ai:assist:mark", "system:role:view"));
        ROLE_PERMISSIONS.put("teacher", List.of(
                "crawler:manage", "question:view", "question:edit", "question:import", "paper:manage", "exam:publish",
                "exam:manage", "marking:do", "grade:view", "grade:export", "analysis:view",
                "ai:chat", "ai:gen:question", "ai:assist:mark", "system:role:view"));
        // 学生无 console 管理权限（数据边界由「本人维度」的查询条件保证，见 AnswerService / Notification）；
        // ai:chat 例外：智能答疑是学生端的自助学习入口（首页快捷入口 + 顶部导航都会指向它）
        ROLE_PERMISSIONS.put("student", List.of("ai:chat"));

        DEMO_USERS.put("admin", new Object[]{"管理员", 1, "super_admin"});
        DEMO_USERS.put("eduadmin", new Object[]{"教务管理员", 1, "edu_admin"});
        DEMO_USERS.put("lead", new Object[]{"学科负责人", 2, "subject_lead"});
        DEMO_USERS.put("teacher", new Object[]{"张老师", 2, "teacher"});
        DEMO_USERS.put("student", new Object[]{"李同学", 3, "student"});
        DEMO_USERS.put("student02", new Object[]{"王同学", 3, "student"});
        DEMO_USERS.put("student03", new Object[]{"赵同学", 3, "student"});
    }

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysClassMapper classMapper;
    private final SubjectMapper subjectMapper;
    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictItemMapper dictItemMapper;
    private final SysBannerMapper bannerMapper;
    private final SysSemesterMapper semesterMapper;
    private final QuestionMapper questionMapper;
    private final CrawlSourceMapper crawlSourceMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final SecurityProperties security;
    @org.springframework.beans.factory.annotation.Value("${zhikao.demo-data.enabled:false}")
    private boolean demoEnabled;
    @org.springframework.beans.factory.annotation.Value("${ZHIKAO_BOOTSTRAP_ADMIN:admin}")
    private String bootstrapAdmin;
    @org.springframework.beans.factory.annotation.Value("${ZHIKAO_BOOTSTRAP_PASSWORD:}")
    private String bootstrapPassword;

    public DataInitializer(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysPermissionMapper permissionMapper, SysUserRoleMapper userRoleMapper,
                           SysRolePermissionMapper rolePermissionMapper,
                           SysClassMapper classMapper, SubjectMapper subjectMapper,
                           SysDictTypeMapper dictTypeMapper, SysDictItemMapper dictItemMapper,
                           SysBannerMapper bannerMapper, SysSemesterMapper semesterMapper,
                           QuestionMapper questionMapper, CrawlSourceMapper crawlSourceMapper,
                           KnowledgePointMapper knowledgePointMapper,
                           SecurityProperties security) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.classMapper = classMapper;
        this.subjectMapper = subjectMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.bannerMapper = bannerMapper;
        this.semesterMapper = semesterMapper;
        this.questionMapper = questionMapper;
        this.crawlSourceMapper = crawlSourceMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.security = security;
    }

    private static void perm(String code, String name, String module, int sort) {
        PERMISSIONS.put(code, new String[]{name, module, String.valueOf(sort)});
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        if (demoEnabled) initBaseData();
        initRbac();
        initDict();
        if (demoEnabled) {
            initPortal();
            initCrawler();
        }
    }

    // ==================== 基础业务数据（班级 / 科目） ====================

    private void initBaseData() {
        // 班级（学生必须归属班级）；count>0 视为已有数据（人工维护表，不做逐行 upsert）
        if (classMapper.selectCount(null) == 0) {
            LocalDateTime now = LocalDateTime.now();
            classMapper.insert(new SysClass(null, "软件工程2301班", "2023级", "软件工程", now));
            classMapper.insert(new SysClass(null, "电子信息2401班", "2024级", "电子信息", now));
        }
        // 科目改为**逐行判缺**：六期爬虫的演示题需要「数学/语文」这类 K-12 科目，
        // 老库里已有 3 个大学科目时，整体跳过的写法永远补不上新科目（打标会因匹配不到科目而落空）。
        subject("Java 程序设计", "JAVA");
        subject("数据结构", "DS");
        subject("计算机网络", "NET");
        subject("数学", "MATH");
        subject("语文", "CHINESE");
    }

    /** 科目按名称判缺，缺失才补（已存在绝不改名/改码，人工维护过的数据以库为准） */
    private void subject(String name, String code) {
        if (subjectMapper.selectCount(Wrappers.<Subject>lambdaQuery()
                .eq(Subject::getSubjectName, name)) > 0) {
            return;
        }
        subjectMapper.insert(new Subject(null, name, code, "system", LocalDateTime.now()));
    }

    // ==================== RBAC：角色 / 权限点 / 关联 / 演示账号 ====================

    private void initRbac() {
        java.util.Set<String> existingRoles = roleMapper.selectList(null).stream()
                .map(SysRole::getRoleCode).collect(java.util.stream.Collectors.toSet());
        // 1. 角色
        Map<String, SysRole> roles = new LinkedHashMap<>();
        ROLES.forEach((code, spec) -> roles.put(code, upsertRole(code, (String) spec[0],
                (Integer) spec[1], (Integer) spec[2])));

        // 2. 权限点
        Map<String, SysPermission> permissions = new LinkedHashMap<>();
        PERMISSIONS.forEach((code, spec) -> permissions.put(code, upsertPermission(code,
                spec[0], spec[1], Integer.parseInt(spec[2]))));

        // 3. 角色-权限关联（super_admin 也逐条落库：代码短路 + 数据兜底双保险）
        ROLE_PERMISSIONS.forEach((roleCode, permCodes) -> {
            // Existing installations retain deliberately customized permission grants.
            if (!demoEnabled && existingRoles.contains(roleCode)) return;
            SysRole role = roles.get(roleCode);
            for (String permCode : permCodes) {
                linkRolePermission(role, permissions.get(permCode));
            }
        });

        if (!demoEnabled) {
            if (userMapper.selectCount(null) == 0) {
                if (bootstrapPassword.isBlank()) throw new IllegalStateException("首次启动请设置 ZHIKAO_BOOTSTRAP_PASSWORD，初始化后可移除此环境变量");
                new com.zhikao.common.PasswordPolicy(security).validate(bootstrapPassword);
                SysUser user = upsertUser(bootstrapAdmin, "系统管理员", 1, null,
                        BCrypt.hashpw(bootstrapPassword, BCrypt.gensalt()));
                ensureUserRoleLink(user, roles.get("super_admin"));
            }
            return;
        }
        // 4. 演示账号与用户角色关联（密码取配置，来源与重置密码/密码策略一致）
        String pwd = BCrypt.hashpw(security.getDefaultPassword(), BCrypt.gensalt());
        Long softwareClassId = classIdOf("软件工程2301班");
        Long electronicsClassId = classIdOf("电子信息2401班");
        DEMO_USERS.forEach((username, spec) -> {
            int persona = (Integer) spec[1];
            Long classId = switch (username) {
                case "student", "student02" -> softwareClassId;
                case "student03" -> electronicsClassId;
                default -> null;
            };
            SysUser user = upsertUser(username, (String) spec[0], persona, classId, pwd);
            ensureUserRoleLink(user, roles.get((String) spec[2]));
        });
    }

    /** 角色 upsert：role_code 已存在直接复用，缺失即补（半初始化老库自愈的关键） */
    private SysRole upsertRole(String code, String name, int persona, int sort) {
        SysRole existing = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, code).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        SysRole role = SysRole.builder().roleCode(code).roleName(name).persona(persona).sort(sort).build();
        roleMapper.insert(role);
        return role;
    }

    /** 权限点 upsert */
    private SysPermission upsertPermission(String code, String name, String module, int sort) {
        SysPermission existing = permissionMapper.selectOne(Wrappers.<SysPermission>lambdaQuery()
                .eq(SysPermission::getPermCode, code).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        SysPermission permission = SysPermission.builder()
                .permCode(code).permName(name).module(module).sort(sort).build();
        permissionMapper.insert(permission);
        return permission;
    }

    /** 角色-权限关联：已有即跳过，避免与人工调整打架 */
    private void linkRolePermission(SysRole role, SysPermission permission) {
        if (role == null || permission == null) {
            return;
        }
        long linked = rolePermissionMapper.selectCount(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, role.getId())
                .eq(SysRolePermission::getPermId, permission.getId()));
        if (linked == 0) {
            rolePermissionMapper.insert(SysRolePermission.builder()
                    .roleId(role.getId()).permId(permission.getId()).build());
        }
    }

    /** 用户 upsert：按 username 判重，已存在不覆盖（密码等字段可能已被人工改过） */
    private SysUser upsertUser(String username, String realName, int userType, Long classId, String pwd) {
        SysUser existing = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser user = SysUser.builder()
                .username(username)
                .password(pwd)
                .realName(realName)
                .userType(userType)
                .classId(classId)
                .status(1)
                .createTime(now)
                .updateTime(now)
                .build();
        userMapper.insert(user);
        return user;
    }

    /**
     * 保证用户有角色关联：仅在该用户**一条关联都没有**时补默认角色。
     * 已有任何人工分配结果时不覆盖——初始化器的职责是「缺了补上」，不是「每启动一次归零一次」。
     */
    private void ensureUserRoleLink(SysUser user, SysRole role) {
        if (user == null || role == null) {
            return;
        }
        long linked = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, user.getId()));
        if (linked == 0) {
            userRoleMapper.insert(SysUserRole.builder().userId(user.getId()).roleId(role.getId()).build());
        }
    }

    /** 按名称取班级 id（建班与用班可能分两次启动完成，取不到时返回 null 不阻塞） */
    private Long classIdOf(String className) {
        SysClass clazz = classMapper.selectOne(Wrappers.<SysClass>lambdaQuery()
                .eq(SysClass::getClassName, className).last("limit 1"));
        return clazz == null ? null : clazz.getId();
    }

    // ==================== 内置字典 ====================

    /**
     * 内置字典：把原本散落在前端各页面的 const OPTIONS 收敛到一处。
     * 注意：字典只承载展示型枚举，业务判分逻辑仍在 Java 枚举里，二者不混用。
     * 幂等策略：类型按 dict_type 判缺，条目按 (dict_type, item_value) 判缺——
     * 老库新增一个字典类型/条目时重启即可补齐，不再整段跳过。
     */
    private void initDict() {
        // 题型
        dict(DictTypeConst.QUESTION_TYPE, "题型", "题目类型（与 QuestionTypeEnum 对应）");
        item(DictTypeConst.QUESTION_TYPE, "单选", "1", "primary", 1);
        item(DictTypeConst.QUESTION_TYPE, "多选", "2", "warning", 2);
        item(DictTypeConst.QUESTION_TYPE, "判断", "3", "success", 3);
        item(DictTypeConst.QUESTION_TYPE, "填空", "4", "info", 4);
        item(DictTypeConst.QUESTION_TYPE, "简答", "5", "danger", 5);

        // 难度
        dict(DictTypeConst.DIFFICULTY, "难度", "1-5，数字越大越难");
        item(DictTypeConst.DIFFICULTY, "很容易", "1", "success", 1);
        item(DictTypeConst.DIFFICULTY, "简单", "2", "success", 2);
        item(DictTypeConst.DIFFICULTY, "中等", "3", "primary", 3);
        item(DictTypeConst.DIFFICULTY, "较难", "4", "warning", 4);
        item(DictTypeConst.DIFFICULTY, "很难", "5", "danger", 5);

        // 用户角色：RBAC 改造后字典值即 role_code（5 个），供角色下拉/标签展示
        dict(DictTypeConst.USER_ROLE, "用户角色", "与 sys_role.role_code 对应");
        ROLES.forEach((code, spec) ->
                item(DictTypeConst.USER_ROLE, (String) spec[0], code, roleColor(code), (Integer) spec[2]));

        // 考试状态
        dict(DictTypeConst.EXAM_STATUS, "考试状态", "由考试时间窗自动推导");
        item(DictTypeConst.EXAM_STATUS, "未开始", "0", "info", 1);
        item(DictTypeConst.EXAM_STATUS, "进行中", "1", "warning", 2);
        item(DictTypeConst.EXAM_STATUS, "已结束", "2", "success", 3);

        // 考试发布状态
        dict(DictTypeConst.EXAM_PUBLISHED, "考试发布状态", "未发布时学生不可见");
        item(DictTypeConst.EXAM_PUBLISHED, "草稿", "0", "info", 1);
        item(DictTypeConst.EXAM_PUBLISHED, "已发布", "1", "success", 2);

        // 学生作答状态
        dict(DictTypeConst.EXAM_STUDENT_STATUS, "学生作答状态", "待考 → 考试中 → 已交卷 → 已阅卷");
        item(DictTypeConst.EXAM_STUDENT_STATUS, "待考", "0", "info", 1);
        item(DictTypeConst.EXAM_STUDENT_STATUS, "考试中", "1", "warning", 2);
        item(DictTypeConst.EXAM_STUDENT_STATUS, "已交卷", "2", "primary", 3);
        item(DictTypeConst.EXAM_STUDENT_STATUS, "已阅卷", "3", "success", 4);

        // 试卷生成方式
        dict(DictTypeConst.PAPER_GEN_TYPE, "试卷生成方式", "手动勾选 / 按规则随机抽取");
        item(DictTypeConst.PAPER_GEN_TYPE, "手动组卷", "1", "primary", 1);
        item(DictTypeConst.PAPER_GEN_TYPE, "随机组卷", "2", "info", 2);

        // 试卷状态
        dict(DictTypeConst.PAPER_STATUS, "试卷状态", "草稿试卷不能用于创建考试");
        item(DictTypeConst.PAPER_STATUS, "草稿", "0", "info", 1);
        item(DictTypeConst.PAPER_STATUS, "已发布", "1", "success", 2);

        // AI 任务类型
        dict(DictTypeConst.AI_TASK_TYPE, "AI 任务类型", "落 ai_task 的 type 字段");
        item(DictTypeConst.AI_TASK_TYPE, "智能出题", "gen_question", "primary", 1);
        item(DictTypeConst.AI_TASK_TYPE, "辅助阅卷", "assist_mark", "warning", 2);
        item(DictTypeConst.AI_TASK_TYPE, "错题分析", "analysis", "info", 3);
        item(DictTypeConst.AI_TASK_TYPE, "智能对话", "chat", "success", 4);

        // AI 任务状态
        dict(DictTypeConst.AI_TASK_STATUS, "AI 任务状态", "落 ai_task 的 status 字段");
        item(DictTypeConst.AI_TASK_STATUS, "待处理", "0", "info", 1);
        item(DictTypeConst.AI_TASK_STATUS, "成功", "1", "success", 2);
        item(DictTypeConst.AI_TASK_STATUS, "失败", "2", "danger", 3);
    }

    /** 角色标签色：两级管理员偏警示色，学科负责人/教师主色，学生信息色 */
    private static String roleColor(String roleCode) {
        return switch (roleCode) {
            case "super_admin" -> "danger";
            case "edu_admin" -> "warning";
            case "subject_lead", "teacher" -> "primary";
            default -> "info";
        };
    }

    /** 插入字典类型（dict_type 已存在则跳过） */
    private void dict(String type, String name, String remark) {
        if (dictTypeMapper.selectCount(Wrappers.<SysDictType>lambdaQuery()
                .eq(SysDictType::getDictType, type)) > 0) {
            return;
        }
        dictTypeMapper.insert(SysDictType.builder()
                .dictName(name)
                .dictType(type)
                .remark(remark)
                .status(1)
                .createTime(LocalDateTime.now())
                .build());
    }

    /** 插入字典项（(dict_type, item_value) 已存在则跳过） */
    private void item(String type, String label, String value, String color, int sort) {
        if (dictItemMapper.selectCount(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictType, type)
                .eq(SysDictItem::getItemValue, value)) > 0) {
            return;
        }
        dictItemMapper.insert(SysDictItem.builder()
                .dictType(type)
                .itemLabel(label)
                .itemValue(value)
                .color(color)
                .sort(sort)
                .status(1)
                .createTime(LocalDateTime.now())
                .build());
    }

    // ==================== 五期：门户与学期（Banner / Semester / 演示题目） ====================

    /**
     * 门户首页与学期模型的演示数据：
     * <ul>
     *   <li>轮播 3 帧：文案取自五期视觉稿（联考报名 / 真题库上新 / AI 错题归因），按 title 判缺；</li>
     *   <li>当前学期：「2026-2027学年 第一学期」，按 name 判缺；库内没有任何当前学期时才置 is_current=1，
     *       不覆盖人工切换过的当前学期；</li>
     *   <li>演示题目：question 表按科目判缺——门户首页 subjectStats 的断言依赖题库有题，
     *       空题库时首页只是一堆空卡片，演示不出「刷题进度」的产品叙事。</li>
     * </ul>
     * link_target 对齐 zhikao-web 现有路由（/student/exam、/ai/chat），前端接新练习页时改库即可，不用改代码。
     */
    private void initPortal() {
        banner("高一全科联考模拟 10月18日开考",
                "同步命题组出题 · 考完即时出排名 · 已有 1,246 人报名",
                "立即报名", "exam", "route", "/student/exam", 1);
        banner("近 5 年高考真题库全面上线",
                "语数英理化生 6 科 · 12,800 题 · 含逐题解析与视频讲解",
                "去刷题", "paper", "route", "/student/exam", 2);
        banner("错题AI归因分析 本周免费升级",
                "定位薄弱知识点 · 生成专属补弱卷 · 平均提分 11.3",
                "体验小智老师", "ai", "route", "/ai/chat", 3);

        semester("2026-2027学年 第一学期", "2026-2027", 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));

        seedDemoQuestions();
    }

    /** 轮播帧：按 title 判缺，已存在不覆盖（文案可能被运营改过） */
    private void banner(String title, String subtitle, String cta, String theme,
                        String linkType, String linkTarget, int sort) {
        if (bannerMapper.selectCount(Wrappers.<SysBanner>lambdaQuery()
                .eq(SysBanner::getTitle, title)) > 0) {
            return;
        }
        bannerMapper.insert(SysBanner.builder()
                .title(title)
                .subtitle(subtitle)
                .cta(cta)
                .theme(theme)
                .linkType(linkType)
                .linkTarget(linkTarget)
                .sort(sort)
                .status(1)
                .createTime(LocalDateTime.now())
                .build());
    }

    /** 学期：按 name 判缺；仅当库内尚无当前学期时该帧才成为当前学期 */
    private void semester(String name, String schoolYear, int termNo,
                          LocalDate start, LocalDate end) {
        if (semesterMapper.selectCount(Wrappers.<SysSemester>lambdaQuery()
                .eq(SysSemester::getName, name)) > 0) {
            return;
        }
        long currentCount = semesterMapper.selectCount(Wrappers.<SysSemester>lambdaQuery()
                .eq(SysSemester::getIsCurrent, 1));
        semesterMapper.insert(SysSemester.builder()
                .name(name)
                .schoolYear(schoolYear)
                .termNo(termNo)
                .startDate(start)
                .endDate(end)
                .isCurrent(currentCount == 0 ? 1 : 0)
                .build());
    }

    /**
     * 演示题库：每个科目补 3 道单选题（Java / 数据结构 / 计算机网络各 3 道），
     * 仅在该科目题数为 0 时写入——已有真实/导入题目数据的科目绝不被污染。
     * options 是 MySQL JSON 列，必须是合法 JSON 串。
     */
    private void seedDemoQuestions() {
        String[][] templates = {
                {"下列哪个关键字用于定义 Java 类？", "A: interface, B: class, C: struct, D: void", "B", "class 是 Java 定义类的关键字"},
                {"Java 中所有类的根父类是？", "A: Object, B: String, C: System, D: Class", "A", "java.lang.Object 是所有类的超类"},
                {"String 类的特点是？", "A: 可变, B: 不可变, C: 线程不安全, D: 无缓存", "B", "String 内部字符数组为 final，不可变"},
        };
        String[][] templatesDs = {
                {"栈的操作特性是？", "A: 先进先出, B: 后进先出, C: 随机存取, D: 顺序存取", "B", "栈是 LIFO 结构"},
                {"链表相比数组的优势是？", "A: 随机访问快, B: 插入删除不必搬移, C: 占用内存少, D: 缓存友好", "B", "链表插入删除只改指针"},
                {"队列的典型应用是？", "A: 递归, B: 广度优先搜索, C: 表达式求值, D: 排序", "B", "BFS 用队列逐层扩展"},
        };
        String[][] templatesNet = {
                {"TCP 协议的特点是？", "A: 无连接, B: 面向连接且可靠, C: 不排序, D: 仅组播", "B", "TCP 三次握手、确认重传"},
                {"HTTP 默认端口是？", "A: 21, B: 443, C: 80, D: 8080", "C", "HTTP 默认 80，HTTPS 默认 443"},
                {"IP 地址 192.168.1.1 属于哪类地址？", "A: A 类, B: B 类, C: C 类, D: D 类", "C", "192.168.x.x 为 C 类私有地址"},
        };
        Map<String, String[][]> bySubject = Map.of(
                "Java 程序设计", templates,
                "数据结构", templatesDs,
                "计算机网络", templatesNet);
        // Map.of 无序，用列表固定 seed 顺序，便于冒烟断言的稳定性
        for (Subject subject : subjectMapper.selectList(null)) {
            String[][] pool = bySubject.get(subject.getSubjectName());
            if (pool == null) {
                continue;
            }
            if (questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                    .eq(Question::getSubjectId, subject.getId())) > 0) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < pool.length; i++) {
                String[] t = pool[i];
                questionMapper.insert(Question.builder()
                        .subjectId(subject.getId())
                        .type(1)
                        .content(t[0])
                        // 列类型为 JSON：拼成 {"A":..,"B":..} 的合法 JSON 串
                        .options(toOptionsJson(t[1]))
                        .answer(t[2])
                        .analysis(t[3])
                        .difficulty(2)
                        .knowledgePoint("演示种子题")
                        .score(new BigDecimal("5.00"))
                        .createBy("system")
                        .createTime(now)
                        .updateTime(now)
                        .build());
            }
        }
    }

    /** "A: xx, B: yy, C: zz, D: ww" -> {"A":"xx","B":"yy","C":"zz","D":"ww"}（MySQL JSON 列要求合法 JSON） */
    private static String toOptionsJson(String plain) {
        StringBuilder sb = new StringBuilder("{");
        String[] parts = plain.split(",\\s*(?=[A-D]:)");
        for (int i = 0; i < parts.length; i++) {
            int idx = parts[i].indexOf(':');
            String key = parts[i].substring(0, idx).trim();
            String value = parts[i].substring(idx + 1).trim().replace("\"", "\\\"");
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(key).append("\":\"").append(value).append('"');
        }
        return sb.append('}').toString();
    }

    // ==================== 六期：爬虫与题库清洗 ====================

    /** 示例源名称，幂等判缺的键 */
    private static final String DEMO_CRAWLER_SOURCE = "本地演示题源（示例）";

    /**
     * 六期爬虫种子：
     * <ul>
     *   <li>补 K-12 基础科目（数学/语文/英语）——题库现有科目是编程向，演示题是 K-12 学科向，
     *       不补科目则 ClassifierTagger 匹配不到、待审题目科目会全空。按 subject_name 逐行判缺，绝不覆盖已有；</li>
     *   <li>一条 LOCAL_DIR 示例源，base_url 指向项目内 <code>题目资源/示例</code>（相对 user.dir 解析，
     *       与 zhikao.crawler.allowed-roots 同一基准，保证通过白名单校验），默认科目取「数学」。</li>
     * </ul>
     */
    private void initCrawler() {
        ensureSubject("数学", "MATH");
        ensureSubject("语文", "CHINESE");
        ensureSubject("英语", "ENGLISH");
        Long mathId = subjectIdOf("数学");
        Long chineseId = subjectIdOf("语文");

        // 少量 K-12 知识点节点，令 ClassifierTagger 的「包含式知识点匹配」和 Promoter 的
        // question_knowledge 关联在演示里可被观察到（按 名称+科目 逐行判缺）
        ensureKnowledgePoint(mathId, "平均分与除法");
        ensureKnowledgePoint(mathId, "长方形周长");
        ensureKnowledgePoint(mathId, "最大公因数");
        ensureKnowledgePoint(mathId, "勾股定理");
        ensureKnowledgePoint(chineseId, "病句辨析");
        ensureKnowledgePoint(chineseId, "古诗理解");
        ensureKnowledgePoint(chineseId, "文学常识");

        if (crawlSourceMapper.selectCount(Wrappers.<CrawlSource>lambdaQuery()
                .eq(CrawlSource::getName, DEMO_CRAWLER_SOURCE)) > 0) {
            return;
        }
        // 项目内示例目录绝对路径：<user.dir>/../题目资源/示例，与 allowed-roots「../题目资源」同基准
        String demoDir = Paths.get(System.getProperty("user.dir"), "..", "题目资源", "示例")
                .toAbsolutePath().normalize().toString();
        crawlSourceMapper.insert(CrawlSource.builder()
                .name(DEMO_CRAWLER_SOURCE)
                .baseUrl(demoDir)
                .sourceType("LOCAL_DIR")
                .licenseNote("演示数据：题目为本项目自编 K-12 示范题，不含任何真实版权题库内容")
                .rateLimitMs(0)
                .defaultSubjectId(mathId)
                .enabled(1)
                .remark("采集冒烟示例源：指向 题目资源/示例 下的 2 个 Markdown 模板 + 1 个 JSON 行文件")
                .createTime(LocalDateTime.now())
                .build());
    }

    /** 按 名称+科目 补知识点节点（已存在则跳过，绝不动既有人工数据） */
    private void ensureKnowledgePoint(Long subjectId, String name) {
        if (subjectId == null) {
            return;
        }
        if (knowledgePointMapper.selectCount(Wrappers.<KnowledgePoint>lambdaQuery()
                .eq(KnowledgePoint::getSubjectId, subjectId)
                .eq(KnowledgePoint::getName, name)) > 0) {
            return;
        }
        knowledgePointMapper.insert(KnowledgePoint.builder()
                .subjectId(subjectId)
                .name(name)
                .sort(0)
                .build());
    }

    /** 按名称补科目（subject_name 已存在则跳过，绝不动既有数据） */
    private void ensureSubject(String name, String code) {
        if (subjectMapper.selectCount(Wrappers.<Subject>lambdaQuery()
                .eq(Subject::getSubjectName, name)) > 0) {
            return;
        }
        subjectMapper.insert(new Subject(null, name, code, "system", LocalDateTime.now()));
    }

    /** 按名称取科目 id，取不到返回 null */
    private Long subjectIdOf(String name) {
        Subject subject = subjectMapper.selectOne(Wrappers.<Subject>lambdaQuery()
                .eq(Subject::getSubjectName, name).last("limit 1"));
        return subject == null ? null : subject.getId();
    }
}
