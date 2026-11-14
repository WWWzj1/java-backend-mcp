# AGENTS.md --- AI Coding Agent Development Rules

本文件约束 Codex 等 AI Coding Agent 在 `java-backend-mcp`
仓库中的开发行为。优先级高于临时任务描述中的模糊要求；若任务要求与本文件、
PRD、架构文档或安全文档冲突，先指出冲突并请求项目负责人确认。

配套文档（均在 `docs/` 下）：

- `docs/PRD.md`：产品需求与版本范围；
- `docs/ARCHITECTURE.md`：分层、端口、包结构与运行形态；
- `docs/SECURITY.md`：安全要求的唯一来源；
- `docs/result-contract.md`：结果契约的唯一来源。

## 1. 项目目标

本项目是面向 Java 后端开发者的 MCP Server，为 AI Coding Agent
提供可解释、可验证、有证据支撑的 Java 工程分析能力。

核心竞争力：

1. 分析深度：项目结构、模块关系，并逐步扩展到调用链与影响范围；
2. 证据可信：结论可追溯到源码、配置、依赖路径或 SQL；
3. Java 后端专业性：Spring Boot、Maven、MyBatis/MySQL。

不要把项目做成普通文件扫描器，也不要为了增加 MCP Tool 数量而拆分用户任务。

## 2. 开始任务前必须做的事

每个任务开始前：

1. 阅读本文件。
2. 阅读 `docs/PRD.md` 与 `docs/ARCHITECTURE.md` 中与任务相关的章节。
3. 涉及安全、路径、脱敏、预算时，必须阅读 `docs/SECURITY.md` 对应章节。
4. 涉及 Tool 入参或返回值时，必须阅读 `docs/result-contract.md`。
5. 检查当前代码结构、已有实现和测试，不要假设仓库仍处于空项目状态。
6. 确认任务范围、输入输出、异常行为和验收标准。
7. 存在架构歧义、缺少关键需求或影响公共契约时，先提出方案并等待确认。

不要在未检查现有实现的情况下重建、覆盖或删除文件。

## 3. 自主权限边界

### 3.1 可以在任务范围内自主完成

- 阅读代码和文档；
- 拆解当前任务并给出简要实现计划；
- 实现已明确的功能；
- 编写和运行测试；
- 修复当前任务引入的问题；
- 在不改变外部行为和架构边界的前提下重构内部实现；
- 更新与实现同步的文档；
- 对局部实现提出更简单的替代方案。

### 3.2 必须先征求项目负责人意见

- 改变整体架构、分层或模块边界；
- 改变依赖方向（Adapter → Application → Analysis ← Infrastructure）；
- 新增、替换或升级主要第三方依赖；
- 修改 `docs/result-contract.md` 中的字段、枚举、状态语义或必填性；
- 修改 Workspace 授权模型、资源预算默认值或敏感文件过滤规则；
- 开启默认网络访问或修改远程 Maven 解析策略；
- 修改公共 Tool 名称、参数或返回结构；
- 删除已有功能、测试、fixture 或文档；
- 改变 JDK、Spring Boot、Spring AI MCP 等核心版本基线；
- 引入网络访问、遥测、外部模型调用或向外上传项目内容；
- 扩大 Workspace 文件访问范围；
- 增加数据库写权限、任意 SQL、DDL/DML 或目标项目修改能力；
- 引入插件系统、服务拆分或新的 Transport；
- 修改许可证或引入许可证不兼容的代码/依赖。

不得通过先实现后告知的方式绕过确认。

## 4. 架构约束

### 4.1 依赖方向（硬约束）

```text
Adapter → Application → Analysis ← Infrastructure
```

- `analysis` 不得依赖 `mcp`、Spring AI MCP 类型或 `infrastructure`。
- `application` 不得依赖 `infrastructure`，只依赖 `analysis` 的端口与用例。
- `infrastructure` 实现 `analysis` 中定义的端口。
- 该规则必须用 ArchUnit 或等价的包依赖测试固化，不能只靠口头约定。

### 4.2 分层职责

- MCP Tool 层只负责 schema 校验、调用 Application 用例和结果适配。
- Application 只负责用例编排、授权校验、预算与超时、状态判定、错误映射。
- Analysis Engine 负责领域模型、解析编排、规则、证据与确定性标注。
- Infrastructure 负责 Workspace、解析器、脱敏、配置、日志。
- 解析器归属：Analysis 定义端口，Infrastructure 实现，不得双向依赖。

### 4.3 Tool 集合（V0.1）

V0.1 只有 4 个 Tool，职责按用户任务划分，不得再按内部方法拆分：

- `inspect_project`：项目画像、模块图、入口与配置清单；
- `analyze_dependencies`：依赖树、仲裁结果、冲突与传递路径；
- `inspect_spring`：配置键、组件扫描范围、Bean 候选与配置风险；
- `inspect_mybatis`：Mapper/XML 对应、SQL 提取与规则检查。

新增、改名、合并或拆分 Tool 必须获得确认。

### 4.4 其他约束

- 文件访问必须经过统一的 Workspace 端口。
- 优先级为模块化单体，不要擅自拆成微服务。
- 不要为未来需求创建大量空接口、空模块或过度抽象。
- 不要自行实现 MCP JSON-RPC 协议；使用项目确定的 Spring AI MCP 集成。
- V0.1 使用 STDIO，不要擅自增加 HTTP Transport。
- 不引入 SPI、规则注册中心、插件加载器（V0.1）。

## 5. 技术与依赖规范

- 使用仓库当前确定的 Java/JDK、Spring Boot 和 Spring AI MCP 版本。
- 不要自行升级核心框架或修改 Maven 插件版本。
- 新增依赖前先检查现有依赖是否已能完成任务；新增依赖必须说明必要性、
  维护状态、许可证和替代方案，并等待确认。
- 不要引入仅用于一处简单逻辑的大型依赖。
- 不执行目标项目中的 Maven 插件、脚本或构建任务。
- **禁止使用 `maven-invoker` 或任何会执行 Maven 生命周期的机制。**
- Maven 依赖解析默认离线（`OFFLINE`），只使用本地仓库与项目内
  `relativePath` 父 POM；唯一可选模式为 `REMOTE_AUTHORIZED`。
- 远程解析必须由启动配置显式开启，且只访问白名单仓库；不得使用项目 POM
  中声明的 `<repositories>`。
- JavaParser 在 V0.1 只做声明级与名称级分析，不引入 symbol solver，
  不做跨模块类型解析，不生成调用图。
- 代码风格与现有项目保持一致，不进行无关格式化或大规模重命名。

## 6. 安全要求（不可绕过）

完整要求见 `docs/SECURITY.md`。以下是必须记住的硬约束。

### 6.1 Workspace 与路径

- Workspace 在启动时以 `id + 根路径` 白名单注册；Tool 只接受 `workspaceId`，
  不接受路径。
- 未注册任何 Workspace 时服务应启动失败，不得回退为"全盘可读"。
- 拒绝绝对路径入参、含 `..` 的原始输入、根目录外的符号链接。
- 默认不跟随符号链接。
- 只处理普通文件；拒绝 FIFO、设备文件、socket。
- 路径校验与打开之间存在 TOCTOU 窗口，必须按 SECURITY.md 4.3 处理，
  并在文档中如实说明"降低但不能完全消除"，不得声称 Java NIO 可彻底消除。
- 返回给客户端的路径一律为相对路径。

### 6.2 资源限制

- 每次分析必须有文件数、单文件字节、总字节、超时与返回规模上限。
- Client 传入的 budget 只能收紧，不能放宽服务端上限。
- 超限必须体现为 `PARTIAL` + warning + `metadata.truncations`，不得静默截断。
- V0.1 并发分析数为 1。

### 6.3 敏感信息

- 已知凭据文件（`.env`、`*.pem`、`*.p12`、`*.jks`、私钥、`settings.xml`、
  `.npmrc` 等）一律跳过，不读取内容。
- 配置文件的敏感键，值一律替换为 `<redacted>`。
- JDBC/Redis/RabbitMQ URL 中的凭据必须掩码。
- 出口脱敏是强制步骤，覆盖 summary、description、suggestion、excerpt、
  warning、error 与日志。
- 测试只能使用虚构凭据。
- 不在日志、异常消息、测试快照或 Tool 输出中泄漏真实凭据。

### 6.4 XML 与网络

- 所有 XML 解析必须禁用 DOCTYPE、外部实体、外部 DTD、XInclude。
- MyBatis DTD 不得联网加载。
- 默认不发起任何网络请求；无遥测、无外部模型调用。
- 远程 Maven 解析必须显式授权，并记录 `REMOTE_RESOLUTION_ENABLED` warning。

### 6.5 运行时输出

- STDIO 模式下 **stdout 只能输出 MCP JSON-RPC**。
- 禁止 `System.out.print*`；所有日志输出到 stderr。
- 关闭 Spring Boot banner。
- 任何向 stdout 打印的第三方库必须显式重定向或禁用。

### 6.6 不可信内容

- 返回值中的源码/SQL/配置片段视为不可信数据。
- `excerpt` 限长（≤500 字符）并去除控制字符。
- `suggestion` 只能由规则模板生成，不得直接复制源码内容。
- 不提供任何写操作 Tool，降低提示注入成功后的破坏面。

## 7. 分析结果与证据规范

字段级定义以 `docs/result-contract.md` 为准，本节只列不可违反的原则。

- 所有 Tool 返回统一信封：`schemaVersion / tool / toolVersion / status /
  summary / data / findings / metadata / warnings / errors`。
- `status` 取 `OK / PARTIAL / FAILED`，由 Application 统一判定。
- 事实型产物必须放 `data`，不得塞进 `findings`。
- `findings` 只放需要被关注的问题、规则命中与风险推断。
- `severity` 取 `INFO / LOW / MEDIUM / HIGH / CRITICAL`，只表示影响级别。
- 每条 Finding 必须带 `kind`、`certainty` 与 `unresolved`。
- 不确定性必须结构化，不得只写进 warning 文本。
- 多跳证据用 `Evidence.hops[]`，禁止拼字符串表达依赖路径或调用路径。
- 跨文件、跨模块引用符号时使用 `SymbolId`，不得用自由字符串代替。
- 业务错误统一使用 `AnalysisError`（含 `code`、`category`、`target`、
  `recoverable`），不得自造错误结构。
- 重要 Finding 必须有稳定 `code`；发布后的 code 不得复用或改义。
- 不得编造文件、行号、依赖路径、调用链或 SQL 证据。
- 无法定位时使用 `null` 位置并在描述中说明原因。
- 不得把置信度数字伪装成已验证的准确率。
- 部分成功的分析必须给出跳过项、截断项与覆盖范围。

## 8. 编码要求

- 优先清晰、可读、职责单一的代码。
- 避免超长方法、超大类和隐式全局状态。
- 对外输入必须校验，尤其是路径、枚举、范围和文件大小。
- 错误应分类并返回稳定、可操作的信息。
- 不要吞掉异常；确需降级时记录安全原因并在结果中说明。
- 不要返回原始堆栈或敏感路径细节给 MCP Client。
- 处理大型项目时使用流式读取与预算控制。
- 只在有证据的情况下优化性能，不做无依据的复杂缓存。
- 分析器不得直接调用 `Files` / `Path`，必须通过 Workspace 端口。

## 9. 测试要求

每个功能必须有测试：

1. 单元测试：正常路径、边界值、异常输入；
2. 反例测试：确保不应命中的情况不会误报；
3. 契约测试：响应通过 JSON Schema 校验，status/errors/warnings 一致；
4. 集成测试：Spring Context、Tool 注册、参数校验、STDIO 端到端；
5. Fixture 测试：最小可复现的真实项目结构；
6. 安全测试：见 `docs/SECURITY.md` 第 11 节；
7. Benchmark：适用时记录项目规模、耗时、内存与跳过项。

具体要求：

- 不得删除或弱化既有测试来让构建通过。
- 不得只断言结果非空，应验证关键字段、规则码、位置与证据。
- 对规则至少提供命中样例和不命中样例。
- 修复 Bug 时优先增加能复现该 Bug 的回归测试。
- 无法运行某项测试时明确说明原因，不得声称测试通过。
- 修改后运行与改动相关的测试；条件允许时运行完整测试套件。

## 10. 文档要求

功能、参数、行为或限制变化时，同步更新相关文档。

必须保持一致性：

- Tool 行为或入参变化 → `PRD.md` + `ARCHITECTURE.md` + `result-contract.md`；
- 安全行为变化 → `SECURITY.md` + `PRD.md`；
- 结果字段变化 → `result-contract.md` + `ARCHITECTURE.md` + 本文件第 7 节；
- 修改结果契约的破坏性变更必须递增 `schemaVersion` 主版本。

文档必须：

- 说明支持范围和不支持范围；
- 提供可复现的命令或配置；
- 不包含真实密钥和个人机器路径；
- 不声称尚未实现的能力已经支持；
- 不把计划中的功能写成当前能力；
- 不把 V0.2/V0.3 的能力写进 V0.1 描述。

## 11. Git 与改动范围

- 不擅自执行破坏性 Git 操作。
- 不重写、删除或覆盖用户已有提交。
- 不擅自提交、推送或发布，除非任务明确要求。
- 保持改动聚焦，不混入无关重构。
- 不删除用户未要求处理的文件。
- 如果发现工作区存在用户未提交改动，保护这些改动，不覆盖、不清理。

## 12. 推荐工作流程

1. **理解**：读取相关文档、代码、测试和 Git 工作区状态。
2. **计划**：列出实现步骤、影响文件、潜在风险和验收方式。
3. **实现**：只修改当前任务所需内容。
4. **验证**：运行相关测试、构建和必要的手工检查。
5. **审查**：检查安全、边界、误报、异常处理和向后兼容。
6. **汇报**：说明改动、验证结果、未解决问题。

小型、边界明确的任务可以简化计划，但不能跳过验证和汇报。

## 13. 完成任务后的汇报格式

每次完成任务后，简要报告：

- **实现内容**：完成了什么；
- **修改文件**：列出关键文件；
- **设计说明**：关键实现选择及原因；
- **测试结果**：执行的命令、通过/失败数量；
- **安全检查**：是否涉及路径、敏感信息或权限；
- **已知限制**：未覆盖场景、误报/漏报风险；
- **待确认事项**：需要项目负责人决策的内容。

不要只回复"已完成"或"测试通过"，必须提供可核验信息。

## 14. 任务冲突处理

当任务指令与 PRD、ARCHITECTURE、SECURITY、result-contract 或本文件冲突时：

1. 指出冲突的具体条款；
2. 解释继续执行的影响；
3. 给出最小变更方案；
4. 等待项目负责人确认。

不得自行选择更宽松的安全策略或更大范围的架构变更。
