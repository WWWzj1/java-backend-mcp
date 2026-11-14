# ARCHITECTURE --- Java Backend MCP Server

> 项目：`java-backend-mcp`
> 文档版本：Draft 0.2
> 目标：定义可演进、可测试、与 MCP 协议适配层解耦的 Java 后端工程分析架构
> 契约来源：结果结构以 `docs/result-contract.md` 为准；安全要求以
> `docs/SECURITY.md` 为准。本文只描述实现结构与依赖方向。

## 1. 架构目标

1. 让 MCP Client 能以稳定 Tool 接口调用 Java 工程分析能力。
2. 把文件访问、解析、规则分析和结果构建分层，核心分析不依赖 MCP 类型。
3. 每项结论都尽可能携带可复核证据，且证据支持多跳路径。
4. V0.1 只交付可验证的项目画像与依赖/配置/Mapper 诊断，不承诺调用链。
5. 安全边界落实在 Workspace 端口与输出边界，而不是散落在各分析器里。
6. 分析核心可脱离 MCP、脱离真实文件系统做单元测试。

## 2. 总体架构

```text
+------------------------------------------------------+
|                 MCP Clients                          |
|             Codex / Claude Code / ...                |
+---------------------------+--------------------------+
                            |  MCP STDIO
+---------------------------v--------------------------+
|                 MCP Adapter Layer                    |
|  Tool 注册 | 入参 schema | 结果序列化 | 协议错误映射   |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                Application Layer                     |
|  用例编排 | 授权校验 | 预算与超时 | 结果聚合 | 错误映射 |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                  Analysis Engine                     |
|  领域模型 | 分析器 | 规则 | 证据构建 | 端口定义         |
+---------------------------^--------------------------+
                            |  端口实现（依赖倒置）
+---------------------------+--------------------------+
|                Infrastructure Layer                   |
|  Workspace | 文件读取 | XML/Java/SQL/Maven 解析适配    |
|  脱敏 | 配置 | 日志                                     |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                Authorized Workspace                  |
+------------------------------------------------------+
```

**依赖方向（硬约束）**

```text
Adapter → Application → Analysis ← Infrastructure
```

- 外层可以依赖内层，内层不得依赖外层。
- `analysis` 不得 import `mcp`、Spring AI MCP 类型或 `infrastructure`。
- `application` 不得 import `infrastructure`；只依赖 `analysis` 的端口与用例。
- `infrastructure` 实现 `analysis` 中定义的端口，可以依赖 `analysis` 的模型。
- 该规则用 ArchUnit 或等价的包依赖测试固化。

## 3. 技术选型

### 3.1 已确定

- Java + Spring Boot + Spring AI MCP。
- V0.1 仅 STDIO Transport。
- JUnit 5 + GitHub Actions。
- 具体版本在实施前核验兼容矩阵，本文不锁定版本号。

### 3.2 解析与建模

| 领域 | 选型 | V0.1 使用边界 |
| --- | --- | --- |
| Java 源码 | JavaParser | 仅声明级 + 名称级；不引入 symbol solver，不做跨模块类型解析 |
| Maven | Maven Model + Maven Resolver（进程内 API） | 只读、离线优先；**禁止使用 maven-invoker，禁止执行 mvn/插件** |
| Spring | 自建静态模型 + 配置解析 | 只做配置键、组件扫描范围、注解声明的 Bean 候选 |
| YAML | SnakeYAML（安全 LoaderOptions） | 禁止默认构造器；限制别名与文档大小 |
| Properties | Java `Properties` / 自建解析 | 保留行号与键，值按敏感规则掩码 |
| MyBatis | 安全 XML 解析器 | 不联网加载 DTD；不做运行时求值 |
| SQL | JSQLParser | 解析失败降级为 `UNSUPPORTED_SYNTAX`，不猜测 |
| 测试 | JUnit 5 + 契约测试 + 安全测试 | 见第 14 节 |

### 3.3 明确排除

- 不执行目标项目的构建、插件、脚本、测试。
- 不加载目标项目的类，不使用反射读取目标项目运行时状态。
- 不引入规则注册中心、SPI、插件加载器（V0.1）。
- 不引入独立数据库、消息队列或服务注册中心。

## 4. 分层职责

### 4.1 MCP Adapter

职责：
- 注册 4 个 Tool，定义入参 schema 与描述。
- 只做 schema 级校验（类型、必填、枚举、数值范围）。
- 调用 Application 用例，把返回的统一结果序列化为 MCP 响应。
- 把协议级异常映射为 MCP 错误。

禁止：
- 遍历文件、解析 POM/XML/Java。
- 实现任何规则。
- 自行拼装 JSON 结构（必须使用统一契约）。
- 直接访问文件系统。

### 4.2 Application

职责：
- 校验 `workspaceId` 是否在启动期白名单内。
- 解析 `scope` 与 `budget`，构造本次分析的预算与超时。
- 编排一个完整用例（例如"分析依赖"），调用 Analysis 的用例服务。
- 聚合结果、判定 `status`（OK/PARTIAL/FAILED）。
- 把内部异常映射为 `AnalysisError`。

禁止：
- 解析 AST、XML、POM。
- 直接 import `infrastructure`。
- 承载具体规则逻辑。

### 4.3 Analysis Engine

职责：
- 定义领域模型：`ProjectModel`、`ModuleModel`、`DependencyTree`、
  `SpringConfigModel`、`MapperModel`、`SqlStatementModel`、`Finding`、
  `Evidence`、`SymbolId`。
- 定义端口（见 4.5）。
- 实现分析器与规则：Maven、Spring、MyBatis、SQL。
- 构建证据并标注 `certainty` / `unresolved`。
- 维护覆盖范围统计（扫描/跳过/截断）。

禁止：
- import MCP 类型、Spring AI 类型、Spring Web 类型。
- 直接调用 `Files` / `Path` / 网络。
- 决定 HTTP/协议层错误。

### 4.4 Infrastructure

职责：
- 实现 Workspace 端口：路径校验、文件枚举、受限读取。
- 实现解析端口：JavaParser、Maven Model/Resolver、XML、SQL、YAML。
- 实现脱敏器、配置加载、日志。
- 提供时钟与预算检查。

禁止：
- 承载业务规则。
- 直接返回 MCP 结构。
- 向 stdout 输出任何内容。

### 4.5 端口清单（V0.1）

端口定义在 `analysis.port` 包，实现位于 `infrastructure`。

| 端口 | 职责 | 关键约束 |
| --- | --- | --- |
| `WorkspacePort` | 根路径、枚举、读取文本/字节 | 只读；带预算与 deadline；拒绝符号链接 |
| `SourceReader` | 按字符集读取 Java/文本文件 | 有 `maxBytes` 与 charset；返回是否发生编码回退 |
| `MavenModelPort` | 构建有效 POM 模型 | 不执行构建；离线优先 |
| `MavenResolutionPort` | 解析依赖树与仲裁结果 | 离线优先；远程需显式授权 |
| `XmlParsePort` | 安全解析 XML | 强制禁用 DTD 与外部实体 |
| `SqlParsePort` | 解析 SQL 结构 | 失败降级，不猜测 |
| `Redactor` | 文本与结构脱敏 | 出口强制调用 |
| `ClockPort` | 计时与 deadline 判定 | 便于测试注入 |

端口只暴露分析所需的最小能力，不暴露通用文件系统 API。

## 5. 包结构（V0.1）

遵循"按需创建，不建空包"。

```text
src/main/java/.../
├── JavaBackendMcpApplication.java
├── mcp/
│   ├── ProjectTools.java
│   ├── DependencyTools.java
│   ├── SpringTools.java
│   └── MyBatisTools.java
├── application/
│   ├── InspectProjectUseCase.java
│   ├── AnalyzeDependenciesUseCase.java
│   ├── InspectSpringUseCase.java
│   ├── InspectMyBatisUseCase.java
│   └── WorkspaceRegistry.java
├── analysis/
│   ├── model/        # 领域模型
│   ├── port/         # 端口接口
│   ├── project/      # 项目画像与模块图
│   ├── dependency/   # Maven 依赖分析
│   ├── spring/       # 配置与组件候选
│   ├── mybatis/      # Mapper 与 SQL
│   ├── rule/         # 规则实现（普通类，无注册中心）
│   └── evidence/     # 证据与 Finding 构建
├── infrastructure/
│   ├── workspace/    # WorkspacePort 实现
│   ├── maven/        # Maven Model/Resolver 适配
│   ├── parser/       # Java/XML/SQL/YAML 适配
│   ├── security/     # 路径校验、敏感过滤、脱敏
│   └── config/       # 启动配置
└── contract/         # 对外结果 DTO（与 result-contract 一一对应）
```

说明：
- `contract` 是对外稳定结构，字段语义以 `result-contract.md` 为准。
- V0.1 允许内部模型直接复用 `contract` 类型，避免为映射而映射；只有当内部
  模型需要独立演进时，才引入 assembler，并保证对外结构不变。
- 不设 `observability`、`metrics`、`plugin` 包。
- 规则用 `Rule` 接口 + 显式列表装配，不实现注册中心或 SPI。

## 6. 核心数据流

以 `analyze_dependencies` 为例：

1. Adapter 校验 schema，调用 `AnalyzeDependenciesUseCase`。
2. Application 校验 `workspaceId` 是否注册，解析 scope 与 budget。
3. Application 创建 `Deadline` 与预算上下文。
4. Analysis 通过 `MavenModelPort` 读取有效 POM（离线优先）。
5. Analysis 通过 `MavenResolutionPort` 解析依赖树与仲裁结果。
6. 规则层识别版本冲突、重复依赖、未解析依赖。
7. 证据构建器生成 `DEPENDENCY_PATH` 证据（多跳）。
8. Application 汇总 `status`（含 PARTIAL 判定）与 metadata。
9. Adapter 序列化，输出前经过 `Redactor`。

其他用例共用同一骨架：授权 → 预算 → 读取 → 建模 → 规则 → 证据 → 汇总。

## 7. 结果模型

字段定义、枚举值与示例全部以 `docs/result-contract.md` 为准，本文不重复。

架构层面的约束：

1. 事实型产物进 `data`，`findings` 只放需要被关注的问题。
2. 每个 Finding 必须带 `certainty` 与 `unresolved`。
3. 多跳证据通过 `Evidence.hops[]` 表达，不允许把路径拼成字符串。
4. `status` 由 Application 统一判定，分析器不得自行决定。
5. 任何列表输出都必须有上限并记录截断。

## 8. 项目理解模型

### 8.1 ProjectModel（V0.1）

- 构建系统（MAVEN）
- 模块列表：路径、`groupId`、`artifactId`、`version`、`packaging`
- `javaVersion`、`springBootVersion`（能可靠识别时才有值）
- 模块依赖图（声明级）
- 启动类候选（`@SpringBootApplication`）
- 配置文件清单（不含值）
- 关键技术依赖
- 无法识别项 `unknown[]`

### 8.2 模块依赖图

- 节点为 Maven module（`g:a` 或相对路径）。
- 边为 POM 中声明的模块依赖。
- 只表达"声明关系"，不表达运行时类路径顺序。
- 缺失模块、坐标重复、无法解析的父 POM 记入 `warnings`。

### 8.3 后续版本

调用图、接口入口调用链、变更影响集合属于 V0.3，V0.1 不实现，
也不在模型中预留空字段。模型演进通过新增可选字段完成。

## 9. 工程诊断

### 9.1 Maven 依赖

解析模式：

| 模式 | 行为 |
| --- | --- |
| `OFFLINE`（默认） | 只读本地仓库与项目内 `relativePath` 父 POM；缺失记 warning |
| `REMOTE_AUTHORIZED` | 仅在启动配置显式开启且仓库在白名单时生效 |

规则要求：

- 必须区分声明版本、`dependencyManagement` 版本、传递请求版本、最终选定版本。
- 不得因为出现多个请求版本就断言运行时存在多个版本。
- 未解析依赖不得猜测版本；输出 `certainty=PARTIAL` 或 warning。
- 不执行 `mvn`，不调用 `maven-invoker`，不触发目标项目插件。

### 9.2 Spring（V0.1 边界）

V0.1 做：

- 识别 `application*.yml/yaml/properties` 与 profile 后缀文件。
- 提取配置键；敏感键的值一律 `<redacted>`。
- 识别 `@SpringBootApplication`、`@ComponentScan` 的扫描包。
- 识别带 `@Component/@Service/@Repository/@Controller/@RestController/@Configuration`
  的候选类（声明级）。
- 识别构造器注入的声明类型（名称级）。
- 一组经过测试的配置风险规则。

V0.1 **不做**（必须写进 Tool 描述）：

- 不构建完整 Bean 依赖图。
- 不求值 `@Conditional*`、profile、`@Import`、自动配置。
- 不解析 `FactoryBean`、代理、`BeanPostProcessor` 产生的对象。
- 不解析占位符的真实值；`${...}` 标记为 `PLACEHOLDER`。
- 不展开 Lombok / MapStruct 生成的成员。

### 9.3 MyBatis / SQL

- 解析 Mapper XML：namespace、statement id、SQL 类型、SQL 文本。
- 尽可能按 namespace + 方法名匹配 Mapper 接口（名称级，不放宽为模糊匹配）。
- 动态标签（`<if>`、`<foreach>`、`<choose>`）生成骨架节点，标记
  `DYNAMIC_SQL`，`certainty=PARTIAL`。
- `${}` 拼接直接产出 SQL 注入风险规则，不尝试还原。
- SQL 规则必须写明适用范围与误报边界；解析失败记 `UNSUPPORTED_SYNTAX`。

## 10. Workspace 与安全

安全要求全部定义在 `docs/SECURITY.md`。架构侧只固定接口形态：

```java
public interface WorkspacePort {
    Path root();
    List<WorkspaceFile> list(ScanOptions options, Budget budget);
    Optional<TextFile> readText(RelativePath path, ReadLimits limits);
    Optional<byte[]> readBytes(RelativePath path, long maxBytes);
}
```

约束：

- 只接受 `RelativePath` 值对象，构造时即校验（非绝对、无 `..`）。
- 所有方法都接受预算或限制参数，没有"无限读"的重载。
- 返回 `TextFile` 时携带实际字符集与是否回退。
- 不提供写方法，不提供删除/重命名。
- 符号链接处理与 TOCTOU 边界按 SECURITY.md 第 4 节执行。

## 11. Tool 设计（V0.1）

四个 Tool 按用户任务划分，公共入参见 result-contract 第 8 节。

### 11.1 `inspect_project`

- 用途：了解项目结构、模块、技术栈、入口、配置文件。
- 专属入参：`options.includeConfigFiles`（默认 true）。
- `data`：项目画像 + 模块图 + 启动类 + 配置清单 + 技术栈。
- `findings`：通常为空；结构性问题走 `warnings`。

### 11.2 `analyze_dependencies`

- 用途：查看依赖树、仲裁结果、冲突与传递路径。
- 专属入参：`module`（可选）、`options.includeTransitive`（默认 true）、
  `options.maxDepth`（默认 10）、`options.resolutionMode`
  （`OFFLINE` / `REMOTE_AUTHORIZED`）。
- `data`：依赖树与选定版本。
- `findings`：`DEP-*` 冲突、重复、未解析。

### 11.3 `inspect_spring`

- 用途：配置键、组件扫描范围、Bean 候选与配置风险。
- 专属入参：`options.includeBeanCandidates`（默认 true）。
- `data`：配置文件、配置键（值已掩码）、扫描范围、Bean 候选、`limitations[]`。
- `findings`：`SPR-*`。
- Tool 描述中必须写明 9.2 的"不做"清单。

### 11.4 `inspect_mybatis`

- 用途：Mapper/XML 对应关系、SQL 提取与规则检查。
- 专属入参：`options.includeSqlText`（默认 true）、
  `options.maxSqlLength`（默认 100 KiB）。
- `data`：mapper、statement、SQL 文本（已脱敏）、动态标记。
- `findings`：`MAP-*`、`SQL-*`。

通用要求：Tool 描述必须写明能力边界、可能耗时与不支持项；
不允许把 V0.2/V0.3 能力写进描述。

## 12. 错误处理

| 层次 | 触发条件 | 返回方式 |
| --- | --- | --- |
| 协议错误 | JSON-RPC 非法、未知方法 | MCP 框架协议错误 |
| 调用错误 | Tool 未知、参数不符合 schema | Tool 错误（`isError=true`） |
| 业务错误 | 工作区未授权、项目不支持、超时、内部异常 | 信封 `status=FAILED` + `errors[]` |
| 部分成功 | 有跳过/截断/未解析 | 信封 `status=PARTIAL` + `warnings[]` |

- 业务错误码与 `AnalysisError` 字段定义见 result-contract 第 5.6/5.7 节。
- 堆栈只进受控日志，不返回 Client。
- 参数通过 schema 校验后，一切失败都走信封，保证 `code` 稳定。

## 13. 运行形态与 STDIO

硬约束：

1. **stdout 只用于 MCP JSON-RPC 通信**。任何非协议内容写入 stdout 都会
   破坏连接。
2. 所有日志输出到 **stderr**：Logback `ConsoleAppender` 的 target 设为
   `System.err`。
3. 关闭 Spring Boot banner：`spring.main.banner-mode=off`。
4. 禁止 `System.out.print*`；代码评审与静态检查都要覆盖。
5. 第三方库若默认向 stdout 打印，必须显式重定向或禁用。

启动方式（示例，参数细节待定）：

```text
java -jar java-backend-mcp.jar \
  --workspace=demo:/path/to/project \
  --spring.profiles.active=stdio
```

集成测试要求：

- 启动真实进程，发送 `initialize`，断言 stdout 每行均为合法 JSON-RPC。
- 断言 stderr 至少包含启动日志。
- 断言分析请求在前述条件下可完成。

## 14. 测试与 Benchmark

### 14.1 单元测试

- 领域模型与规则（正例、反例、边界）。
- SymbolId 与 SourceLocation 构造与校验。
- 证据构建（单点与多跳）。
- 脱敏器与敏感键判定。

### 14.2 契约测试

- 每个 Tool 的响应通过 JSON Schema 校验。
- `status`、`errors`、`warnings`、`metadata.truncations` 的一致性规则。
- 兼容性：新增字段不影响旧客户端解析。

### 14.3 安全测试

按 `SECURITY.md` 第 11 节逐项实现，必须使用真实文件系统行为，
不得全部用 mock 替代。

### 14.4 集成测试

- Spring Context + Tool 注册 + 参数校验。
- STDIO 端到端（含 stdout 纯净性）。
- 多模块 fixture、依赖冲突 fixture、MyBatis fixture。

### 14.5 Benchmark

- 记录 fixture 规模、耗时、内存、跳过项。
- 输出 TP/FP/FN 与证据位置正确率（适用时）。
- 不承诺固定阈值，先建立基线。

## 15. 演进原则

- 模块化单体，不提前拆服务。
- 契约先行：先改 result-contract，再改实现。
- 新能力必须有真实场景与 fixture，否则不进入路线图。
- 引入缓存前先证明必要性，并定义失效策略与内存上限。
- 架构级变更通过 ADR 记录。

## 16. 待技术验证项

- Spring Boot 与 Spring AI MCP 的兼容矩阵。
- Spring AI MCP 的 STDIO 启动与工具注册方式。
- Maven Resolver 离线模式对父 POM、BOM、profile 的实际行为。
- JavaParser 对目标 Java 语言级别的支持范围。
- JSQLParser 对 MySQL 方言与 MyBatis 动态 SQL 的边界。
- 大项目下 20k 文件 / 200 MiB 预算的实际耗时与内存占用。
