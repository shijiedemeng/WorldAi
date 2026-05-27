---
name: project-development-documents
description: 当需要在会话初始化时直接读取 ai-api 项目文档库里的“通用 + Agent角色”规范上下文时使用。本技能处理 ProjectDocumentUsage=AGENT_COMMON、AGENT_MAIN、AGENT_DEVELOPER、AGENT_TESTER、AGENT_OPS、AGENT_REVIEWER；只通过接口读取并输出上下文，不同步、不写入本地文件。不处理角色文件列表、AGENTS/SOUL/USER/MEMORY/.learnings 等基础 Markdown，那些由 project-markdown-workspace 技能负责。
---

# Project Development Documents

这个技能只负责“项目文档库关联 / 通用文档 / Agent角色文档”这一类规范。

不要把它和“角色文件列表”混用：

- 通用文档：来自项目文档库关联，usage 是 `AGENT_COMMON`，用于所有 Agent 都需要遵守的项目通用规范。
- 角色文档：来自项目文档库关联，usage 按 Agent 角色选择 `AGENT_MAIN`、`AGENT_DEVELOPER`、`AGENT_TESTER`、`AGENT_OPS`、`AGENT_REVIEWER`。
- 角色文件列表：来自项目 Markdown 配置里的 `markdownFiles`，按 `COMMON` 和 Agent 角色落到 `md-files`，由 `project-markdown-workspace` 技能控制。

## 常用命令

在当前技能目录下执行脚本，路径只使用 `scripts/...`，不要写上级目录或机器相关路径。

查看当前配置：

```bash
python3 scripts/project_development_docs.py config
```

直接读取通用文档和当前角色文档上下文：

```bash
python3 scripts/project_development_docs.py fetch
```

等价的上下文命令：

```bash
python3 scripts/project_development_docs.py context
```

## URL 获取

如果外部 Agent 只拿到了完整 URL，也可以直接指定：

```bash
python3 scripts/project_development_docs.py fetch \
  --context-url http://localhost:8080/api/projects/PROJECT/document-context/AGENT_DEVELOPER
```

## 行为约束

- 本技能只读取服务端文档上下文并返回给调用方。
- 不创建 `.ai-api-context`，不写入 `development-documents.md`，不把规范同步到业务工作目录。
- 选择目录作为通用文档或角色文档关联时，服务端会自动展开目录下全部子文档。
- 本技能不上传、不修改项目文档库。
