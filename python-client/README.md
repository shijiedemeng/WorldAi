# AI API Python 客户端

这个目录是本机执行客户端。它负责在启动时把本机注册到后端的“在线客户端”列表，并上报本机可控制的 Agent 编号、工作目录和启动命令。页面上的“在线客户端”只展示运行态，配置来源就是这里的本地配置文件。

## 配置文件位置

默认读取当前目录下的配置文件：

```text
python-client/client-config.json
```

示例文件在：

```text
python-client/client-config.example.json
```

可以先复制一份：

```bash
cp python-client/client-config.example.json python-client/client-config.json
```

如果你要指定别的路径，也可以启动时传：

```bash
python3 -m ai_api_client --client-config /path/to/client-config.json --use-client-config
```

## 配置文件字段说明

```json
{
  "client_code": "local-macbook",
  "client_name": "本机客户端",
  "server": {
    "protocol": "http",
    "host": "127.0.0.1",
    "port": 8080,
    "api_base": ""
  },
  "app_version": "python-client",
  "agents": [
    {
      "agent_code": "ce-main-agent",
      "enabled": true,
      "workspace_dir": "/Users/zhaoyiming/Desktop/项目/ai-api",
      "worker_command": "codex"
    }
  ]
}
```

字段含义：

- `client_code`：客户端唯一编号，一台机器一个编号。
- `client_name`：页面上显示的客户端名称。
- `server.protocol`：后端协议，通常是 `http`。
- `server.host`：后端地址，例如 `127.0.0.1` 或服务器 IP。
- `server.port`：后端端口，例如 `8080`。
- `server.api_base`：接口前缀。当前后端代码会自动拼 `/api`，这里通常留空。
- `app_version`：客户端版本标识，页面展示用。
- `agents`：这台客户端可控制的 Agent 列表，一个客户端可以配置多个 Agent。
- `agents[].agent_code`：必须是后端 Agent 管理里已经存在的 Agent 编号；不存在的编号会被服务端忽略，不会关联。
- `agents[].enabled`：是否启用这个 Agent 关联。
- `agents[].workspace_dir`：该 Agent 执行任务时使用的工作目录。
- `agents[].worker_command`：该 Agent 的启动命令，例如 `codex`、`claude` 或自定义脚本。若该 Agent 走 ACP，会根据命令自动选择对应协议：
  - `qoder` / `qodercli` 会走 `qodercli --acp`
  - `codex` 会走 `codex app-server --listen stdio://`

## 常用命令

查看当前配置和配置文件路径：

```bash
PYTHONPATH=python-client python3 -m ai_api_client --show-client-config
```

按配置启动客户端，启动后会出现在页面“在线客户端”里：

```bash
PYTHONPATH=python-client python3 -m ai_api_client --use-client-config
```

只注册一次并打印服务端返回，不进入心跳循环：

```bash
PYTHONPATH=python-client python3 -m ai_api_client --register-client-node
```

初始化或更新配置文件基础信息：

```bash
PYTHONPATH=python-client python3 -m ai_api_client \
  --init-client-config \
  --client-code local-macbook \
  --client-name "本机客户端" \
  --base-url http://127.0.0.1:8080
```

往配置文件里追加或更新一个可控制 Agent：

```bash
PYTHONPATH=python-client python3 -m ai_api_client \
  --add-controllable-agent \
  --agent-code ce-main-agent \
  --workspace-dir /Users/zhaoyiming/Desktop/项目/ai-api \
  --worker-command codex
```

打印本机探测到的运行时：

```bash
PYTHONPATH=python-client python3 -m ai_api_client --print-runtimes
```

## 启动行为

执行 `--use-client-config` 后：

1. 读取 `python-client/client-config.json`。
2. 根据 `server.host` 和 `server.port` 拼出后端地址。
3. 探测本机安装的运行时，例如 Codex CLI、Qoder CLI、Claude Code。
4. 把客户端和 `agents` 列表上报到后端。
5. 后端只保留已经存在的 Agent 编号。
6. 客户端建立 `/ws/clients` WebSocket 连接并注册 `clientCode`，用于接收服务端主动下发的执行命令。
7. 客户端持续发送心跳；退出时会发送下线，页面列表会移除该客户端。
8. 客户端每轮心跳后也会拉取 pending 命令作为 WebSocket 兜底。

## 执行下发

需求详情页点击“执行”后，服务端会把命令下发给对应在线客户端和 Agent：

1. 优先通过 WebSocket `/ws/clients` 推送。
2. 客户端收到命令后，按 `agents[].workspace_dir` 进入工作目录。
3. 客户端先执行两个技能初始化：
   - `skills/project-markdown-workspace/scripts/project_markdown_workspace.py load`
   - `skills/ai-api-agent-integration/scripts/ai_api_skill_client.py config show`
4. 然后用 `agents[].worker_command` 执行 prompt，默认形式是：

```bash
<worker_command> -p "<prompt>"
```

5. 执行完成后，客户端回传结果；服务端会更新命令状态，并把关联链路回写为 `DONE` 或 `BLOCKED`。

如果当前会话类型是 `ACP`，则不会走上面的 `-p` 命令行执行，而是按对应 CLI 的 ACP 协议启动会话并发送 prompt。

## 扩展 CLI 探测

CLI 探测实现放在：

```text
python-client/ai_api_client/cli_tools/
```

每个 CLI 单独一个文件，例如 `codex.py`、`qoder.py`、`claude.py`。新增工具时，新建一个 Probe 类并在 `cli_tools/__init__.py` 的 `DEFAULT_CLI_PROBES` 里注册即可。旧入口 `discover_runtimes()` 保留不变，启动客户端和 `--print-runtimes` 都会走这套实现。
