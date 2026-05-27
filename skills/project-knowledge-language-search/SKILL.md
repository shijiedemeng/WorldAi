---
name: project-knowledge-language-search
description: 当需要查询 ai-api 项目储备库、根据自然语言在向量库里查相似知识、获取项目编码和项目名称列表、或在 Agent 会话中通过 MCP/SSE 使用项目知识库时使用。这个技能不负责写入知识，只负责项目列表和语言搜索。
---

# Project Knowledge Language Search

## 能力边界

这个技能用于读取 ai-api 项目储备库：

- 查询项目编码和项目名称列表。
- 按自然语言查询项目储备知识向量库。
- 返回综合匹配度、最佳分片匹配度、命中分片数和最佳匹配内容。

不要用这个技能创建、修改、删除项目储备知识。

## 调用方式

这个技能支持两种调用方式：

- MCP SSE：适合支持 SSE MCP 的 Agent 会话。
- HTTP/REST：适合不支持 SSE、SSE 被代理阻断、或只允许普通 HTTP 请求的环境。

优先使用 MCP SSE；如果当前会话不能稳定使用 SSE，直接改用 HTTP/REST 辅助脚本。

## MCP SSE 配置

在支持 MCP SSE 的会话里注入：

```json
{
  "mcpServers": {
    "ai-api-project-knowledge": {
      "type": "sse",
      "url": "http://127.0.0.1:8080/api/mcp/project-knowledge/sse"
    }
  }
}
```

如果后端不是本机端口，只需要修改 `skill-config.json` 里的 `url`。

`skill-config.json` 只保留一个配置：

```json
{
  "url": "http://127.0.0.1:8080"
}
```

这里不配置项目编码。项目编码必须通过 `project_knowledge_projects` 或 REST 辅助脚本的 `projects` 命令查询。

## HTTP/REST 接口

如果 SSE 不可用，直接使用普通 HTTP 接口：

- 项目列表：`GET /api/mcp/project-knowledge/projects`
- 工具列表：`GET /api/mcp/project-knowledge/tools`
- 语言搜索：`POST /api/mcp/project-knowledge/search`

语言搜索请求体示例：

```json
{
  "projectCode": "cs",
  "query": "并行工作流暂停后如何恢复执行",
  "knowledgeType": "COMMON_ISSUE",
  "limit": 5,
  "minMatchScore": 70
}
```

`embeddingSettingKey` 可不传，后端会使用第一个启用的向量模型。

## MCP 工具

### `project_knowledge_projects`

获取项目编码和名称列表。项目编码不明确时必须先调用这个工具。

参数：

- `keyword`：可选，按项目编码、项目名称、项目描述过滤。
- `limit`：可选，返回数量，默认 50，最大 100。

### `project_knowledge_language_search`

按自然语言搜索项目储备库。

参数：

- `projectCode`：必填，项目编码。
- `query`：必填，自然语言搜索内容。优先从当前技能会话、用户问题、任务描述、报错内容里提取。
- `knowledgeType`：可选，`COMMON_ISSUE` 或 `PROCESS_GUIDE`。
- `embeddingSettingKey`：可选，向量模型配置键。不传时后端会使用第一个启用的向量模型。
- `limit`：可选，返回列表数量，默认 5，最大 20。
- `minMatchScore`：可选，最低综合匹配值，支持 `0-100` 或 `0-1`，例如 `75` 或 `0.75`。

返回重点字段：

- `matchScore`：综合匹配度，百分制。
- `bestChunkScore`：最佳分片匹配度，百分制。
- `matchedChunkCount`：命中分片数量。
- `content`：最佳匹配分片内容。

## 使用流程

1. 如果用户没给项目编码，先调用 `project_knowledge_projects`。
2. 从会话里提取搜索文本作为 `query`。
3. 按用户要求设置 `limit` 和 `minMatchScore`；如果没说，默认 `limit=5`，不设置最低匹配值。
4. 调用 `project_knowledge_language_search`。
5. 回答时优先给匹配度最高的结果，并说明综合匹配度。

## REST 辅助命令

如果当前 Agent 不能直接调用 MCP/SSE，可以用脚本走 HTTP/REST：

```bash
python3 scripts/project_knowledge_search.py projects
```

```bash
python3 scripts/project_knowledge_search.py search \
  --project-code cs \
  --query "并行工作流暂停后如何恢复执行" \
  --limit 5 \
  --min-match-score 70
```
