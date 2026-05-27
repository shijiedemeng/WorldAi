# World

World is an AI orchestration platform for software project collaboration. It connects a Vue frontend, a Spring Boot backend, a local Python execution client, and a vector knowledge service to manage projects, agents, requirement decomposition, workflow execution, defect analysis, AI audit logs, and reusable project knowledge.

It is designed for teams that want to coordinate local coding agents, inject project-specific standards and documentation into agent sessions, track execution results, and build a searchable knowledge base from real delivery feedback.

## Architecture

```text
Vue Frontend
  |
  | HTTP API / WebSocket
  v
Java Backend
  |
  | WebSocket commands / heartbeat / execution feedback
  v
Python Client
  |
  | Local Codex / Qoder / Claude / MCP / filesystem
  v
Local Agent Workspace

Java Backend
  |
  | HTTP API
  v
Vector Service
  |
  | Milvus Lite / Milvus
  v
Project Knowledge Vector Store
```

Core responsibilities:

- Vue frontend: provides project, agent, requirement, workflow, defect, log, and knowledge-base operation pages.
- Java backend: acts as the orchestration center, stores business data, dispatches clients, calls external AI APIs, synchronizes status, and talks to the vector service.
- Python client: runs on the machine that owns the real workspace, registers local agents, receives backend commands, executes local CLI/ACP/MCP tools, and reports results.
- Vector service: writes, searches, and deletes project knowledge vectors. Milvus Lite is used by default and can be replaced by a standalone Milvus service.

## Highlights

- Multi-agent orchestration: bind different agents to projects, requirements, and modules, then dispatch them through the backend.
- Workflow execution: decompose requirements into module nodes and automatically determine executable tasks by dependency, review status, and session availability.
- Local execution loop: run tasks in the real workspace through Codex, Qoder, Claude, or custom CLI commands, then send execution details back to the backend.
- Dynamic context injection: inject coding standards, API documents, design notes, and project knowledge by project, requirement, agent role, and execution scenario.
- AI audit trail: record AI requests, responses, errors, and business results for requirement analysis, defect analysis, and knowledge organization.
- Project knowledge base: organize delivery feedback into reusable knowledge, store it by project, and retrieve it in later agent sessions.
- Real-time status sync: collect client heartbeat, task execution, session events, and workflow progress in the backend, then push updates to the frontend.
- Replaceable infrastructure: use Milvus Lite locally or switch to standalone Milvus; customize agent execution commands for different local environments.

## Screenshots

### Main UI

![Main UI](public/%E4%B8%BB%E7%95%8C%E9%9D%A2.png)

### Task Orchestration

![Task Orchestration](public/%E4%BB%BB%E5%8A%A1%E7%BC%96%E6%8E%92.png)

### Task Dispatch

![Task Dispatch 1](public/%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6-1.png)

![Task Dispatch 2](public/%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6-2.png)

![Task Dispatch 3](public/%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6-3.png)

## Tech Stack

- Backend: Java 17, Spring Boot 3.3, Spring Data JPA, Flyway, MySQL, WebSocket
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Pinia, Vue Router
- Local client: Python 3.10+
- Vector service: Python, FastAPI, Milvus Lite / Milvus

## Project Structure

```text
.
├── src/                     # Java backend source and Flyway migrations
├── frontend/                # Vue frontend
├── python-client/           # Local agent execution client
├── milvusClient/            # Vector service
├── public/                  # README screenshots
└── skills/                  # Standard skills used by agents
```

## Prerequisites

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Python 3.10+
- MySQL 8.x

Create the database:

```sql
CREATE DATABASE `ai-api` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

The backend runs Flyway migrations from `src/main/resources/db/migration` during startup.

## Backend Deployment

The backend listens on port `8080` by default. Configure the database and vector service through environment variables:

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/ai-api?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DB_USERNAME='root'
export DB_PASSWORD='your-database-password'
export SERVER_PORT=8080
export AI_KNOWLEDGE_VECTOR_BASE_URL='http://127.0.0.1:8091'
```

Development startup:

```bash
mvn spring-boot:run
```

Production build:

```bash
mvn clean package
java -jar target/ai-api-0.0.1-SNAPSHOT.jar
```

OpenAPI UI:

```text
http://127.0.0.1:8080/swagger-ui.html
```

## Vector Service Deployment

The vector service listens on `8091` by default and uses `state/milvus-lite/ai_api.db` as the local Milvus Lite data file.

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r milvusClient/requirements.txt
python3 milvusClient/app.py
```

To connect to a standalone Milvus service:

```bash
export MILVUS_URI='http://127.0.0.1:19530'
python3 milvusClient/app.py
```

## Frontend Deployment

Development startup:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server is available at:

```text
http://127.0.0.1:5173
```

Development proxy rules are configured in `frontend/vite.config.ts`:

- `/api` -> `http://localhost:8080`
- `/ws` -> `ws://localhost:8080`

Production build:

```bash
cd frontend
npm install
npm run build
```

The output is generated in `frontend/dist`. Serve it with Nginx or another static server and proxy `/api` and `/ws` to the Java backend.

## Python Client Deployment

The Python client runs agent tasks in the local workspace.

Copy the example config:

```bash
cp python-client/client-config.example.json python-client/client-config.json
```

Edit `python-client/client-config.json`:

- `server.host` and `server.port` must point to the Java backend.
- `agents[].agent_code` must exist in backend Agent management.
- `agents[].workspace_dir` must point to the real local workspace.
- `agents[].worker_command` must be a runnable local command, such as `codex`, `qoder`, or a custom command.

Start the client:

```bash
PYTHONPATH=python-client python3 -m ai_api_client --use-client-config
```

After startup, the client appears in the frontend online-client and agent pages.

## Standard Skills

The project includes four standard skills under `skills/`. They connect agent sessions with ai-api backend workflows, project standards, Markdown context, and the project knowledge base.

| Skill | Purpose | Typical Use Case |
| --- | --- | --- |
| `ai-api-agent-integration` | ai-api collaboration integration | Query projects, agents, requirements, modules, links, sessions, and write back execution results |
| `project-markdown-workspace` | Project Markdown workspace sync | Sync server-side Markdown configuration to local `md-files`, or upload local Markdown back to the server |
| `project-development-documents` | Development document context | Dynamically read common, development, testing, operations, and review standards by project and agent role |
| `project-knowledge-language-search` | Project knowledge search | Search reusable project knowledge and similar experience through MCP SSE or HTTP |

## Key Skill Configuration

`ai-api-agent-integration` connects task collaboration. Key fields are workspace, project, agent, role, engine, and backend address.

Config file: `skills/ai-api-agent-integration/skill-config.json`

```json
{
  "workspaces": [
    {
      "workspaceDir": "/absolute/path/to/workspace",
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

`project-markdown-workspace` syncs server-side Markdown configuration to the local workspace.

Config file: `skills/project-markdown-workspace/skill-config.json`

```json
{
  "workspaces": [
    {
      "workspaceDir": "/absolute/path/to/workspace",
      "projectCode": "ai-api",
      "agentRole": "DEVELOPER",
      "url": "http://localhost:8080"
    }
  ]
}
```

`project-development-documents` reads standards and development document context by project and agent role.

Config file: `skills/project-development-documents/skill-config.json`

```json
{
  "workspaces": [
    {
      "workspaceDir": "/absolute/path/to/workspace",
      "projectCode": "ai-api",
      "agentRole": "DEVELOPER",
      "url": "http://localhost:8080"
    }
  ]
}
```

`project-knowledge-language-search` searches project knowledge.

Config file: `skills/project-knowledge-language-search/skill-config.json`

```json
{
  "url": "http://127.0.0.1:8080"
}
```

If the agent supports MCP SSE, add the project knowledge MCP server:

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

Configuration field reference:

| Field | Applies To | Meaning |
| --- | --- | --- |
| `workspaces` | First three skills | Workspace configuration list. A skill can configure multiple workspaces and select one by the current directory. |
| `workspaceDir` | First three skills | Absolute path of the local agent workspace. |
| `projectCode` | First three skills | Project code in the backend project management page. |
| `agentCode` | `ai-api-agent-integration` | Agent code in the backend Agent management page. |
| `agentName` | `ai-api-agent-integration` | Display name used when initializing or aligning agent metadata. |
| `agentEngineType` | `ai-api-agent-integration` | Agent engine type. Supported values: `CODEX`, `QODER`, `CLAUDE`. |
| `agentRole` | First three skills | Agent role. Supported values: `MAIN`, `DEVELOPER`, `TESTER`, `OPS`, `REVIEWER`. |
| `baseUrl` | `ai-api-agent-integration` | Java backend address, for example `http://localhost:8080`. |
| `url` | Other three skills | Java backend address, for example `http://localhost:8080` or `http://127.0.0.1:8080`. |
| `mcpServers.ai-api-project-knowledge.type` | MCP config | MCP transport type. Current value: `sse`. |
| `mcpServers.ai-api-project-knowledge.url` | MCP config | Project knowledge MCP SSE endpoint: `{backend-url}/api/mcp/project-knowledge/sse`. |

## Startup Order

1. Start MySQL.
2. Start the vector service: `python3 milvusClient/app.py`.
3. Start the Java backend: `mvn spring-boot:run`.
4. Start the Vue frontend: `cd frontend && npm run dev`.
5. Start the Python client: `PYTHONPATH=python-client python3 -m ai_api_client --use-client-config`.
6. Configure projects, agents, and AI model settings in the frontend, then create requirements and dispatch tasks.

## Environment Variables

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Java backend port |
| `DB_URL` | Local `ai-api` MySQL | JDBC database URL |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | Empty | Database password |
| `AI_KNOWLEDGE_VECTOR_BASE_URL` | `http://127.0.0.1:8091` | Vector service address |
| `AI_IMAGE_STORAGE_DIR` | `./state/ai-images` | Image generation file storage directory |
| `AI_IMAGE_RESPONSE_STORAGE_DIR` | `./state/ai-image-responses` | Image generation response record directory |

AI model API keys, image model settings, and defect platform accounts should be maintained in the system pages rather than committed to the repository.

## Git Notes

The repository ignores local and generated files such as:

- `target/`
- `frontend/node_modules/`
- `frontend/dist/`
- `state/`
- Python `__pycache__` and virtual environments
- `python-client/client-config.json`
- IDE and local agent configuration
