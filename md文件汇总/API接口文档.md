# AI API 一期接口文档

## 1. 项目概览

### 1.1 一期目标
一期实现基于 Spring Boot 的多 Agent 协同后端最小闭环，覆盖：
- 项目管理
- 需求管理
- 需求链路开发管理
- 测试记录管理
- Agent 管理
- 主控需求完整性检查

### 1.2 技术栈
- Java 17
- Spring Boot 3.3.4
- Spring Web
- Spring Validation
- Spring Data JPA
- Flyway
- MySQL 8.x
- H2（测试）
- Springdoc OpenAPI

### 1.3 统一返回结构
所有接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 1.4 Swagger 地址
服务启动后可访问：
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 2. 模块划分

### 2.1 Project 项目模块
负责项目基础资料维护。

### 2.2 Requirement 需求模块
负责需求主单维护、按项目查询、状态更新。

### 2.3 Link 需求链路模块
负责需求拆解后的开发链路任务创建、查询、Agent 拉任务、Agent 回写进度。

### 2.4 TestRecord 测试记录模块
负责需求测试记录登记与查询。

### 2.5 Agent 模块
负责 Agent 注册、查询、心跳更新。

### 2.6 Inspection 检查模块
负责按 `requirementNo` 聚合链路、测试、Agent 数据，输出缺失项、未完成项、阻塞项和建议通知 Agent。

---

## 3. 接口清单

## 3.1 项目管理

### 3.1.1 创建项目
- 方法：`POST`
- 路径：`/api/projects`

核心请求字段：
- `projectCode` 项目编码，必填
- `projectName` 项目名称，必填
- `projectDesc` 项目说明
- `businessGoal` 业务目标
- `techStack` 技术栈
- `repositoryUrl` 仓库地址
- `ownerAgentCode` 主控 Agent 标识
- `markdownSyncMode` Markdown 同步策略，可选，`OVERWRITE` / `CANCEL`
- `markdownFiles` 项目 Markdown 文件列表，可选
- `status` 项目状态，必填

请求示例：
```json
{
  "projectCode": "AI-PLATFORM",
  "projectName": "AI 协同平台",
  "projectDesc": "一期后端建设",
  "businessGoal": "实现 Agent 协同闭环",
  "techStack": "Spring Boot, MySQL",
  "repositoryUrl": "https://example.com/repo.git",
  "ownerAgentCode": "main-agent",
  "status": "ENABLED"
}
```

核心响应字段：
- `id`
- `projectCode`
- `projectName`
- `status`
- `createdAt`
- `updatedAt`

### 3.1.2 查询项目详情
- 方法：`GET`
- 路径：`/api/projects/{projectCode}`

核心响应字段：
- `projectCode`
- `projectName`
- `projectDesc`
- `businessGoal`
- `techStack`
- `repositoryUrl`
- `ownerAgentCode`
- `status`

### 3.1.3 项目列表
- 方法：`GET`
- 路径：`/api/projects`

核心响应字段：
- 项目列表数组
- 每项包含 `projectCode`、`projectName`、`status`、`ownerAgentCode`、`markdownSyncMode`

### 3.1.4 更新项目
- 方法：`PUT`
- 路径：`/api/projects/{projectCode}`

核心请求字段：
- `projectName` 项目名称，必填
- `projectDesc` 项目说明
- `businessGoal` 业务目标
- `techStack` 技术栈
- `repositoryUrl` 仓库地址
- `ownerAgentCode` 主控 Agent 标识
- `markdownSyncMode` Markdown 同步策略，必填
- `status` 项目状态，必填

说明：
- `projectCode` 作为主键保持不变，通过路径参数传入

### 3.1.5 删除项目
- 方法：`DELETE`
- 路径：`/api/projects/{projectCode}`

说明：
- 如果项目下仍存在 Agent、需求或会话，将拒绝删除
- 删除成功时会同时清理该项目的 Markdown 配置

### 3.1.6 查询项目 Markdown 配置
- 方法：`GET`
- 路径：`/api/projects/{projectCode}/markdown-config`

核心响应字段：
- `projectCode`
- `markdownSyncMode`
- `markdownFiles`

Markdown 文件字段：
- `fileType`：`BASE` / `EXTRA`
- `baseKey`：基础文件槽位，可选，`AGENTS` / `SOUL` / `USER` / `MEMORY` / `LEARNINGS`
- `filePath`：相对路径
- `content`：Markdown 内容

### 3.1.7 保存项目 Markdown 配置
- 方法：`PUT`
- 路径：`/api/projects/{projectCode}/markdown-config`

请求示例：
```json
{
  "markdownSyncMode": "CANCEL",
  "markdownFiles": [
    {
      "fileType": "BASE",
      "baseKey": "AGENTS",
      "filePath": "AGENTS.md",
      "content": "# 项目协作约定"
    },
    {
      "fileType": "EXTRA",
      "filePath": "docs/开发交接.md",
      "content": "# 开发交接规范"
    }
  ]
}
```

---

## 3.2 需求管理

### 3.2.1 创建需求
- 方法：`POST`
- 路径：`/api/requirements`

核心请求字段：
- `requirementNo` 需求编号，必填
- `projectCode` 所属项目编码，必填
- `title` 需求标题，必填
- `requirementDesc` 需求说明
- `priority` 优先级
- `status` 需求状态，必填
- `source` 来源
- `mainAgentCode` 主控 Agent
- `currentStage` 当前阶段
- `expectedDeadline` 期望完成时间
- `createdBy` 创建人

请求示例：
```json
{
  "requirementNo": "REQ-20260415-001",
  "projectCode": "AI-PLATFORM",
  "title": "新增 inspection 检查能力",
  "requirementDesc": "支持主控按需求编号检查缺失项",
  "priority": "P1",
  "status": "PENDING",
  "source": "phase1",
  "mainAgentCode": "main-agent",
  "currentStage": "dispatch",
  "expectedDeadline": "2026-04-20T18:00:00",
  "createdBy": "system"
}
```

核心响应字段：
- `id`
- `requirementNo`
- `projectCode`
- `title`
- `status`
- `currentStage`
- `expectedDeadline`

### 3.2.2 查询需求详情
- 方法：`GET`
- 路径：`/api/requirements/{requirementNo}`

核心响应字段：
- `requirementNo`
- `projectCode`
- `title`
- `requirementDesc`
- `priority`
- `status`
- `mainAgentCode`
- `currentStage`

### 3.2.3 需求列表
- 方法：`GET`
- 路径：`/api/requirements`
- 查询参数：`projectCode`（可选）

核心响应字段：
- 需求列表数组
- 支持按 `projectCode` 过滤

### 3.2.4 更新需求状态
- 方法：`PUT`
- 路径：`/api/requirements/{requirementNo}/status`

核心请求字段：
- `status`，必填

请求示例：
```json
{
  "status": "IN_PROGRESS"
}
```

核心响应字段：
- `requirementNo`
- `status`
- `updatedAt`

---

## 3.3 需求链路开发管理

### 3.3.1 新增链路任务
- 方法：`POST`
- 路径：`/api/requirements/{requirementNo}/links`

核心请求字段：
- `linkType` 链路类型，必填
- `taskTitle` 任务标题，必填
- `taskDesc` 任务描述
- `agentCode` 负责 Agent，必填
- `developerName` 开发者名称
- `status` 链路状态，必填
- `dependsOnLinkId` 依赖链路 ID

请求示例：
```json
{
  "linkType": "BACKEND",
  "taskTitle": "实现 inspection service",
  "taskDesc": "补齐主控检查能力",
  "agentCode": "agent-dev",
  "developerName": "dev-a",
  "status": "TODO",
  "dependsOnLinkId": null
}
```

核心响应字段：
- `id`
- `requirementNo`
- `linkType`
- `taskTitle`
- `agentCode`
- `status`
- `dependsOnLinkId`

### 3.3.2 查询需求下全部链路
- 方法：`GET`
- 路径：`/api/requirements/{requirementNo}/links`

核心响应字段：
- 链路列表数组
- 每项包含 `id`、`linkType`、`taskTitle`、`agentCode`、`status`

### 3.3.3 Agent 拉取自己的任务
- 方法：`GET`
- 路径：`/api/agents/{agentCode}/tasks`

说明：
- 返回该 Agent 名下状态为 `TODO`、`DOING`、`BLOCKED` 的任务

核心响应字段：
- `id`
- `requirementNo`
- `linkType`
- `taskTitle`
- `status`
- `resultSummary`
- `deliverablePath`

### 3.3.4 Agent 更新任务执行结果
- 方法：`PUT`
- 路径：`/api/links/{linkId}/progress`

核心请求字段：
- `status`，必填
- `resultSummary`
- `deliverablePath`
- `startedAt`
- `finishedAt`

请求示例：
```json
{
  "status": "DONE",
  "resultSummary": "inspection service completed",
  "deliverablePath": "/deliverables/REQ-20260415-001/backend.md",
  "startedAt": "2026-04-15T10:00:00",
  "finishedAt": "2026-04-15T12:00:00"
}
```

核心响应字段：
- `id`
- `status`
- `resultSummary`
- `deliverablePath`
- `startedAt`
- `finishedAt`
- `updatedAt`

---

## 3.4 测试管理

### 3.4.1 新增测试记录
- 方法：`POST`
- 路径：`/api/requirements/{requirementNo}/tests`

核心请求字段：
- `testType` 测试类型，必填
- `testTitle` 测试标题，必填
- `testContent` 测试内容
- `testerAgentCode` 测试 Agent
- `testerName` 测试人
- `testResult` 测试结果，必填
- `bugCount` 缺陷数，必填
- `riskDesc` 风险说明
- `suggestion` 建议
- `attachments` 附件
- `testedAt` 测试时间

请求示例：
```json
{
  "testType": "接口测试",
  "testTitle": "inspection API smoke test",
  "testContent": "验证缺失项输出",
  "testerAgentCode": "agent-test",
  "testerName": "tester-a",
  "testResult": "PASS",
  "bugCount": 0,
  "riskDesc": "无",
  "suggestion": "可进入下一步",
  "attachments": "[]",
  "testedAt": "2026-04-15T16:00:00"
}
```

核心响应字段：
- `id`
- `requirementNo`
- `testType`
- `testTitle`
- `testerAgentCode`
- `testResult`
- `bugCount`
- `testedAt`

### 3.4.2 查询测试记录
- 方法：`GET`
- 路径：`/api/requirements/{requirementNo}/tests`

核心响应字段：
- 测试记录列表数组
- 每项包含 `testType`、`testTitle`、`testResult`、`bugCount`、`testedAt`

---

## 3.5 Agent 管理

### 3.5.1 注册 Agent
- 方法：`POST`
- 路径：`/api/agents`

核心请求字段：
- `agentCode` Agent 编码，必填
- `agentName` Agent 名称，必填
- `projectCode` 所属项目编码，必填
- `agentRole` Agent 角色，必填
- `agentDesc` 描述
- `capabilityTags` 能力标签
- `supportedLinkTypes` 支持链路类型
- `callbackMode` 回调模式
- `endpointUrl` 接口地址
- `status` Agent 状态，必填

请求示例：
```json
{
  "agentCode": "agent-dev",
  "agentName": "Backend Dev Agent",
  "projectCode": "AI-PLATFORM",
  "agentRole": "DEVELOPER",
  "agentDesc": "负责后端开发链路",
  "capabilityTags": "java,spring,mysql",
  "supportedLinkTypes": "BACKEND,API,DOCS",
  "callbackMode": "PULL",
  "endpointUrl": "http://localhost:9001",
  "status": "ONLINE"
}
```

核心响应字段：
- `id`
- `agentCode`
- `agentName`
- `projectCode`
- `agentRole`
- `supportedLinkTypes`
- `status`
- `lastHeartbeatTime`

### 3.5.2 Agent 列表
- 方法：`GET`
- 路径：`/api/agents`

查询参数：
- `projectCode`（可选）

核心响应字段：
- Agent 列表数组
- 每项包含 `agentCode`、`agentName`、`projectCode`、`agentRole`、`status`

### 3.5.3 Agent 详情
- 方法：`GET`
- 路径：`/api/agents/{agentCode}`

核心响应字段：
- `agentCode`
- `agentName`
- `projectCode`
- `agentRole`
- `agentDesc`
- `capabilityTags`
- `supportedLinkTypes`
- `status`
- `lastHeartbeatTime`

### 3.5.4 更新 Agent
- 方法：`PUT`
- 路径：`/api/agents/{agentCode}`

核心请求字段：
- `agentName` Agent 名称，必填
- `projectCode` 所属项目编码，必填
- `agentRole` Agent 角色，必填
- `agentDesc` 描述
- `capabilityTags` 能力标签
- `supportedLinkTypes` 支持链路类型
- `callbackMode` 回调模式
- `endpointUrl` 接口地址
- `status` Agent 状态，必填

说明：
- `agentCode` 作为主键保持不变，通过路径参数传入
- 如果 Agent 已被项目、需求、链路、测试记录或会话引用，则不允许修改所属项目

### 3.5.5 删除 Agent
- 方法：`DELETE`
- 路径：`/api/agents/{agentCode}`

说明：
- 如果 Agent 仍被项目、需求、链路、测试记录或会话引用，将拒绝删除

### 3.5.6 更新 Agent 心跳
- 方法：`POST`
- 路径：`/api/agents/{agentCode}/heartbeat`

说明：
- 更新 `lastHeartbeatTime`
- 如果当前状态为 `OFFLINE`，会自动改成 `ONLINE`

核心响应字段：
- `agentCode`
- `status`
- `lastHeartbeatTime`

---

## 3.6 主控检查接口

### 3.6.1 检查需求链路完整性
- 方法：`GET`
- 路径：`/api/requirements/{requirementNo}/inspection`

检查规则：
- 至少有一条开发链路
- 默认关键链路包含 `BACKEND`、`API`、`DOCS`
- 标题或描述命中前端关键词时增加 `FRONTEND`
- 标题或描述命中数据库关键词时增加 `DB`
- 没有测试记录时返回 `TEST_RECORD` 缺失
- `TODO`、`DOING` 归入未完成项
- `BLOCKED` 归入阻塞项
- 建议通知 Agent 优先使用已分配 Agent，其次匹配在线且支持缺失链路类型的 Agent

核心响应字段：
- `requirementNo`
- `title`
- `requirementStatus`
- `existingLinks`
- `missingItems`
- `incompleteLinks`
- `blockedLinks`
- `suggestedAgents`

响应示例：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requirementNo": "REQ-20260415-001",
    "title": "新增 inspection 检查能力",
    "requirementStatus": "IN_PROGRESS",
    "existingLinks": ["BACKEND"],
    "missingItems": ["API", "DOCS", "TEST_RECORD"],
    "incompleteLinks": [
      {
        "linkId": 1,
        "linkType": "BACKEND",
        "agentCode": "agent-dev",
        "status": "DOING",
        "summary": null
      }
    ],
    "blockedLinks": [],
    "suggestedAgents": [
      {
        "agentCode": "agent-dev",
        "reason": "link BACKEND not finished"
      }
    ]
  }
}
```

---

## 4. 一期数据表

已包含以下核心表：
- `project_info`
- `requirement_info`
- `agent_info`
- `requirement_dev_link`
- `requirement_test_record`

数据库迁移文件：
- `src/main/resources/db/migration/V1__init_schema.sql`

---

## 5. 本地启动说明

### 5.1 配置数据库
默认读取：
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`

默认 MySQL 连接：
`jdbc:mysql://localhost:3306/ai_api?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai`

### 5.2 启动命令
如果本机已安装 Maven：

```bash
mvn spring-boot:run
```

或执行测试：

```bash
mvn test
```

### 5.3 测试环境
测试使用 H2 内存数据库，配置文件：
- `src/test/resources/application.yml`

---

## 6. 当前一期覆盖结论

已覆盖一期需求中的：
- 五张核心表
- 项目/需求/链路/测试/Agent 基础 API
- Agent 按 `agentCode` 拉任务
- Agent 回写链路进度与结果
- 主控按 `requirementNo` 检查缺失项、未完成项、阻塞项、建议通知 Agent
- Swagger/OpenAPI 地址配置
- 基础启动测试与 inspection 规则单测
