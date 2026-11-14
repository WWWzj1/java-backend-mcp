# PRD --- Java Backend MCP Server

> 项目名称：`java-backend-mcp`
> Tagline：**Engineering Intelligence for Java Developers**
> 当前文档版本：Draft 0.2
> 状态：架构审查后的修订基线
> 配套文档：`ARCHITECTURE.md`、`SECURITY.md`、`result-contract.md`

## 1. 产品概述

### 1.1 一句话定位

面向 Java 后端开发者的 MCP Server，为 Codex、Claude Code 等 AI Coding
Agent 提供可解释、可验证、有证据支撑的 Java 工程分析能力。

### 1.2 产品愿景

让 AI Coding Agent 不只是读取 Java 项目的文件或泛泛回答代码问题，而是
能够逐步理解一个真实 Java 后端工程的：

- 项目结构与技术栈；
- Maven 模块及声明依赖关系；
- Spring Boot 配置与组件扫描范围；
- Controller、Service、Mapper 等代码结构关系；
- MyBatis Mapper 与 SQL；
- 代码变更可能影响的模块、接口和业务路径（后续版本）。

### 1.3 产品不做什么

- 不重新实现通用 Coding Agent，不与 Codex/Claude Code 竞争代码生成能力。
- 不在 MCP Server 内置大语言模型，不负责自主规划复杂任务。
- 不自行实现 MCP JSON-RPC 协议，使用 Spring AI MCP。
- 不把静态分析推断包装成确定的运行时事实。
- V0.1 不修改目标项目文件，不执行任意 SQL，不执行 DDL/DML。
- V0.1 不执行目标项目的 Maven 构建、插件或脚本。
- V0.1 不提供完整 Spring Bean 依赖图。
- V0.1 不提供方法调用链与变更影响分析。
- 不以 MCP Tool 数量作为产品质量指标，不为凑数量拆分用户任务。

## 2. 目标与优先级

用户明确的目标优先级：

1. **开源影响力**：解决真实开发问题，具备清晰差异化，让开发者愿意使用、
   反馈和贡献。
2. **学习成长**：通过产品设计、实现、测试和发布掌握 MCP 与 Java 工程分析。
3. **简历竞争力**：体现系统设计、静态分析与工程质量，但不以包装牺牲产品价值。

开源发布目标：首发达到可供真实使用的程度，建立社区协作基础，长期演进为
可扩展的 Java 后端工程分析工具。

## 3. 目标用户与核心场景

### 3.1 核心用户

- 正在开发、维护 Java 后端项目的开发者；
- 刚加入团队、需要快速熟悉成熟项目的成员；
- 使用 Coding Agent、希望 Agent 更准确理解 Java 工程的开发者。

基础学习者不是首要目标用户。

### 3.2 场景一：陌生项目快速理解

- 项目由哪些模块组成，采用什么技术栈？
- 入口、配置文件和关键组件在哪里？
- Maven 模块之间如何依赖？

V0.1 交付项目画像、模块图、启动类与配置清单。调用链与影响范围属于后续版本。

### 3.3 场景二：日常工程诊断

- Maven 依赖冲突、重复依赖和传递依赖来源；
- Spring Boot 配置风险与组件扫描范围；
- MyBatis Mapper 接口与 XML 映射问题；
- SQL 结构层面的基础风险。

### 3.4 典型使用方式

用户在 MCP Client 中提出自然语言任务，例如：

> 帮我了解这个项目的整体结构，找出主要模块和技术栈，并说明模块之间的依赖关系。

或：

> 检查这个项目的 Maven 依赖冲突，给出冲突版本、传递依赖路径和对应 pom.xml 证据。

MCP Server 返回结构化结果与证据；由外部 Agent 负责进一步推理与组织说明。

## 4. 差异化与设计原则

### 4.1 三项核心竞争力

**A. 分析深度**
从文件枚举走向项目结构、模块依赖、配置关系与 Mapper/SQL 关系。

**B. 证据可信**
每条重要结论附带可复核证据：文件路径与行号、依赖路径、配置键与来源、
Mapper/XML 对应关系、SQL 片段与解析位置、规则依据。

**C. Java 后端专业性**
围绕 Spring Boot、Maven、MyBatis/MySQL，后续扩展 Redis、RabbitMQ、
事务、并发与日志异常。

### 4.2 统一原则

1. 确定性任务交给 Java 分析引擎；LLM 推理由 MCP Client 完成。
2. 结论必须区分事实、规则命中和风险推断。
3. 能给证据就给证据；不能定位时明确说明，不伪造文件、行号或调用关系。
4. 静态分析结果必须说明覆盖范围、确定性与局限。
5. 分析核心与 MCP 协议适配层解耦。
6. 先只读、安全、可验证，再考虑更高权限能力。
7. 事实型产物与问题型发现分离，不把项目画像塞进 findings。

## 5. 版本范围

工具数量不预先固定，但 V0.1 明确为 4 个任务型 Tool。

### 5.1 V0.1：可用的首发版本

#### 工具一：`inspect_project`

- 识别 Maven 单模块/多模块结构；
- 输出模块坐标、打包方式、Java 与 Spring Boot 版本（可识别时）；
- 输出模块声明依赖图；
- 定位启动类候选与配置文件清单；
- 识别关键技术依赖。

#### 工具二：`analyze_dependencies`

- 解析依赖树与版本仲裁结果；
- 输出直接依赖、传递依赖与最终选定版本；
- 识别版本冲突、重复依赖与未解析依赖；
- 给出可复核的依赖路径证据。

约束：默认离线解析，只使用本地仓库与项目内父 POM；远程解析必须显式授权。

#### 工具三：`inspect_spring`

- 识别 `application*.yml/yaml/properties` 与 profile 文件；
- 提取配置键，敏感值一律掩码；
- 识别组件扫描范围；
- 识别注解声明的 Bean 候选类与构造器注入的声明类型（名称级）；
- 输出一组经过测试的配置风险规则。

边界（必须在 Tool 描述与文档中声明）：

- 不构建完整 Bean 依赖图；
- 不求值条件装配、profile、自动配置；
- 不解析 `FactoryBean`、代理、占位符真实值；
- 不展开 Lombok/MapStruct 生成成员。

#### 工具四：`inspect_mybatis`

- 识别 Mapper XML 与 Mapper 接口的对应关系（名称级）；
- 提取 statement id、SQL 类型与 SQL 文本；
- 标记动态 SQL 片段与不确定结构；
- 输出一组经过测试的 SQL 结构风险规则。

#### V0.1 基础产品能力

- Spring AI MCP Server，STDIO Transport；
- 面向启动期授权 Workspace 的只读访问；
- 统一结果契约（status/data/schemaVersion/toolVersion）；
- 输入校验、协议错误与业务错误分离、敏感信息脱敏；
- 单元测试、契约测试、安全测试、集成测试、真实样例与性能基准；
- GitHub Actions CI；
- 安装、配置、Codex/Claude Code 接入与安全说明文档。

#### V0.1 明确不支持

- 方法调用链、变更影响分析；
- 完整 Spring Bean 依赖图；
- Java 与服务端代码执行；
- 数据库连接；
- 远程 Maven 解析（除非显式开启）；
- Windows 平台的安全保证（仅保证不崩溃）。

### 5.2 V0.2：关系分析增强

候选范围：

- 更完整的 Maven 模块与 Spring Bean 依赖关系；
- 局部 `Controller → Service → Mapper` 调用链；
- 更深入的 Spring 自动配置分析；
- Redis、RabbitMQ 工程诊断；
- 事务与并发风险分析；
- 日志与异常分析基础能力；
- JavaParser symbol solver 与跨模块类型解析。

### 5.3 V0.3：调用链与影响分析

候选范围：

- 静态方法调用关系；
- 从接口入口追踪关键业务调用链；
- 基于类、方法、模块关系的变更影响分析；
- 标注动态分派、反射、代理、异步与跨服务调用的盲区。

### 5.4 V1.0 及长期方向

- 稳定的规则与分析器扩展机制；
- MCP Resources、Prompts（经实际使用验证后引入）；
- 可选只读数据库分析（受限 EXPLAIN）；
- 更丰富的真实项目 benchmark；
- 团队级共享或服务化能力；
- 插件生态与社区贡献机制。

以上为候选方向，不代表全部承诺。

## 6. 输出契约

V0.1 统一结果信封包含：

- `schemaVersion`、`tool`、`toolVersion`；
- `status`：`OK` / `PARTIAL` / `FAILED`；
- `summary`；
- `data`：事实型结构化产物；
- `findings`：问题、规则命中与风险推断；
- `metadata`：范围、耗时、扫描/跳过/截断统计、预算、解析模式；
- `warnings`：结构化警告（含 code）；
- `errors`：结构化错误（含 code、category、recoverable）。

关键原则：

1. 事实型产物进 `data`，不塞进 `findings`。
2. 不确定性通过 `certainty` 与 `unresolved` 表达，不只写进 warning 文本。
3. `severity` 取 `INFO/LOW/MEDIUM/HIGH/CRITICAL`，只表示影响级别。
4. 证据支持多跳（依赖路径、后续的调用路径）。
5. 所有列表输出有上限并标记截断。

字段级定义与示例见 `docs/result-contract.md`。

## 7. 安全与权限要求

完整要求见 `docs/SECURITY.md`，产品层面必须满足：

- Workspace 在**启动时**以 `id + 根路径` 白名单注册；Tool 只接受 `workspaceId`，
  不接受路径。
- 所有路径规范化后校验根目录包含性；默认不跟随符号链接；只处理普通文件。
- 单次分析有文件数、单文件大小、总字节数、超时与返回规模上限。
- 已知凭据文件一律跳过；配置敏感值一律掩码；返回值与日志统一脱敏。
- 默认不联网；远程 Maven 解析必须显式开启并配置仓库白名单。
- 不执行目标项目的构建、插件或脚本。
- XML 解析禁用 DTD 与外部实体；MyBatis DTD 不联网加载。
- 返回值中的源码片段视为不可信数据，限长并去除控制字符。

平台支持：V0.1 官方支持 **macOS 与 Linux**；Windows 不提供安全保证。

## 8. 非功能需求与质量指标

V0.1 必须具备：

- 可重复运行的自动化测试；
- 每个规则的命中样例、不命中样例与边界样例；
- 结果契约测试（JSON Schema 校验）；
- 安全测试（授权、路径穿越、符号链接、FIFO、资源上限、脱敏、XXE、
  stdout 纯净性）；
- MCP 集成测试，覆盖 Tool 注册、参数校验、调用与序列化；
- 至少一个多模块 Spring Boot 真实结构样例；
- 准确性验证：命中、漏报、误报与证据位置；
- 性能基准：记录规模、耗时、内存与跳过项；
- CI 自动构建与测试；
- 文档中的能力边界与已知限制。

不在缺少数据时承诺固定准确率、QPS 或扫描耗时。

## 9. 发布与社区要求

- 可安装、可配置、可在常见 MCP Client 中接入；
- 提供最小可运行示例与真实使用案例；
- 提供 README、快速开始、配置说明、安全说明、常见问题；
- 提供 CONTRIBUTING、Issue/PR 模板与路线图；
- 对外说明支持的 JDK、Spring Boot、Spring AI MCP 与构建工具版本；
- 使用明确的开源许可证（待项目负责人确认）；
- 通过 GitHub Actions 保证基本质量门禁。

## 10. V0.1 验收标准

V0.1 是否发布不以 Tool 数量或代码行数判断。必须同时满足：

1. 用户按文档启动 STDIO MCP Server，并被目标 Client 成功调用。
2. 用户配置 Workspace 白名单后，能获得稳定的项目画像。
3. 能识别 Maven 多模块结构并输出模块声明依赖图。
4. 能输出依赖冲突/仲裁结果与可复核依赖路径；离线缺依赖时正确降级为
   `PARTIAL` 并给出 warning。
5. `inspect_spring` 能定位配置文件、输出经过测试的配置风险，并明确声明
   不含完整 Bean 依赖图。
6. `inspect_mybatis` 能识别一组受支持的 Mapper/XML 关系并提取 SQL。
7. SQL 规则有规则说明、正反例与误报边界。
8. 所有结论遵循结果契约，不伪造位置或确定性；事实产物不进 findings。
9. 路径访问、敏感信息脱敏、只读策略、资源预算、XXE 防护有自动化测试。
10. 未注册的 workspaceId、绝对路径、`..` 与符号链接均被拒绝。
11. 默认配置下无出站网络连接；若 V0.1 提供远程解析开关，未显式开启时不可用。
12. STDIO 模式下 stdout 仅有合法 JSON-RPC，日志全部走 stderr。
13. 单元、契约、安全、集成测试与 CI 均可运行。
14. README、快速开始、安全说明、贡献指南与路线图完整。

## 11. 待确认事项

以下事项在实施或首发前由项目负责人确认：

- 项目许可证；
- Spring Boot 与 Spring AI MCP 的具体稳定兼容版本；
- 最低支持的 JDK 与 Maven 版本；
- V0.1 各工具的精确规则清单（`DEP-*`、`SPR-*`、`MAP-*`、`SQL-*`）；
- Workspace 配置方式与 MCP Client 配置示例；
- 是否在首发提供预编译发行包；
- Benchmark 项目来源与许可证；
- 资源预算默认值是否维持 20k 文件 / 200 MiB / 30 s；
- 远程 Maven 解析是否在 V0.1 就提供开关，或整体推迟到 V0.2。
