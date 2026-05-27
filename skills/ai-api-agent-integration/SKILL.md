---
name: ai-api-agent-integration
description: 这个技能主要是通过api都方式管理需求任务执行，而非单纯的md文件阅读驱动，如果出现查询需求，任务需要调用 当用户要“使用 ai-api 协作中台”而不是单纯写本地代码时，必须调用这个技能。典型触发包括：查询 `projectCode / requirementNo / agentCode / linkId / sessionCode`；判断当前 Agent 该执行什么；查看总需求下是否已有子任务；查看/创建开发链路；查看 inspection；回写 `DONE/BLOCKED` 结果与详细执行回执；创建或复用会话；修改或删除未执行的子任务。它是一个已经存在、可直接使用的 ai-api 协作技能，不是创建 skill 的模板。用户说“加载/使用/初始化 ai-api-agent-integration 技能”时，默认是使用这份现成技能，而不是激活 skill-creator 重新造一个新 skill；只有用户明确要求新建 skill、重写 SKILL.md 或设计技能模板时，才应该使用 skill-creator。
---

# AI API Agent Integration

## 先消除一个最常见的误解

所以当用户说下面这些话时：

- “加载 `ai-api-agent-integration` 技能”
- “使用 `./skills/ai-api-agent-integration`”
- “初始化 `ai-api-agent-integration` 技能”
- “用这个 skill 处理 ai-api 的任务”

默认都应该理解成：

**使用这份已有 skill，按当前工作目录读取它的 `skill-config.json`，然后调用它的脚本命令。**

**不要**默认理解成：

- 要新建一个 skill
- 要重写这个 skill 的骨架
- 要激活 `skill-creator` 来反问“这个技能要做什么”

只有用户明确表达下面这些意思，才应该使用 `skill-creator`：

- “帮我新建一个 skill”
- “重写这个 skill 的 SKILL.md”
- “重新设计这个技能的结构/模板”
- “基于这个名字创建一个新技能”

如果用户只是想让 AI 去“用这个 skill 干活”，那就直接用本 skill，不要再走 skill-creator 的提问流程。

## “初始化这个技能”在这里到底是什么意思

在 ai-api 客户端长会话里，技能初始化应该发生在会话创建阶段。后续同一个会话下发多个任务时，直接复用会话中已初始化的技能上下文，不要在每个任务执行 prompt 里再次要求“加载/初始化技能”。

在这个 skill 的语境里，“初始化”通常不是“创建 skill 本身”，而是下面两种事之一：

1. 读取 `skill-config.json`，按当前工作目录确认默认的 `projectCode / agentCode / baseUrl`
2. 调用脚本里的 `init` 命令，把当前 Agent 注册/对齐到 ai-api 上下文

也就是说，这里的“初始化”更接近：

- 初始化 ai-api 协作上下文
- 初始化 Agent 在 ai-api 里的身份
- 初始化这个 skill 的运行参数

而不是：

- 初始化一个全新的 skill 项目

## 如果用户只说“初始化这个技能”，不要反问业务场景，先做这 2 步

先执行：

```bash
python3 scripts/ai_api_skill_client.py config show
```

然后：

- 如果配置里已经有 `projectCode` 和 `agentCode`，直接继续执行 `agents next` 或 `requirements get`
- 如果配置不完整，再执行 `projects list` 或 `init`

**不要一上来就问“这个技能是做什么的”**。  
这份 skill 的用途已经是确定的：它就是 ai-api 协作技能，不需要重新发明定义。

## 这个技能到底是干什么的

一句话：

**这个技能是让其他 AI/Agent 接入 `ai-api` 协作中台，用来查项目、查需求、查子任务、查链路、查会话、查 inspection，并把执行结果回写到后端。**

## 什么场景下一定要调用这个技能

只要用户的真实意图是“和 ai-api 后端协作状态交互”，就要调用这个技能。

最常见触发场景如下：

- 用户提到 `ai-api`
- 用户提到 `projectCode`、`requirementNo`、`agentCode`、`linkId`、`sessionCode`
- 用户说“看看当前任务 / 当前轮到我做什么 / 这个 Agent 现在该做什么”
- 用户说“查一下这个需求 / 这个需求下面有没有子任务 / 这个总需求怎么执行”
- 用户说“看看链路 / 看看 inspection / 还缺什么 / 为什么卡住了”
- 用户说“把这个任务回写 DONE / BLOCKED / 提交结果 / 回填执行内容”
- 用户说“创建子任务 / 创建链路 / 创建会话 / 复用会话”
- 用户说“修改子任务 / 删除未执行子任务”

换句话说，这个技能处理的是：

- ai-api 中台里的项目、需求、子任务、链路、会话、inspection、回执

这个技能**不**处理的是：

- 单纯本地写代码
- 单纯解释业务代码
- 单纯阅读仓库结构
- 单纯新建一个技能模板

它解决的不是“怎么写业务代码”，而是下面这些协作问题：

- 我现在属于哪个项目
- 我这个 Agent 叫什么、有没有注册
- 当前轮到我执行哪个任务
- 一个总需求下面有没有已经拆好的子任务
- 某个任务做完后，怎么把结果和详细回执回填到 ai-api
- 什么时候应该继续自己做，什么时候应该拆给别的 AI

如果另一个 AI 加载了这个 skill，却还在先看仓库目录、先猜项目结构、先看 `pom.xml`/`package.json`，那就是用错了。  
**这个 skill 的入口永远是先跑脚本命令，不是先看代码仓库。**

## 你加载这个技能后，脑子里先记住这 4 件事

1. 这是“ai-api 协作技能”，不是“项目源码阅读技能”
2. 默认配置优先从 `skill-config.json` 的当前工作目录 entry 读取，不要每次都手填
3. 主需求不等于可执行任务，很多时候真正要执行的是子任务或链路
4. `inspection` 只是缺项总览，不是当前执行入口

## 默认配置从哪里来

这个 skill 目录下有 `skill-config.json`，配置按工作目录拆分。脚本会用 `--workspace-dir`、`AI_API_SKILL_WORKSPACE_DIR` 或当前目录匹配 `workspaces[].workspaceDir`；匹配不到会提示并终止，避免多个项目/多个 AI 共用错误配置。

每个工作目录 entry 通常会提供这些默认值：

- `projectCode`
- `agentCode`
- `agentName`
- `agentEngineType`
- `agentRole`
- `baseUrl`
- `workspaceDir`

典型配置示例：

```json
{
  "workspaces": [
    {
      "workspaceDir": "/Users/zhaoyiming/Desktop/项目/ai-api",
      "projectCode": "ai-api",
      "agentCode": "ce-main-agent",
      "agentName": "CE Main Agent",
      "agentEngineType": "CODEX",
      "agentRole": "DEVELOPER",
      "baseUrl": "http://localhost:8080"
    }
  ]
}
```

这意味着：

- 你**不一定每次都要手动传** `--project-code`
- 你**不一定每次都要手动传** `--agent-code`
- 如果脚本里已经能从配置文件拿到默认值，就应该直接复用
- 如果当前目录不是项目根，显式传 `--workspace-dir /absolute/path/to/workspace`

## 创建或对齐 Agent 时必须带智能体类型

Agent 信息里的智能体类型只允许下面三个值：

- `CODEX`
- `QODER`
- `CLAUDE`

调用 `init` 或 `agents ensure` 时要传 `--agent-engine-type CODEX|QODER|CLAUDE`。如果没有明确上下文，优先根据实际 worker/ACP 工具选择；不要编造其他类型，也不要写成 `gpt`、`openai`、`chatgpt` 这类模型或供应商名称。

只有下面几种情况才需要显式覆盖：

- 你要切换到另一个项目
- 你要切换到另一个 Agent 身份
- 你要连不同的 `baseUrl`
- 你要临时使用另一个工作目录的配置

## 其他 AI 第一次加载这个技能时，应该怎么理解它

把它理解成一个固定流程：

1. 先确认我现在用的是哪个项目、哪个 Agent
2. 再确认这个 Agent 有没有已分配任务
3. 如果用户给的是需求编号，先判断这是总需求、子任务，还是链路任务
4. 如果是总需求，先看子任务，不要直接停在 `inspection`
5. 执行完后必须回写结果，而且除了摘要，还要有详细执行回执

## 最常见的 3 条使用路径

### 路径 A：用户说“看看我现在该做什么”

先执行：

```bash
python3 scripts/ai_api_skill_client.py agents next --claim
```

如果配置文件里已有 `agentCode`，这里甚至不需要显式传 `--agent-code`。

`--claim` 表示准备开始执行当前任务：非 ACP 会话会先把当前子模块置为 `IN_PROGRESS`，并把当前链路置为 `DOING`，避免其他执行入口重复领取；ACP 会话会自动跳过这一步。

总需求用于描述目标和上下文，子模块才是当前 Agent 的执行单元。`agents next` 会合并开发链路任务和未建链路的子模块任务；未建链路时先认领子模块，再创建开发链路并回写结果。

你要看的重点是：

- `currentTask`
- `readyTasks`
- `waitingTasks`
- `blockedTasks`

这条路径是“按当前 Agent 拉取可执行任务”的标准入口。

### 路径 B：用户给了一个需求编号，让你“处理这个需求”

先执行：

```bash
python3 scripts/ai_api_skill_client.py requirements get --requirement-no REQ-XXX
```

然后**立刻**执行：

```bash
python3 scripts/ai_api_skill_client.py requirements children \
  --master-requirement-no REQ-XXX
```

原因很重要：

- `requirements get` 只能告诉你这个需求本身是什么
- 真正要执行的，往往是它下面的子任务
- 如果你只看 `inspection`，很容易误判成“没任务/没链路/还没拆”

如果任务上下文里已经有 `documentId`，优先直接查文档：

```bash
python3 scripts/ai_api_skill_client.py documents get --document-id DOC-XXX
```

这个命令会同时返回：

- `document`：当前文档本体
- `subtree`：当前文档及其子文档树

需要单独看路径或引用时，还可以用：

```bash
python3 scripts/ai_api_skill_client.py documents path --document-id DOC-XXX
python3 scripts/ai_api_skill_client.py documents refs --document-id DOC-XXX
```

### 路径 C：用户说“这个任务做完了，回写一下”

先确认 `linkId` 或链路上下文，然后执行：

```bash
python3 scripts/ai_api_skill_client.py links update \
  --link-id 12 \
  --requirement-no REQ-XXX \
  --status DONE \
  --result-summary "接口完成" \
  --execution-details "执行步骤、实际改动、验证过程和结果"
```

这里必须记住：

- `resultSummary` 是简短摘要
- `executionDetails` 是详细执行回执
- 长文本优先用 `--execution-details-stdin`，不要在工作目录创建 `receipt.md`、`receipt_*.md` 或其他任务回执文件
- 链路回写后，所属子任务状态也应该同步为 `IN_PROGRESS / DONE / BLOCKED`

## 如果你只记一个决策原则，就记这个

**当前 Agent 的执行入口优先级：**

1. `agents next`
2. 子任务列表
3. 链路列表
4. inspection

也就是说：

- 不要先看 inspection 再猜有没有任务
- 不要只看主需求状态就说“没有可执行内容”
- 不要把总需求当成实际执行单元

## 一个最容易犯的错

错误做法：

1. 用户给了总需求编号
2. AI 执行 `requirements inspection`
3. 看到“缺 BACKEND / API / DOCS”
4. 直接回复“这个需求还没有任务，需要你先告诉我做什么”

这通常是错的。

更合理的做法是：

1. 先 `requirements get`
2. 再 `requirements children`
3. 看子任务里有没有已经拆好的执行步骤
4. 再决定是不是还需要看链路或 inspection

## 如果另一个 AI 完全不知道怎么开始，直接让它照这个最小流程走

```bash
# 1. 看默认配置
python3 scripts/ai_api_skill_client.py config show

# 2. 看当前 Agent 是否已有待执行任务
python3 scripts/ai_api_skill_client.py agents next

# 3. 如果用户给了 requirementNo，再看需求和子任务
python3 scripts/ai_api_skill_client.py requirements get --requirement-no REQ-XXX
python3 scripts/ai_api_skill_client.py requirements children --master-requirement-no REQ-XXX
```

如果这 3 步都还没做，就不要急着下结论。

## 强制入口

加载这个技能后，第一步只能做下面两件事之一：

1. 如果还不知道 `projectCode`，执行：

```bash
python3 scripts/ai_api_skill_client.py projects list
```

2. 如果已经知道 `projectCode`，执行：

```bash
python3 scripts/ai_api_skill_client.py init \
  --project-code <PROJECT_CODE> \
  --agent-code <AGENT_CODE> \
  --agent-name "<AGENT_NAME>" \
  --agent-role DEVELOPER
```

在这两步完成之前，不允许做仓库探索。

## 禁止行为

加载这个技能后，禁止先做下面这些动作：

- 不要 `Glob` / `Find` / `Explore` 代码仓库来猜这个技能用途
- 不要扫描 `build.gradle`、`pom.xml`、`package.json`、`settings.gradle` 之类的构建文件
- 不要先看仓库目录结构再决定是否调用 ai-api
- 不要把“理解 skill”变成“理解整个项目代码”

这个技能的入口不是仓库扫描，而是先跑脚本命令。

## 核心功能

- AI API 对接 Agent：让外部 Agent 通过 ai-api 查询项目、需求、链路、会话和 inspection，并回写执行结果
- Agent 集成开发：先完成 `projectCode` 和 Agent 初始化，再判断当前任务能否独立执行
- API + Agent 全流程：覆盖项目选择、Agent 注册、按当前 Agent 自动拉取可执行子任务、inspection、拆分协作和结果回写

## 触发场景

- 用户提到 `ai-api`
- 用户要先看项目列表、再决定 `projectCode`
- 用户要检查 Agent 是否已注册，或让 Agent 接任务
- 用户要按当前 Agent 自动判断“现在该执行哪个子模块/链路”
- 用户要看当前需求、当前链路、当前会话，或者要查 `inspection`
- 用户要创建子需求、链路、会话，或更新 session preference
- 用户要回写 `DONE` / `BLOCKED`

## 使用示例

- “先看有哪些项目，选一个 `projectCode`，然后初始化 Agent”
- “查 `REQ-XXX` 的 inspection，看要不要拆给其他 AI”
- “把 `agentCode=<AGENT_CODE>` 的当前任务拿出来，执行后回写 DONE”
- “按当前 Agent 自动找出现在应该执行的子任务，做完继续下一个”
- “这个需求复用 `SES-20260507-001`，顺便创建子需求并绑定会话”

## 初始化

这个技能在开始工作前，必须先完成初始化。

初始化前置条件：

- 必须先填写 `projectCode`
- 如果不知道 `projectCode`，先查项目列表
- 初始化阶段必须先执行 Agent 查询/注册，不存在就注册

初始化顺序固定如下：

1. 先填写 `projectCode`
2. 先执行 Agent 查询/注册，不存在就注册
3. 再查询项目是否存在
4. 如果项目不存在，先不要继续后续动作
5. 然后才进入任务、inspection、链路和会话判断

初始化命令：
```bash
python3 scripts/ai_api_skill_client.py init \
  --project-code <PROJECT_CODE> \
  --agent-code <AGENT_CODE> \
  --agent-name "<AGENT_NAME>" \
  --agent-role DEVELOPER
```

如果你还不知道 `projectCode`，先查项目列表，再把选中的编码填回初始化命令：

```bash
python3 scripts/ai_api_skill_client.py projects list
```

初始化成功后，才允许继续执行后面的任务判断和回写流程。

## 什么时候必须加载这个技能

当用户消息里出现下面这些信号时，必须加载这个技能：

- 提到 `ai-api`
- 提到“当前任务”“当前需求”“当前链路”“当前会话”
- 提到 `inspection`
- 提到“提交结果”“回写进度”“反馈状态”
- 提到“拆分需求”“创建子需求”“创建链路”“创建会话”
- 提到“沿用旧会话”“复用会话”
- 提到 `projectCode`、`requirementNo`、`linkId`、`agentCode`、`sessionCode`

注意：

- 没有 `projectCode` 时，不要直接进入需求创建、链路创建、会话绑定或回写
- 没有先完成 Agent 查询/注册时，不要开始执行任务回写

## 什么时候不要用这个技能

下面这些情况不要加载这个技能：

- 只是普通本地编码，没有要同步到 ai-api 的状态
- 只是普通讨论，不需要查后端当前状态
- 只是单纯改文档、改页面、改代码，但不需要在 ai-api 里记录结果
- 用户没有要求和 ai-api 协作中台发生交互

## 先判断用户说的“任务”是哪一种

在 ai-api 里，“任务”常见有三种语义，不要混淆：

1. Agent 当前任务
   含义：某个 `agentCode` 现在被分配到的待办链路任务。
   典型命令：

   ```bash
   python3 scripts/ai_api_skill_client.py agents tasks --agent-code YOUR_AGENT
   ```

   如果用户说“查看需求列表”，默认只展示当前技能配置项目、当前 Agent 负责且未完成/未关闭的需求，不分页：

   ```bash
   python3 scripts/ai_api_skill_client.py requirements list --open-only
   ```

   工作流任务执行前，如果需要读取上游结果，使用：

   ```bash
   python3 scripts/ai_api_skill_client.py requirements workflow-results --requirement-no REQ-XXX
   ```

   `workflow-results` 只返回允许提取响应的已完成子模块回执，用来整合上游结果，不要在本地创建 `receipt.md` 或 `receipt_*.md`。

2. 某个需求下的链路任务
   含义：某个 `requirementNo` 下面的 BACKEND/API/DOCS/DB 等开发链路。
   典型命令：

   ```bash
   python3 scripts/ai_api_skill_client.py requirements links --requirement-no REQ-XXX
   ```

3. 总需求 / 子需求本身
   含义：需求树结构，不是具体执行链路。
   典型命令：

   ```bash
   python3 scripts/ai_api_skill_client.py requirements get --requirement-no REQ-XXX
   ```

如果你没先分清这三类，就不要急着回写状态。

## 标准判断顺序

当用户让你“看当前状态”或“继续推进”时，按这个顺序判断：

1. 我知不知道 `projectCode`
2. 我知不知道 `requirementNo`
3. 我知不知道 `agentCode`
4. 用户说的是 Agent 当前任务，还是需求链路
5. 我是要查询状态，还是要提交反馈

如果 `projectCode` 不知道，先查项目列表，不要硬编码。

命令：

```bash
python3 scripts/ai_api_skill_client.py projects list
```

## 直接命令怎么用

```bash
python3 scripts/ai_api_skill_client.py --help
```

## 什么时候该调用哪个命令

### 1. 先看有哪些项目

适用场景：

- 用户没给 `projectCode`
- 你不知道任务属于哪个项目
- 你需要先确认项目编码再创建需求

命令：

```bash
python3 scripts/ai_api_skill_client.py projects list
```

如果已经知道项目编码，要看项目详情：

```bash
python3 scripts/ai_api_skill_client.py projects get --project-code AI-PLATFORM
```

### 2. 检查 Agent 是否已注册

适用场景：

- 用户要让某个 Agent 接任务
- 你准备回写某个 Agent 的任务结果
- 你不确定 `agentCode` 是否已存在

只检查：

```bash
python3 scripts/ai_api_skill_client.py agents get --agent-code YOUR_AGENT
```

不存在就创建：

```bash
python3 scripts/ai_api_skill_client.py agents ensure \
  --project-code AI-PLATFORM \
  --agent-code YOUR_AGENT \
  --agent-name "Your Agent" \
  --agent-role DEVELOPER
```

### 3. 先按当前 Agent 自动拿可执行任务

适用场景：

- 用户说“现在轮到我做什么”
- 其他 AI 需要按自己的 `agentCode` 自动判断当前应执行的子模块
- 你希望按依赖顺序自动推进，不想自己手工判断哪个链路先做

优先命令：

```bash
python3 scripts/ai_api_skill_client.py agents next --agent-code YOUR_AGENT --claim
```

如果只是查看队列、不准备开始执行，可以去掉 `--claim`。一旦准备处理 `currentTask`，必须使用 `--claim`，让中台先标记子模块执行中。

如果 `currentTask.taskSource` 是 `REQUIREMENT_MODULE`，说明这是直接来自子模块表的任务，还没有开发链路。开始处理后需要先创建对应开发链路，再围绕该链路回写结果。

返回重点看：

- `currentTask`
- `readyTasks`
- `waitingTasks`
- `blockedTasks`
- `allTasks[*].executionOrder`

规则：

- 先执行 `currentTask`
- 如果 `currentTask` 为 `null` 但 `readyTasks` 非空，执行 `readyTasks[0]`
- 一个任务回写 `DONE` 后，立刻重新执行一次 `agents next`
- 只要还有新的 `readyTasks`，就继续顺序执行，不要停在 requirement inspection 的静态结果上

### 4. 查看某个 Agent 当前任务原始队列

适用场景：

- 用户说“看看当前任务”
- 用户说“这个 Agent 现在该做什么”
- 你要看未经排序的原始待办链路列表

命令：

```bash
python3 scripts/ai_api_skill_client.py agents tasks --agent-code YOUR_AGENT
```

默认优先使用 `agents next`，只有在你明确需要看原始队列时再用 `agents tasks`。

不要在这种场景下直接去创建子需求，先看现有任务。

### 5. 查看某个需求当前状态

适用场景：

- 用户给了 `requirementNo`
- 你要看当前需求是不是主需求、子需求、是否阻塞
- 用户说”查一下某某需求的任务”

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements get --requirement-no REQ-XXX
```

**查完需求后，必须紧接着查子需求列表**，看是否有子任务待执行：

```bash
python3 scripts/ai_api_skill_client.py requirements children \
  --master-requirement-no REQ-XXX
```

重点关注：

- `status` 为 `PENDING` 或 `DOING` 的子需求
- `mainAgentCode` 是否分配给当前 Agent
- `executionSteps` 里的具体执行内容

判断逻辑：

1. 如果有子需求且 `mainAgentCode` 是当前 Agent，这些就是你需要执行的子任务
2. 如果子需求状态是 `PENDING`，说明还没开始，应该优先执行
3. 如果没有子需求，再看链路任务和 inspection

注意：

- 不要把”查看 requirement 状态”当成”查看当前执行任务”
- requirement 还是 `PENDING`，并不代表当前 Agent 没有可做的链路
- 其他 AI 如果只查 requirement 或 inspection，容易看到”无链路/缺项”这种总览信息，却漏掉自己已经被分配的子执行模块
- 当用户说”查需求任务”时，子需求才是真正要执行的任务，不要只停留在主需求层面

如果用户说“修改子任务”或“删除子任务”，也先查子需求列表，再操作具体子需求。不要只看总需求 `inspection` 就下结论。

### 6. 判断是否需要协作，先看 inspection

适用场景：

- 用户问“这个任务还缺什么”
- 用户问“要不要拆给其他 AI”
- 用户问“为什么卡住”

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements inspection --requirement-no REQ-XXX
```

重点看：

- `missingItems`
- `incompleteLinks`
- `blockedLinks`
- `suggestedAgents`

inspection 是全局缺项检查，不是当前 Agent 的执行入口。当前 Agent 要先看 `agents next`。

### 7. 看某个需求下已经有哪些链路

适用场景：

- 你想知道 BACKEND/API/DOCS/DB 是否已经建过
- 你要更新某个 `linkId`
- 你准备新建链路前先避免重复

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements links --requirement-no REQ-XXX
```

### 8. 创建总需求

适用场景：

- 用户明确要新建一个主需求
- 当前工作还没有对应需求记录

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements create \
  --requirement-no REQ-20260507-M-001 \
  --project-code AI-PLATFORM \
  --title "实现多 Agent 协同" \
  --status PENDING \
  --requirement-type MASTER
```

### 9. 创建子需求

适用场景：

- inspection 表明当前需求太大，需要拆分
- 用户明确说“拆成几个子需求”
- 你准备把不同工作交给不同 Agent

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements child \
  --master-requirement-no REQ-20260507-M-001 \
  --requirement-no REQ-20260507-S-001 \
  --project-code AI-PLATFORM \
  --title "实现 API 链路" \
  --status PENDING \
  --requirement-type SUB
```

### 10. 创建链路任务

适用场景：

- 某个需求已经存在，但还没有对应的 BACKEND/API/DOCS/DB 链路
- 你要把具体执行工作分给某个 Agent

命令：

```bash
python3 scripts/ai_api_skill_client.py requirements link \
  --requirement-no REQ-XXX \
  --link-type API \
  --task-title "实现接口文档" \
  --agent-code api-agent \
  --depends-on-link-id 11 \
  --status TODO
```

如果任务需要严格执行顺序，必须补 `--depends-on-link-id`，否则 `agents next` 无法准确判断先后。

### 11. 创建或查询会话

适用场景：

- 用户提到沿用旧会话
- 用户要求复用某个历史会话
- 你要给需求绑定会话上下文

创建会话：

```bash
python3 scripts/ai_api_skill_client.py sessions create \
  --session-code SES-20260507-001 \
  --session-type CODEX_CLI \
  --project-code AI-PLATFORM \
  --agent-code codex-cli-agent \
  --client-code client-mac-mini-01
```

查询会话：

```bash
python3 scripts/ai_api_skill_client.py sessions list --project-code AI-PLATFORM
```

如果用户明确要求某个需求沿用指定会话，再更新会话偏好：

```bash
python3 scripts/ai_api_skill_client.py requirements session-preference \
  --requirement-no REQ-XXX \
  --session-strategy REUSE_SELECTED \
  --preferred-session-code SES-20260507-001
```

### 12. 回写链路结果

适用场景：

- 任务做完了，要提交 `DONE`
- 当前做不下去了，要提交 `BLOCKED`

安全回写时，必须带 `--requirement-no` 或 `--agent-code`，脚本会先读取当前链路，避免把已有字段清空。

```bash
python3 scripts/ai_api_skill_client.py links update \
  --link-id 12 \
  --requirement-no REQ-XXX \
  --status DONE \
  --result-summary "接口和文档已完成" \
  --execution-details "执行步骤、实际改动、验证过程和结果" \
  --deliverable-path "docs/api.md"
```

回写后必须立刻重新执行：

```bash
python3 scripts/ai_api_skill_client.py agents next --agent-code YOUR_AGENT
```

如果还有新的 `readyTasks`，继续执行，不要停在第一条任务。

如果你连链路上下文都没有，就不要急着更新，先查链路。

链路回写完成后，必须额外检查所属子任务状态是否已经同步：

```bash
python3 scripts/ai_api_skill_client.py requirements get --requirement-no REQ-XXX
```

如果后端还没有自动同步，至少要补一次：

```bash
python3 scripts/ai_api_skill_client.py requirements status \
  --requirement-no REQ-XXX \
  --status DONE
```

目标是避免出现“开发链路已经 DONE，但子任务还停留在 PENDING”的错误展示。

## 什么时候应该继续自己做

满足以下条件时，优先继续自己做，而不是拆出去：

- 当前任务就在你的能力范围内
- inspection 没有显示明显缺项
- 不需要额外专业 Agent 才能推进
- `agents next` 已经给出了 `currentTask` 或 `readyTasks`

这时应该做的是：

1. 先看 `agents next --claim`
2. 执行工作
3. 回写 `DONE`
4. 再次执行 `agents next`
5. 如果还有 ready task，继续执行下一条

## 什么时候应该拆给其他 Agent

满足以下条件时，优先考虑协作：

- `missingItems` 显示当前缺 API、DOCS、DB、TEST_RECORD 等你不负责的内容
- `suggestedAgents` 已经给出了更合适的 Agent
- 当前需求太大，不适合一个 Agent 独立完成
- 用户明确要求分工
- 当前任务需要会话复用或不同 Agent 交接

这时不要只写一句“请其他 AI 处理”，而要做结构化动作：

- 创建子需求
- 创建链路
- 创建会话
- 更新 session preference

## 什么时候不应该调用这些命令

下面这些情况不要直接打 API：

- 你还没分清用户说的是 Agent 任务还是需求链路
- 你还没先看当前 Agent 是否已经有 ready task
- 你不知道项目归属，却打算直接建需求
- 你不知道 `linkId` 的上下文，却想直接更新 progress
- 你只是猜测需要协作，但还没看 inspection

先查当前 Agent 队列，再决定要不要看 inspection。不要凭 requirement 总状态猜当前执行任务。

## 回写时必须遵守的规则

- `linkStatus` 用 `DOING`，不要用 `IN_PROGRESS`
- 子模块执行状态用 `IN_PROGRESS`；主动执行任务前必须通过 `agents next --claim` 或 `requirements status --status IN_PROGRESS` 先认领，避免重复执行
- 总需求负责目标和上下文，子模块负责具体执行；所有读取、认领、更新都走 API
- 回写前要清楚当前链路属于哪个需求或哪个 Agent
- `resultSummary` 只写简洁摘要，方便列表快速浏览
- `executionDetails` 必填，必须写详细执行回执，至少包含执行步骤、实际改动、验证过程、结果或阻塞原因
- 不要在工作目录创建 `receipt.md`、`receipt_*.md` 或其他任务回执文件；长回执优先用 `--execution-details-stdin` 直接从标准输入提交
- 链路完成后，要确保所属子任务状态也跟着更新，不允许出现链路 `DONE` 但子任务仍是 `PENDING`
- `deliverablePath` 要指向真实交付物
- 如果阻塞，就回写 `BLOCKED`，不要假装完成
- 一个任务 `DONE` 后，默认继续拉下一条 ready task，直到没有 ready task 为止

## 一句话决策表

- 不知道项目是谁：先 `projects list`
- 不知道 Agent 有没有注册：先 `agents get` 或 `agents ensure`
- 用户说“当前任务”：先 `agents next`
- 其他 AI 查不到自己该做什么：不要只查 requirement，先 `agents next`
- 用户说“还缺什么”：先 `requirements inspection`
- 要拆工作：建子需求或链路
- 要提交结果：`links update`
