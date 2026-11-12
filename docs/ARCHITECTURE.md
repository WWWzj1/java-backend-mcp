# ARCHITECTURE --- Java Backend MCP Server

> 项目：`java-backend-mcp`\
> 文档版本：Draft 0.1\
> 目标：定义可演进、可测试、与 MCP 协议适配层解耦的 Java
> 后端工程分析架构。

## 1. 架构目标

1.  让 MCP Client 能以稳定工具接口调用 Java 工程分析能力。
2.  将文件扫描、解析、规则分析和结果构建放在独立分析层。
3.  每项发现都尽可能携带可复核证据。
4.  支持从 V0.1 项目画像和工程诊断演进到调用链、影响分析。
5.  将安全边界落实在 Workspace 和数据访问基础设施中。
6.  让分析核心可脱离 MCP 层进行单元测试和集成测试。

## 2. 总体架构

``` text
+------------------------------------------------------+
|                 MCP Clients                          |
|             Codex / Claude Code / ...                |
+---------------------------+--------------------------+
                            |
                       MCP STDIO
                            |
+---------------------------v--------------------------+
|                 MCP Adapter Layer                    |
| Spring AI MCP Server | Tool definitions | Schemas    |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                Application Layer                     |
| ProjectInspectionService                              |
| MavenAnalysisService                                  |
| SpringAnalysisService                                 |
| MyBatisAnalysisService                                |
| Use-case orchestration / validation                  |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                  Analysis Engine                     |
| Project Model | Maven Model | Spring Model           |
| Mapper Model  | SQL Model   | Rules                  |
| Evidence Collection | Finding Aggregation            |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                Infrastructure Layer                   |
| Workspace Access | File Readers | Parsers             |
| Maven Resolver   | Path Guard   | Redaction            |
| Metrics / Logging / Configuration                     |
+---------------------------+--------------------------+
                            |
+---------------------------v--------------------------+
|                Authorized Workspace                  |
| pom.xml | Java Sources | application.yml | Mapper XML |
+------------------------------------------------------+
```

依赖方向原则：MCP Adapter → Application → Analysis
Engine；Infrastructure
为分析引擎和应用层提供接口实现。核心分析模型不依赖 Spring AI MCP 类型。

## 3. 技术选型原则

### 3.1 已确定方向

-   Java；
-   Spring Boot；
-   Spring AI MCP；
-   V0.1 使用 STDIO Transport；
-   JUnit 5；
-   GitHub Actions。

### 3.2 候选解析技术

具体依赖版本和兼容性必须在实施前核验，不在本文锁定未经验证的版本。

  ------------------------------------------------------------------------------------
  领域                    候选技术                  用途
  ----------------------- ------------------------- ----------------------------------
  Java 源码               JavaParser                AST、类型/方法声明、基础调用关系

  Maven                   Maven Model、Maven        POM 模型、依赖解析、依赖路径
                          Resolver / Invoker        

  Spring                  自建静态模型 + 配置解析   配置、组件扫描、Bean 候选关系

  YAML/Properties         SnakeYAML（安全配置）及   配置解析
                          Java Properties API       

  MyBatis                 XML Parser / DOM + Java   Mapper XML 与接口映射
                          源码解析                  

  SQL                     JSQLParser                SQL 结构提取和规则分析

  测试                    JUnit 5                   单元与集成测试
  ------------------------------------------------------------------------------------

选型要求： - 优先成熟、维护活跃、许可证兼容的库； -
避免为简单任务引入重量级依赖； - 不执行目标项目 Maven
插件或构建脚本来"分析"项目； - 对解析器不支持的语法给出
unsupported/warning，而不是静默产生错误结论。

## 4. 分层职责

### 4.1 MCP Adapter Layer

职责： - 注册 MCP Tools； - 定义输入参数和描述； - 将 MCP 请求映射为
Application 层用例； - 将统一结果模型序列化为 MCP 返回内容； -
将可预期异常转换为稳定错误结构。

禁止： - 在 Tool 方法里直接遍历大量文件； - 在 Tool 方法里实现
Maven、Spring 或 SQL 规则； - 在 Tool 方法里拼装各自独立的 JSON 结构； -
直接访问任意文件系统路径而绕过 Workspace 安全检查。

### 4.2 Application Layer

职责： - 编排单次分析用例； - 校验输入和用户授权的 Workspace； -
调用所需解析器、模型构建器和规则； - 管理超时、扫描限制和结果聚合； -
返回统一结果模型。

Application 层表达"要完成什么用例"，不承载底层 AST 或 SQL 解析细节。

### 4.3 Analysis Engine

职责： - 将源文件和配置转换为领域模型； - 构建模块、Bean、Mapper、SQL
等关系； - 运行确定性规则； - 生成 Finding 和 Evidence； -
标记分析覆盖范围、未知项和不确定性。

建议领域模型： - `ProjectModel` - `ModuleModel` - `TechnologyProfile` -
`DependencyGraph` - `SpringConfigurationModel` - `BeanDependencyGraph` -
`MapperModel` - `SqlStatementModel` - `Finding` - `Evidence` -
`AnalysisMetadata`

核心模型不得依赖 Spring AI MCP 的 Request/Result 类型。

### 4.4 Infrastructure Layer

职责： - Workspace 受控文件访问； - 文件枚举、编码处理和大小限制； -
路径规范化与越权检测； - Maven 本地仓库和解析器适配； - 敏感信息脱敏； -
日志、指标和性能计时。

所有文件访问必须通过统一的 Workspace
API，不允许分析器自行拼接任意路径读取。

## 5. 建议包结构

以下为建议结构，可根据实际依赖和复杂度调整；不要求一开始创建空包或过度抽象。

``` text
src/main/java/.../
├── JavaBackendMcpApplication.java
├── mcp/
│   ├── ProjectTools.java
│   ├── MavenTools.java
│   ├── SpringTools.java
│   └── MyBatisTools.java
├── application/
│   ├── project/
│   ├── maven/
│   ├── spring/
│   └── mybatis/
├── analysis/
│   ├── project/
│   ├── dependency/
│   ├── spring/
│   ├── mybatis/
│   ├── sql/
│   ├── finding/
│   └── evidence/
├── infrastructure/
│   ├── workspace/
│   ├── filesystem/
│   ├── maven/
│   ├── parser/
│   ├── security/
│   └── observability/
└── shared/
    ├── result/
    ├── error/
    └── validation/
```

测试目录应按单元、集成和样例分层：

``` text
src/test/
├── java/
│   ├── .../unit/
│   └── .../integration/
└── resources/
    └── fixtures/
        ├── simple-spring-boot/
        ├── multi-module-project/
        ├── dependency-conflicts/
        └── mybatis-mappers/
benchmark/
├── fixtures/
├── expected/
└── README.md
```

## 6. 核心数据流

以 `analyze_maven_dependencies` 为例：

1.  MCP Client 发起 Tool 调用，传入 Workspace 标识/路径和分析选项。
2.  MCP Adapter 校验参数并调用 Application 用例。
3.  Workspace Manager 验证根目录授权、规范化路径并创建只读访问上下文。
4.  Maven Analyzer 定位 POM，构建 Maven 模型并解析依赖。
5.  Dependency Analyzer 构建模块依赖和传递依赖路径。
6.  Rule Engine 识别版本冲突、重复依赖等发现。
7.  Evidence Collector 收集 POM 路径、依赖路径、版本仲裁信息等证据。
8.  Finding Aggregator 构建统一结果。
9.  MCP Adapter 序列化结果并返回 Client。

其他分析用例遵循相同流程：输入校验 → 安全 Workspace → 解析/建模 →
规则分析 → 证据收集 → 统一结果。

## 7. 统一结果模型

建议 Java 类型（具体命名可在实现中调整）：

``` java
public record AnalysisResult(
    boolean success,
    String summary,
    List<Finding> findings,
    AnalysisMetadata metadata,
    List<AnalysisWarning> warnings,
    List<AnalysisError> errors
) {}

public record Finding(
    String code,
    Severity severity,
    String title,
    FindingKind kind,
    SourceLocation location,
    List<Evidence> evidence,
    String description,
    String suggestion,
    Double confidence
) {}

public record SourceLocation(
    String path,
    Integer line,
    Integer column,
    String symbol
) {}

public record Evidence(
    String type,
    String source,
    Integer line,
    String excerpt,
    String explanation
) {}
```

注意： - 以上是概念模型，不是要求照抄的最终 API。 - `confidence`
可选；没有合理依据时不填写。 - `line`、`column`
不可用时为空，禁止编造。 - `FindingKind` 至少区分
`FACT`、`RULE_MATCH`、`RISK_INFERENCE`。 - `severity`
表示影响级别，不代表分析置信度。 - 对外 JSON
字段命名、枚举值和向后兼容策略需要在实现前固定。

## 8. 项目理解模型

### 8.1 V0.1 项目画像

`ProjectModel` 至少考虑： - 项目根目录（对外返回时可相对化）； -
构建系统； - 模块列表； - 每个模块的 POM 路径、artifact 坐标和子模块； -
Java/JDK 目标版本（若能可靠识别）； - Spring Boot
版本（若能可靠识别）； - 关键技术依赖； - 启动类候选； -
配置文件清单； - 未识别或冲突信息。

### 8.2 模块依赖图

-   节点代表 Maven module；
-   有向边代表声明的模块依赖；
-   需要区分直接依赖与传递依赖；
-   缺失模块、重复坐标和无法解析的依赖应作为 warning；
-   输出结构化节点和边，图形渲染由 Client 或后续工具完成。

### 8.3 后续关系分析

后续可增加： - Spring Bean 候选依赖图； - Controller/Service/Mapper
层级关系； - 方法调用图； - 接口入口到数据访问层的局部调用链； -
变更影响候选集合。

静态调用分析需考虑接口多态、继承、代理、反射、异步和框架回调。对无法解析的动态关系必须标注
unknown/partial，不得声称完整。

## 9. 工程诊断设计

### 9.1 Maven

输入：Workspace、目标模块/分析选项。\
处理：POM 模型、依赖解析、版本仲裁和依赖路径分析。\
输出：依赖树摘要、冲突/重复发现、版本来源和证据。

需要明确区分： - 声明版本； - dependencyManagement 管理版本； -
传递依赖请求版本； - Maven 最终选定版本。

不能仅因依赖路径出现多个版本就直接断言运行时一定存在多个版本冲突。

### 9.2 Spring Boot

V0.1 候选能力： - 识别 application.yml/yaml/properties 及 profile
配置； - 提取配置键并进行敏感信息脱敏； - 识别启动类和组件扫描范围； -
识别常见注解与 Bean 声明； - 构建可解释的 Bean 候选依赖关系； -
检查部分常见配置风险。

静态扫描无法完整模拟 Spring 条件装配、运行时
Profile、FactoryBean、代理和自定义注册过程。结果必须说明覆盖范围。

### 9.3 MyBatis / SQL

-   解析 Mapper XML；
-   识别 namespace、statement id、SQL 类型和 SQL 文本；
-   尽可能匹配 Mapper 接口方法；
-   解析动态 SQL 时区分静态可确定片段与运行时动态部分；
-   使用 SQL Parser 识别结构，再应用规则；
-   每条 SQL 风险给出规则依据、SQL 位置和适用限制。

"缺少
WHERE""分页方式""潜在全表扫描"等规则必须谨慎定义，不能只用简单字符串匹配就输出高严重度结论。

## 10. Workspace 与安全架构

建议提供统一接口：

``` java
public interface Workspace {
    Path root();
    List<WorkspaceFile> listFiles(ScanOptions options);
    Optional<String> readText(String relativePath);
    Optional<byte[]> readBytes(String relativePath, long maxBytes);
}
```

安全要求： - 根目录在启动或请求时显式授权； - `resolve`
后进行规范化和根目录包含性验证； - 考虑符号链接逃逸； -
不允许绝对路径越权； - 限制扫描深度、文件数量、单文件大小和总字节数； -
排除构建产物、版本控制目录和无关大目录； -
只读打开文件，不提供写入接口； - 对异常和日志中的敏感信息脱敏。

不要把 `Path` 从未经校验的 MCP 参数直接传给 `Files.readString`。

## 11. MCP Tool 设计原则

Tool 应按用户任务设计，而不是按内部方法一一暴露。

每个 Tool： - 名称稳定、描述明确； - 参数有类型、范围和默认值； -
说明分析范围、可能耗时和局限； - 输出统一结构； -
对无效路径、解析失败、不支持项目返回可理解错误； -
不返回无关的大量源码； -
允许用户指定模块或分析范围，避免每次扫描整个仓库。

候选 Tool 仅作能力清单，不锁定最终数量： - `inspect_java_project` -
`inspect_spring_boot` - `inspect_spring_config` -
`analyze_maven_dependencies` - `find_dependency_conflicts` -
`inspect_mybatis_mappers` - `analyze_sql_patterns`

Tool 数量由真实用例、参数复杂度和 Client 使用体验决定。

## 12. 错误处理

区分： - 输入错误：参数缺失、格式错误、选项不支持； -
权限错误：Workspace 未授权、路径越界； - 项目错误：不是受支持项目、POM
无法解析； - 部分分析：部分文件不支持或解析失败； -
系统错误：内部异常、超时、资源限制。

可预期错误应转成稳定错误码和可操作说明。堆栈只进入受控日志，不直接返回
Client。部分分析成功时，应明确标记 `partial` 并列出跳过项。

## 13. 测试与 Benchmark 架构

### 13.1 单元测试

针对： - POM 模型和版本仲裁； - 配置解析； - Mapper/XML 映射； - SQL
规则； - 路径安全； - 脱敏； - Finding/Evidence 序列化。

### 13.2 集成测试

覆盖： - Spring Context 启动； - MCP Tool 注册； - 参数校验； - STDIO
或合适的 MCP Client 测试方式； - 正常、错误和部分成功返回。

### 13.3 Benchmark

每个 fixture 应有： - 可复现输入项目； - 预期项目画像/发现； -
预期证据位置； - 正例与反例； - 已知不支持语法； -
测试说明和来源许可证。

评估维度： - True Positive / False Positive / False
Negative（适用时）； - 证据位置正确率； - 分析覆盖率； -
扫描耗时和资源占用。

## 14. 演进原则

-   先以模块化单体实现，不因"微服务"或"插件化"愿景过早拆服务。
-   先建立稳定的领域模型和分析器接口，再考虑插件机制。
-   不在 V0.1 同时支持 STDIO 和 HTTP。
-   不将 MCP SDK 类型泄漏到分析核心。
-   不因未来需求创建大量空接口、空模块。
-   每次扩展都必须有真实用户场景和测试样例。
-   架构变化通过 ADR 记录，涉及重大边界调整先由项目负责人确认。

## 15. 待技术验证项

-   Spring Boot 与 Spring AI MCP 的稳定兼容矩阵；
-   STDIO 启动、日志输出和打包方式；
-   JavaParser 对目标 Java 语法级别的支持；
-   Maven Resolver 对本地仓库、父 POM、BOM 和 Profile 的处理；
-   JSQLParser 对 MySQL 方言和 MyBatis 动态 SQL 的支持边界；
-   Windows/macOS/Linux 路径与权限行为；
-   大型项目扫描性能和资源限制。
