# RESULT CONTRACT --- Java Backend MCP Server

> 文档版本：1.0（对应产品 V0.1）
> 状态：Draft 0.2，契约冻结候选
> 适用对象：所有 MCP Tool 的入参与返回值
> 效力：本文件是结果契约的唯一来源。PRD 只描述产品层面的输出要求，
> ARCHITECTURE 只描述模型如何产生；字段名、枚举值和状态语义以本文件为准。

## 0. 变更规则

1. 修改本文件必须同步检查 `PRD.md`、`ARCHITECTURE.md`、`AGENTS.md`、`SECURITY.md`。
2. 对外字段只做加法：新增可选字段、新增枚举值属于次版本变更。
3. 删除字段、改名、改变字段语义或必填性属于破坏性变更，必须递增
   `schemaVersion` 主版本，并在 PRD 中记录。
4. 客户端必须忽略未知字段和未知枚举值，不得因新增字段失败。
5. `schemaVersion` 描述信封结构，`toolVersion` 描述实现版本，两者独立。

## 1. 设计原则

1. 事实型产物（项目画像、模块图、依赖树、SQL 列表）放 `data`，不塞进
   `findings`。
2. `findings` 只承载需要被关注的问题、规则命中和风险推断。
3. 不确定性必须是结构化字段（`certainty`、`unresolved`），不能只写进
   `warnings` 文本。
4. 静态分析无法确定的内容标记 `UNKNOWN`，不得推断为确定事实。
5. 所有列表型产物必须有上限，并在 `metadata.truncations` 中标记截断。
6. 返回内容中的源码片段属于不可信数据，必须限长、去控制字符，并按下文
   `excerpt` 规则处理。

## 2. 结果信封

```json
{
  "schemaVersion": "1.0",
  "tool": "inspect_project",
  "toolVersion": "0.1.0",
  "status": "OK",
  "summary": "识别到 Maven 多模块工程，共 3 个模块",
  "data": {},
  "findings": [],
  "metadata": {},
  "warnings": [],
  "errors": []
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | string | 是 | 信封结构版本，形如 `1.0` |
| `tool` | string | 是 | Tool 名称，与注册名一致 |
| `toolVersion` | string | 是 | 实现版本，形如 `0.1.0` |
| `status` | enum | 是 | `OK` / `PARTIAL` / `FAILED` |
| `summary` | string | 是 | 一句话结果摘要，不含敏感信息，不超过 500 字符 |
| `data` | object \| null | 是 | Tool 专属结构化产物；纯诊断型可为 `null` |
| `findings` | array | 是 | `Finding` 列表，无发现时为空数组，不得为 `null` |
| `metadata` | object | 是 | `AnalysisMetadata` |
| `warnings` | array | 是 | `AnalysisWarning` 列表 |
| `errors` | array | 是 | `AnalysisError` 列表；`status=FAILED` 时非空 |

## 3. 状态语义

| 状态 | 含义 | 客户端建议行为 |
| --- | --- | --- |
| `OK` | 在请求的 scope 与预算内完成分析，无影响结论的跳过项 | 直接使用结果 |
| `PARTIAL` | 产出了可用结果，但存在跳过文件、截断列表、未解析依赖、降级解析等 | 可用，但必须阅读 `warnings`、`metadata.skipped`、`metadata.truncations`，不得当作完整结论 |
| `FAILED` | 无法产出可用结果 | 读取 `errors[].code` 决定修正参数、换范围或放弃 |

补充规则：
- `PARTIAL` 时 `data` 可以为非空，`findings` 可以为非空。
- `OK` 不表示"项目没有问题"，只表示分析本身完整。
- 不建议客户端只用 `status` 判断，仍需读取 `warnings` 与 `errors`。

## 4. 公共枚举

### 4.1 Severity

`INFO`、`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`

- 只表示问题影响级别。
- 不表示分析失败，也不表示置信度。
- 分析失败统一由 `status` 与 `errors` 表达，不使用 severity。

### 4.2 FindingKind

`FACT`、`RULE_MATCH`、`RISK_INFERENCE`

- `FACT`：可复核的事实陈述，例如"存在两个模块声明了同一 artifact"。
- `RULE_MATCH`：命中确定性规则，规则本身可能有适用范围。
- `RISK_INFERENCE`：基于静态证据的推断，必须同时给出 `certainty` 与
  `unresolved`。

### 4.3 Certainty

`CERTAIN`、`PARTIAL`、`UNKNOWN`

- `CERTAIN`：证据完整且静态可确定。
- `PARTIAL`：证据存在但缺失部分上下文（如动态 SQL、未解析依赖）。
- `UNKNOWN`：无法判断，只能报告"存在该结构"。

### 4.4 UnresolvedReason

| 值 | 说明 |
| --- | --- |
| `PLACEHOLDER` | 配置占位符未解析，如 `${DB_URL}` |
| `PROFILE_OVERRIDE` | 值可能被 profile 覆盖 |
| `CONDITIONAL_BEAN` | 条件装配无法静态求值 |
| `DYNAMIC_SQL` | MyBatis 动态标签导致结构不确定 |
| `REFLECTION` | 反射调用 |
| `PROXY` | 动态代理或 AOP 代理 |
| `ASYNC` | 异步执行导致调用关系不确定 |
| `MISSING_ARTIFACT` | 依赖或父 POM 未解析 |
| `UNSUPPORTED_SYNTAX` | 解析器不支持该语法 |
| `ENCODING_FALLBACK` | 使用回退字符集解析，内容可能有误 |
| `EXTERNAL_CONFIG` | 配置来自外部（环境变量、配置中心） |

## 5. 公共类型

### 5.1 SymbolId

```json
{
  "module": "com.example:demo-service",
  "fqn": "com.example.demo.order.OrderService",
  "member": "createOrder",
  "descriptor": "(Lcom/example/demo/order/CreateOrderCmd;)Lcom/example/demo/order/Order;"
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `module` | 是 | 模块标识，优先 `groupId:artifactId`；无法解析时为模块相对路径 |
| `fqn` | 是 | 全限定类名或接口名 |
| `member` | 否 | 方法/字段名；类型级符号为 `null` |
| `descriptor` | 否 | JVM 方法描述符；V0.1 不保证可用，无法确定时为 `null` |

V0.1 只保证 `module` + `fqn`，`member` 仅在声明级解析中可用。

### 5.2 SourceLocation

```json
{
  "path": "demo-service/src/main/java/com/example/demo/order/OrderService.java",
  "line": 42,
  "column": 5,
  "symbolId": null
}
```

- `path` 永远是相对 Workspace 根目录的路径，禁止返回绝对路径。
- `line`、`column` 从 1 开始；无法确定时为 `null`，禁止编造。
- `symbolId` 可选，用于把位置与符号关联。

### 5.3 Evidence

```json
{
  "kind": "DEPENDENCY_PATH",
  "summary": "spring-core 5.3.0 经 demo-api 传递引入",
  "certainty": "CERTAIN",
  "hops": [
    { "ref": "com.example:demo-service:1.0.0", "symbolId": null, "location": null, "note": "根模块" },
    { "ref": "com.example:demo-api:1.0.0", "symbolId": null, "location": { "path": "demo-api/pom.xml", "line": 30, "column": null, "symbolId": null }, "note": "声明依赖" },
    { "ref": "org.springframework:spring-core:5.3.0", "symbolId": null, "location": null, "note": "传递依赖" }
  ],
  "excerpt": null
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `kind` | 是 | `FILE` / `DEPENDENCY_PATH` / `CALL_PATH` / `CONFIG_KEY` / `SQL` / `SYMBOL` |
| `summary` | 是 | 人类可读的一句话证据说明 |
| `certainty` | 是 | 该证据本身的可确定性 |
| `hops` | 是 | 有序节点列表，单点证据长度为 1 |
| `excerpt` | 否 | 源码/SQL 摘录，最长 500 字符，去控制字符，按 SECURITY.md 脱敏 |

`hops[]` 元素：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `ref` | 是 | 稳定引用：`g:a:v`、相对路径、SQL statement id 等 |
| `symbolId` | 否 | 指向源码符号时填写 |
| `location` | 否 | 指向文件位置时填写 |
| `note` | 否 | 该跳的简短说明 |

### 5.4 Finding

```json
{
  "code": "DEP-VERSION-CONFLICT",
  "severity": "MEDIUM",
  "title": "同一 artifact 存在多个请求版本",
  "kind": "FACT",
  "certainty": "CERTAIN",
  "location": {
    "path": "demo-service/pom.xml",
    "line": 24,
    "column": null,
    "symbolId": null
  },
  "evidence": [],
  "description": "spring-core 被请求 5.3.0 与 5.3.20 两个版本，Maven 最终选择 5.3.20。",
  "suggestion": "核对版本来源，必要时在 dependencyManagement 中固定版本。",
  "unresolved": [],
  "confidence": null
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `code` | 是 | 稳定规则码，格式 `DOMAIN-NAME`，发布后不复用 |
| `severity` | 是 | 见 4.1 |
| `title` | 是 | 不超过 200 字符 |
| `kind` | 是 | 见 4.2 |
| `certainty` | 是 | 见 4.3 |
| `location` | 否 | 主位置；无法定位时为 `null`，并在 `description` 说明原因 |
| `evidence` | 是 | `Evidence` 列表，至少 1 条；确实无法提供证据时为空并在 `description` 说明 |
| `description` | 是 | 事实或风险说明，不得包含敏感值 |
| `suggestion` | 否 | 建议；只能由规则生成，不得直接复制源码内容 |
| `unresolved` | 是 | `UnresolvedReason` 列表，无则为空数组 |
| `confidence` | 否 | 仅在可量化时填写，禁止伪装成准确率 |

### 5.5 AnalysisWarning

```json
{
  "code": "UNRESOLVED_DEPENDENCY",
  "message": "父 POM com.example:demo-parent:1.0.0 在离线模式下不可用",
  "target": "com.example:demo-parent:1.0.0",
  "scope": "module:demo-service"
}
```

`code` 建议集合：`UNRESOLVED_DEPENDENCY`、`UNSUPPORTED_SYNTAX`、
`SKIPPED_SENSITIVE_FILE`、`SKIPPED_SYMLINK`、`ENCODING_FALLBACK`、
`TRUNCATED_RESULT`、`PARTIAL_SCOPE`、`LIMIT_APPLIED`、`REMOTE_RESOLUTION_ENABLED`。

### 5.6 AnalysisError

```json
{
  "code": "PATH_OUTSIDE_WORKSPACE",
  "category": "PERMISSION",
  "message": "请求路径超出授权 Workspace 范围",
  "target": "scope.include[0]",
  "recoverable": false,
  "details": {}
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `code` | 是 | 稳定错误码，见 5.7 |
| `category` | 是 | `INPUT` / `PERMISSION` / `PROJECT` / `LIMIT` / `INTERNAL` |
| `message` | 是 | 可操作说明，不含敏感信息与绝对路径细节 |
| `target` | 否 | 出错对象：参数名、模块坐标、相对路径或 Tool 名 |
| `recoverable` | 是 | 客户端修正参数后是否可能成功 |
| `details` | 否 | 结构化补充信息，只放非敏感字段 |

### 5.7 错误码

| code | category | 说明 |
| --- | --- | --- |
| `INVALID_ARGUMENT` | INPUT | 参数缺失、类型错误、枚举不支持 |
| `WORKSPACE_NOT_AUTHORIZED` | PERMISSION | `workspaceId` 不在启动期白名单 |
| `PATH_OUTSIDE_WORKSPACE` | PERMISSION | 路径规范化后不在授权根内 |
| `SYMLINK_REJECTED` | PERMISSION | 路径为符号链接且策略禁止跟随 |
| `UNSUPPORTED_FILE_TYPE` | INPUT | 非普通文件或不受支持的文件类型 |
| `BUDGET_EXCEEDED` | LIMIT | 超出文件数/字节数预算 |
| `TIMEOUT` | LIMIT | 超出本次分析时限 |
| `NOT_A_JAVA_PROJECT` | PROJECT | 未找到受支持的构建文件 |
| `POM_PARSE_ERROR` | PROJECT | POM 无法解析 |
| `XML_PARSE_ERROR` | PROJECT | XML 无法安全解析 |
| `UNRESOLVED_DEPENDENCY` | PROJECT | 依赖无法解析（通常降级为 warning） |
| `UNSUPPORTED_SYNTAX` | PROJECT | 语法不受支持（通常降级为 warning） |
| `INTERNAL_ERROR` | INTERNAL | 未预期异常，不得回传堆栈 |

## 6. 错误通道

| 层次 | 判定 | 返回方式 |
| --- | --- | --- |
| MCP 协议错误 | JSON-RPC 不合法、未知方法 | 由 MCP 框架返回协议错误 |
| Tool 调用错误 | Tool 名未知、参数不符合 schema | MCP Tool 错误（`isError=true`），不带结果信封 |
| 业务分析错误 | 参数合法但工作区未授权、项目不支持、超时、内部异常 | 正常 Tool 结果，信封 `status=FAILED` + `errors[]` |
| 分析完成 | 成功或有降级 | 信封 `status=OK` 或 `PARTIAL` |

规则：一旦参数通过 schema 校验，后续一切失败都走信封，不走协议错误，
以保证客户端能拿到稳定 `code`。

## 7. AnalysisMetadata

```json
{
  "workspaceId": "demo",
  "scope": { "modules": [], "include": ["**/*"], "exclude": [] },
  "startedAt": "2026-11-12T06:20:11Z",
  "elapsedMs": 842,
  "scanned": { "files": 128, "bytes": 486213, "modules": 3 },
  "skipped": {
    "count": 12,
    "byReason": { "BINARY": 3, "SENSITIVE": 1, "EXCLUDED_DIR": 8 },
    "samples": [{ "path": "logs/app.log", "reason": "EXCLUDED_DIR" }]
  },
  "truncated": false,
  "truncations": [],
  "limits": {
    "maxFiles": 20000,
    "maxFileBytes": 5242880,
    "maxTotalBytes": 209715200,
    "timeoutMs": 30000
  },
  "analyzers": [
    { "name": "maven-model", "version": "0.1.0" },
    { "name": "java-declaration", "version": "0.1.0" }
  ],
  "resolutionMode": "OFFLINE"
}
```

- `skipped.samples` 最多 20 条，只给相对路径与原因。
- `truncations[]` 元素为 `{ "field": "data.modules", "shown": 200, "total": 512 }`。
- `resolutionMode`：`OFFLINE` 或 `REMOTE_AUTHORIZED`。
- 禁止在 metadata 中记录绝对路径、环境变量值或命令参数原文。

## 8. Tool 公共入参

所有 Tool 共享以下入参结构，Tool 专属字段在其后追加。

```json
{
  "workspaceId": "demo",
  "scope": { "modules": [], "include": ["**/*"], "exclude": [] },
  "budget": {
    "maxFiles": 20000,
    "maxFileBytes": 5242880,
    "maxTotalBytes": 209715200,
    "timeoutMs": 30000
  }
}
```

- `workspaceId` 必填，且必须是启动期注册的标识，不接受路径。
- `scope.modules` 为空表示全部模块。
- `scope.include` / `scope.exclude` 使用相对路径 glob；`exclude` 优先级更高。
- `budget` 为可选覆盖值，只能小于或等于服务端上限，不能放大。
- 任何绝对路径、`..` 段、符号链接目标都会被拒绝。

## 9. Tool 专属契约摘要

| Tool | `data` 主要字段 | `findings` 主要内容 |
| --- | --- | --- |
| `inspect_project` | `buildSystem`、`modules[]`、`moduleGraph`、`entrypoints[]`、`configFiles[]`、`technologies[]`、`unknown[]` | 通常为空 |
| `analyze_dependencies` | `resolutionMode`、`modules[].dependencies[]`、`modules[].declaredCount`、`modules[].conflictCount` | `DEP-*` 冲突、重复、未解析依赖 |
| `inspect_spring` | `configFiles[]`、`configKeys[]`、`componentScan[]`、`beanCandidates[]`、`limitations[]` | `SPR-*` 配置与组件风险 |
| `inspect_mybatis` | `mappers[]`（含 `statements[]`）、`limitations[]` | `MAP-*` 映射问题、`SQL-*` 规则命中 |

## 10. 完整示例：inspect_project

请求：

```json
{
  "workspaceId": "demo"
}
```

响应：

```json
{
  "schemaVersion": "1.0",
  "tool": "inspect_project",
  "toolVersion": "0.1.0",
  "status": "OK",
  "summary": "识别到 Maven 多模块工程，共 3 个模块，Spring Boot 2.7.18",
  "data": {
    "buildSystem": "MAVEN",
    "root": ".",
    "modules": [
      {
        "path": ".",
        "groupId": "com.example",
        "artifactId": "demo-parent",
        "version": "1.0.0",
        "packaging": "pom",
        "javaVersion": "17",
        "springBootVersion": "2.7.18"
      },
      {
        "path": "demo-api",
        "groupId": "com.example",
        "artifactId": "demo-api",
        "version": "1.0.0",
        "packaging": "jar",
        "javaVersion": "17",
        "springBootVersion": "2.7.18"
      },
      {
        "path": "demo-service",
        "groupId": "com.example",
        "artifactId": "demo-service",
        "version": "1.0.0",
        "packaging": "jar",
        "javaVersion": "17",
        "springBootVersion": "2.7.18"
      }
    ],
    "moduleGraph": {
      "nodes": ["com.example:demo-parent", "com.example:demo-api", "com.example:demo-service"],
      "edges": [
        { "from": "com.example:demo-service", "to": "com.example:demo-api", "type": "DECLARED" }
      ]
    },
    "entrypoints": [
      {
        "kind": "SPRING_BOOT_APPLICATION",
        "symbolId": {
          "module": "com.example:demo-service",
          "fqn": "com.example.demo.DemoApplication",
          "member": "main",
          "descriptor": null
        },
        "location": {
          "path": "demo-service/src/main/java/com/example/demo/DemoApplication.java",
          "line": 9,
          "column": 5,
          "symbolId": null
        }
      }
    ],
    "configFiles": [
      { "path": "demo-service/src/main/resources/application.yml", "format": "YAML", "profiles": [] }
    ],
    "technologies": [
      { "name": "spring-boot", "version": "2.7.18", "source": "parent-pom" },
      { "name": "mybatis-spring-boot-starter", "version": "2.3.1", "source": "dependency" }
    ],
    "unknown": []
  },
  "findings": [],
  "metadata": {
    "workspaceId": "demo",
    "scope": { "modules": [], "include": ["**/*"], "exclude": [] },
    "startedAt": "2026-11-12T06:20:11Z",
    "elapsedMs": 842,
    "scanned": { "files": 128, "bytes": 486213, "modules": 3 },
    "skipped": {
      "count": 12,
      "byReason": { "BINARY": 3, "SENSITIVE": 1, "EXCLUDED_DIR": 8 },
      "samples": [{ "path": "logs/app.log", "reason": "EXCLUDED_DIR" }]
    },
    "truncated": false,
    "truncations": [],
    "limits": {
      "maxFiles": 20000,
      "maxFileBytes": 5242880,
      "maxTotalBytes": 209715200,
      "timeoutMs": 30000
    },
    "analyzers": [{ "name": "maven-model", "version": "0.1.0" }],
    "resolutionMode": "OFFLINE"
  },
  "warnings": [],
  "errors": []
}
```

## 11. 完整示例：analyze_dependencies

请求：

```json
{
  "workspaceId": "demo",
  "scope": { "modules": ["demo-service"] },
  "options": { "includeTransitive": true, "maxDepth": 10, "resolutionMode": "OFFLINE" }
}
```

响应（离线模式下父 POM 不可用，状态为 `PARTIAL`）：

```json
{
  "schemaVersion": "1.0",
  "tool": "analyze_dependencies",
  "toolVersion": "0.1.0",
  "status": "PARTIAL",
  "summary": "demo-service 解析到 12 个直接依赖，1 个父 POM 未解析，发现 1 处版本冲突",
  "data": {
    "resolutionMode": "OFFLINE",
    "modules": [
      {
        "module": "com.example:demo-service:1.0.0",
        "path": "demo-service",
        "declaredCount": 12,
        "conflictCount": 1,
        "dependencies": [
          {
            "group": "org.springframework",
            "artifact": "spring-core",
            "requestedVersions": ["5.3.0", "5.3.20"],
            "selectedVersion": "5.3.20",
            "scope": "compile",
            "source": "TRANSITIVE",
            "depth": 3,
            "certainty": "CERTAIN",
            "unresolved": []
          },
          {
            "group": "com.example",
            "artifact": "demo-api",
            "requestedVersions": ["1.0.0"],
            "selectedVersion": "1.0.0",
            "scope": "compile",
            "source": "DECLARED",
            "depth": 1,
            "certainty": "CERTAIN",
            "unresolved": []
          }
        ]
      }
    ]
  },
  "findings": [
    {
      "code": "DEP-VERSION-CONFLICT",
      "severity": "MEDIUM",
      "title": "spring-core 存在多个请求版本",
      "kind": "RULE_MATCH",
      "certainty": "CERTAIN",
      "location": {
        "path": "demo-service/pom.xml",
        "line": 24,
        "column": null,
        "symbolId": null
      },
      "evidence": [
        {
          "kind": "DEPENDENCY_PATH",
          "summary": "5.3.0 由 demo-api 传递引入，5.3.20 来自 dependencyManagement",
          "certainty": "CERTAIN",
          "hops": [
            { "ref": "com.example:demo-service:1.0.0", "symbolId": null, "location": null, "note": "根模块" },
            { "ref": "com.example:demo-api:1.0.0", "symbolId": null, "location": { "path": "demo-api/pom.xml", "line": 30, "column": null, "symbolId": null }, "note": "声明依赖" },
            { "ref": "org.springframework:spring-core:5.3.0", "symbolId": null, "location": null, "note": "传递请求版本" }
          ],
          "excerpt": null
        }
      ],
      "description": "spring-core 被请求 5.3.0 与 5.3.20，Maven 最终选定 5.3.20；这不等于运行时一定加载两个版本。",
      "suggestion": "确认版本来源，必要时在 dependencyManagement 中固定版本。",
      "unresolved": [],
      "confidence": null
    }
  ],
  "metadata": {
    "workspaceId": "demo",
    "scope": { "modules": ["demo-service"], "include": ["**/*"], "exclude": [] },
    "startedAt": "2026-11-12T06:21:03Z",
    "elapsedMs": 2145,
    "scanned": { "files": 46, "bytes": 201334, "modules": 1 },
    "skipped": { "count": 0, "byReason": {}, "samples": [] },
    "truncated": false,
    "truncations": [],
    "limits": {
      "maxFiles": 20000,
      "maxFileBytes": 5242880,
      "maxTotalBytes": 209715200,
      "timeoutMs": 30000
    },
    "analyzers": [{ "name": "maven-resolver", "version": "0.1.0" }],
    "resolutionMode": "OFFLINE"
  },
  "warnings": [
    {
      "code": "UNRESOLVED_DEPENDENCY",
      "message": "父 POM com.example:demo-parent:1.0.0 在离线模式下不可用，版本仲裁可能不完整",
      "target": "com.example:demo-parent:1.0.0",
      "scope": "module:demo-service"
    }
  ],
  "errors": []
}
```

## 12. 失败示例

```json
{
  "schemaVersion": "1.0",
  "tool": "inspect_mybatis",
  "toolVersion": "0.1.0",
  "status": "FAILED",
  "summary": "Workspace 未授权",
  "data": null,
  "findings": [],
  "metadata": {
    "workspaceId": "unknown",
    "scope": { "modules": [], "include": ["**/*"], "exclude": [] },
    "startedAt": "2026-11-12T06:22:00Z",
    "elapsedMs": 3,
    "scanned": { "files": 0, "bytes": 0, "modules": 0 },
    "skipped": { "count": 0, "byReason": {}, "samples": [] },
    "truncated": false,
    "truncations": [],
    "limits": {
      "maxFiles": 20000,
      "maxFileBytes": 5242880,
      "maxTotalBytes": 209715200,
      "timeoutMs": 30000
    },
    "analyzers": [],
    "resolutionMode": "OFFLINE"
  },
  "warnings": [],
  "errors": [
    {
      "code": "WORKSPACE_NOT_AUTHORIZED",
      "category": "PERMISSION",
      "message": "workspaceId 未在启动配置中注册，请检查启动参数或配置文件",
      "target": "workspaceId",
      "recoverable": true,
      "details": {}
    }
  ]
}
```
