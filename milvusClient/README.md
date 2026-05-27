# Milvus Lite 向量服务

这是项目储备库的本地向量服务，默认使用 Milvus Lite 文件库，不依赖 Docker。

启动方式：

```bash
pip install -r milvusClient/requirements.txt
python3 milvusClient/app.py
```

本地 `.db` 文件模式依赖 `milvus-lite`，已通过 `pymilvus[milvus_lite]` 声明在 `requirements.txt` 中。`milvus-lite 2.5.1` 仍会导入 `pkg_resources`，因此 requirements 同时限制 `setuptools<81`。如果不使用本地文件库，而是连接独立 Milvus 服务，启动时通过 `--uri` 或 `MILVUS_URI` 指向服务地址即可。

默认配置：

- 服务地址：`http://127.0.0.1:8091`
- 本地 DB：`state/milvus-lite/ai_api.db`
- 普通 HTTP API：`/vectors/upsert`、`/vectors/search`、`/vectors/delete`
- MCP SSE 地址：`http://127.0.0.1:8091/mcp/sse`
- MCP 消息地址：服务端会在 SSE `endpoint` 事件里返回 `/mcp/messages?sessionId=...`
- MCP 工具列表：`http://127.0.0.1:8091/mcp/tools`

后续切换 Docker Milvus 时，只需要修改 `MILVUS_URI` 指向 Milvus 服务地址。

Java 后端启动配置示例：

```yaml
ai:
  knowledge:
    vector-base-url: http://127.0.0.1:8091
```

项目隔离不在页面配置，也不在 Java 后端配置 collection 策略。Java 只配置向量服务 URL；查询、写入和删除时必须带项目列表里的 `projectCode`。milvusClient 内部按 `projectCode` 隔离数据，Milvus Lite 的 DB 文件路径属于 milvusClient 服务自身配置，通过启动参数 `--uri` 或环境变量 `MILVUS_URI` 设置，Java 不控制底层 DB。

MCP SSE 当前提供三个工具：

- `milvus_vector_search`：按向量检索集合。
- `milvus_vector_upsert`：写入或更新向量。
- `milvus_vector_delete`：删除向量。
