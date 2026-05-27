---
name: project-markdown-workspace
description: 当用户要把 ai-api 项目 Markdown 配置同步、落盘、加载，或把本地 `md-files` 里的 Markdown 回传到服务端时，必须使用这个技能。典型触发包括：根据 `skill-config.json` 里匹配当前工作目录的 `agentRole` 默认查看“通用 + 当前角色”的 Markdown；同步/拉取 `AGENTS.md`、`SOUL.md`、`USER.md`、`MEMORY.md`、`.learnings/LEARNINGS.md` 或其他项目 Markdown 文件到 `md-files/common` 与 `md-files/<agentRole>`；初始化时加载该目录下全部 Markdown；拉取或更新后重新加载对应 Markdown；将本地新增或修改的 Markdown 上传回项目配置；按 `markdownSyncMode` 处理 `OVERWRITE` / `CANCEL`；排查 Markdown sync conflict。
---

# Project Markdown Workspace

## 这个技能解决的不是“查任务”，而是“把项目 Markdown 在本地 md-files 和服务端之间双向管理”

如果用户的真实意图是下面这些事，就应该用这个技能：

- “把项目 Markdown 同步到本地工作区”
- “先把 `AGENTS.md` / `SOUL.md` 拉下来”
- “执行任务前先同步项目上下文”
- “初始化时把本地 md-files 全部加载进来”
- “拉取后重新加载当前角色对应的 md”
- “把本地新增的 md 上传回服务端”
- “把开发角色目录里的 md 合并回项目 Markdown 配置”
- “看看为什么 Markdown sync 冲突了”
- “把服务端的 Markdown 配置物化成 md-files 目录”

如果用户要的是这些事，就不要把它混进别的技能流程里：

- 查询当前 Agent 该做什么
- 查看需求、子任务、链路、inspection
- 创建/更新 requirement、link、session
- 回写 `DONE` / `BLOCKED`

这类动作不属于本技能的职责。

## 默认入口

先执行：

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py config
```

这个脚本会优先读取：

- `skills/project-markdown-workspace/skill-config.json` 中匹配当前工作目录的 `projectCode`
- `skills/project-markdown-workspace/skill-config.json` 中匹配当前工作目录的 `agentRole`
- `skills/project-markdown-workspace/skill-config.json` 中匹配当前工作目录的 `url`

`skill-config.json` 必须按 `workspaces[].workspaceDir` 配置工作目录。运行时通过 `--workspace-dir`、`PROJECT_MARKDOWN_WORKSPACE_DIR` 或当前目录匹配；匹配不到会提示并终止，避免多个项目/多个 AI 共用错误配置。

典型配置示例：

```json
{
  "workspaces": [
    {
      "workspaceDir": "/Users/zhaoyiming/Desktop/项目/ai-api",
      "projectCode": "ai-api",
      "agentRole": "DEVELOPER",
      "url": "http://localhost:8080"
    }
  ]
}
```

真正的本地目录固定是 `工作目录/md-files/`，脚本会自动创建：

- `md-files/common`
- `md-files/<agentRole小写>`

## 初始化

先执行：

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py init
```

这个命令会默认加载：

- `md-files/common/**/*.md`
- `md-files/<agentRole小写>/**/*.md`

如果目录不存在，会自动创建。

## 最小工作流

### 路径 A：先看服务端 Markdown 配置

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py fetch
```

这里默认只看：

- 通用 Markdown
- 当前 `agentRole` 对应的 Markdown

如果要切项目或切环境，再显式覆盖：

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py fetch \
  --project-code AI-PLATFORM \
  --url http://localhost:8080
```

### 路径 B：同步到本地工作区

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py sync \
  --workspace-dir /absolute/path/to/workspace
```

同步时会自动写入：

- `工作目录/md-files/common/...`
- `工作目录/md-files/<agentRole小写>/...`

如果没传 `--workspace-dir`，脚本默认同步到当前工作目录下的 `md-files`。  
同步完成后，脚本会自动重新加载 `common + 当前角色` 下的全部 Markdown。

### 路径 C：只重载本地 md-files

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py load
```

### 路径 D：上传本地 md 到服务端

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py upload \
  --file .md
```

这个命令默认会：

- 从 `md-files/<agentRole小写>/cs2.md` 读取本地内容
- 拉取当前项目已有 Markdown 配置
- 按当前 `agentRole` 合并同路径文件
- 整体保存回服务端配置

如果要上传通用目录文件，就传：

```bash
python3 skills/project-markdown-workspace/scripts/project_markdown_workspace.py upload \
  --file common/xxx.md
```

## 你必须记住的路径规则

服务端返回的是项目级相对路径，但本技能本地落盘并不是直接写到工作区根，而是写到 `md-files` 下的作用域目录。

公共基础 Markdown：

- `AGENTS.md`
- `SOUL.md`
- `USER.md`
- `MEMORY.md`
- `.learnings/LEARNINGS.md`

角色作用域基础 Markdown 在服务端可能是：

- `roles/main/AGENTS.md`
- `roles/developer/SOUL.md`
- `roles/tester/USER.md`
- 其他同理

也就是说：

- 没有 `agentRole` 的文件，写到 `md-files/common/...`
- 有 `agentRole` 且角色等于当前配置角色的文件，写到 `md-files/<agentRole小写>/...`
- 其他角色的文件默认不拉取
- 如果服务端路径自带 `roles/<agentRole>/` 前缀，落盘时会去掉这个前缀，只保留文件本体路径
- `EXTRA` 文件也按同样规则进入 `common` 或当前角色目录

不要把其他角色的 Markdown 混到当前角色目录里。

## `CANCEL` 和 `OVERWRITE` 怎么理解

- `OVERWRITE`：本地有差异也直接覆盖
- `CANCEL`：只要本地内容与服务端不同，就把该文件记为冲突并中止本次同步

如果脚本返回 `MarkdownSyncConflict`：

1. 先报告冲突文件
2. 不要继续假装同步成功
3. 不要私自覆盖本地文件
4. 让用户决定是改服务端策略、手动合并，还是指定新的工作区

## 常见错误

错误做法：

1. 用户说“同步项目 Markdown”
2. AI 只执行配置查询
3. 把 JSON 贴出来
4. 却没有真正把文件写进本地 `md-files`
5. 也没有重新加载本地 Markdown

这不算完成任务。

正确做法是：

1. 看配置
2. 确认 `agentRole`
3. 必要时确认 `projectCode / url`
4. 按需求执行 `sync`、`load` 或 `upload`
5. 明确返回写入了哪些文件、跳过了哪些文件、哪些文件冲突、重载了哪些文件，或者上传了哪个文件

## 和其他工作的配合顺序

如果用户同时要：

- 先同步项目 Markdown
- 再执行 ai-api 任务

顺序应是：

1. 先用本技能把 Markdown 落盘到 workspace
2. 再执行后续开发、查询或其他协作动作

不要反过来。
