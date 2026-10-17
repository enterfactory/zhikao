# 智考在线 · Zhikao

面向教师与中小教学组织的教学测评项目，将资料处理和考试后的学习反馈连成一条流程：

**PDF / Word 导入 → 识别题目与公式 → 对照原文复核 → 审核入库 → 组卷考试 → 阅卷与发布 → 考后练习。**

当前是持续完善的自部署项目。主要完成了业务闭环与阶段性正确性验证，尚不代表经过大规模正式考试或生产容量验收。

## 技术栈

| 层次 | 实现 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3、MyBatis / MyBatis-Plus |
| 数据与迁移 | PostgreSQL、Flyway V1–V7、Redis |
| 权限 | Sa-Token、RBAC、业务数据归属校验 |
| 前端 | Vue 3、Vite、Pinia、Element Plus、ECharts、KaTeX |
| 资料处理 | PDFBox、Apache POI、RapidOCR，按配置接入云端识别 |
| AI 扩展 | AgentScope Java、OpenAI 兼容模型接口、Qdrant 与 Embedding |

## 已实现的主要能力

- 管理端、教师端和学生端；组织内用户、班级、科目、知识点与角色权限管理。
- 文档按内容哈希与解析配置区分版本，保留不可变原文快照；PDF 题干页码与起始区域定位，Word 文本锚点核对。
- 候选题修订版本、人工确认、驳回与审核记录；新旧审核入口共用应用服务，避免重复入库与覆盖他人修订。
- 题目进入试卷时保存题目和知识点快照；考试发布时冻结参考名单，避免后续改题、改名和转班影响历史解释。
- 服务端校验作答期限，事务化收卷与判分，重复交卷处理；阅卷版本校验，成绩与解析分级发布。
- 已开放解析的个人考试题目生成练习，按科目与稳定知识点 ID 归类；同一试卷用于多场考试时去重。
- 文件共享字节与个人业务引用分离，上传内容校验、业务引用删除保护与延迟回收。
- 事务内写通知 outbox，异步重试投递，通过事件与接收人唯一约束控制重复通知。
- 标准标注、两个不同账号签核、开发集与保留集隔离；保存机器原始预测，按标注版本生成质量报告。

## 仓库范围

```text
zhikao-server/       后端业务源码、MyBatis Mapper、迁移和测试
zhikao-web/          前端源码、静态资源、依赖锁文件和构建配置
.env.ecs.example     无凭据的基础设施配置模板
LICENSE             保留原项目 BSD 3-Clause 许可证
```

本仓库不包含原仓库的 Git 历史、ECS 凭据、数据库数据、上传原文、采集语料、日志、模型文件、node_modules、JAR、dist、设计稿或求职材料。

## 启动

准备 JDK 17、Maven 3.9+、Node.js 22 LTS，以及自己的 PostgreSQL 和 Redis。默认 `ecs` profile 使用 PostgreSQL；`local` 目前也是同一基础设施配置入口，不能理解为无依赖演示模式。默认不生成演示班级、教师和学生账号。

1. 在仓库根目录复制配置模板：

```powershell
Copy-Item .env.ecs.example .env.ecs.local
```

2. 在 `.env.ecs.local` 填写自己的 PostgreSQL、Redis 和首个管理员配置。数据库须预先存在；数据库账号需要创建业务 schema 和运行迁移的权限。首个管理员设置 `ZHIKAO_BOOTSTRAP_ADMIN` 和符合口令策略的 `ZHIKAO_BOOTSTRAP_PASSWORD`。该文件不会提交到 Git。

3. 首次可关闭外部 AI 和向量能力，启动核心业务：

```powershell
Set-Location zhikao-server
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=ecs --zhikao.ai.enabled=false --zhikao.vector.enabled=false"
```

后端地址为 `http://localhost:8080/api`，健康检查为 `http://localhost:8080/api/actuator/health`。Flyway 自动执行 PostgreSQL 迁移。上传文件和原文快照默认位于应用本地磁盘，需要持久化并与数据库一起备份。

4. 在另一个终端启动前端：

```powershell
Set-Location zhikao-web
npm ci
npm run dev
```

打开 `http://localhost:5173`。Vite 将 `/api` 代理到后端 8080；可用环境变量 `ZHIKAO_API_PROXY` 调整开发代理。

5. 启用 AI 时，填写有效的模型和 Embedding 服务配置，准备 Qdrant，并移除启动参数中的两个关闭开关。模型名称、额度、区域可用性与费用由使用者自己的服务决定。向量集合与其他项目分开使用。

## 构建与测试

```powershell
# 在 zhikao-server 中：不依赖远程服务的定向单元测试
mvn "-Dtest=QualityScoringTest,FileTypeVerifierTest,DocumentParserTest,AnswerVisibilityPolicyTest" test

# 在 zhikao-server 中：需要你自己的真实 PostgreSQL / Redis 配置
mvn "-Dtest=StageBIntegrationTest,StatisticsLedgerIntegrationTest" test

# 在 zhikao-server 中：构建后端
mvn -DskipTests package

# 在 zhikao-web 中
npm test
npm run build
```

集成测试创建隔离的 `zhikao_test_*` schema 和 Redis 命名空间。阶段 B 测试会保留专用 schema 以便检查结果，请使用专门的测试实例或测试数据库并自行管理这些数据。不要把默认 `mvn test` 理解为离线测试，部分用例会访问配置的真实基础设施。

2026-10-17 开发环境验收分批通过了 35 项后端专项及相关回归、2 项前端队列测试、8 项离线质量工具测试，另通过 22 项部署后 HTTP 检查。离线质量工具与本机验收材料没有随本次源码发布。上述结果不是一次全仓回归通过，也不代表你的部署环境已验收。

## 已知边界

- 自然资料尚缺完成独立双人签核的质量基准，不能宣称公式识别率、OCR 准确率或审核效率提升百分比。
- 公式评分使用归一化 LaTeX 字符串匹配，不等于数学语义等价判定；Word 不伪造页码或 PDF 式坐标。
- 已有并发正确性专项，不包含 50 / 200 / 500 人容量结论、长期稳定性或完整故障恢复认证。
- AI 产出需要人工核对；主观题辅助阅卷不应被理解为自动产生正式成绩。
- Redis 会话与跨实例撤权仍有进一步故障验证工作；前端生产构建仍有较大 vendor chunk 提示。
- 文件类型检查不是恶意软件扫描；跨实例共享文件存储、运维备份和部署入口还需完善。

## 许可证

保留原项目 [BSD 3-Clause License](LICENSE)。第三方依赖、OCR 引擎、模型及外部资料按各自许可证使用；本仓库不分发用户题库或原始试卷资料。
