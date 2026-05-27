> One developer. Multiple machines. Coordinated AI execution.
> 一个开发者，多台机器，统一协同的 AI 执行系统。

# World

[中文说明](README_CN.md) | [English README](README_EN.md)

⭐ World AI

World AI 是一个分布式 AI 编排系统，用于跨多台机器统一调度本地编码智能体（Agent），实现从需求到实现的结构化自动化开发流程。

它允许单个开发者像“控制一个团队”一样，统一管理运行在不同机器上的 AI 编码执行单元，并将执行结果持续沉淀为可复用的项目知识。

⸻

🧠 核心能力

* 多智能体协同调度（跨机器 / 本地执行）
* 结构化工作流：需求 → 拆解 → 任务 → 执行 → 反馈
* 项目规范与文档动态注入 Agent 上下文
* 执行结果自动沉淀为项目知识库（支持复用与检索）
* 中央调度系统统一管理分布式 AI 编码节点

⸻

⚙️ 系统架构

🖥 前端（Vue）

* 项目管理
* 工作流可视化
* Agent 状态监控
* 执行日志展示

🧠 后端（Java / Spring Boot）

* 工作流编排中心
* Agent 调度系统
* 任务状态管理
* API 与 WebSocket 通信

🤖 本地执行端（Python Client）

* 运行在本机或多台机器
* 接收任务并调用本地工具（Codex / Qoder / CLI）
* 回传执行结果

📦 知识库（Milvus）

* 存储项目执行经验
* 支持语义检索
* 用于增强后续任务执行

⸻

🚀 一句话总结

一个开发者，多台机器，统一协同的 AI 编排与执行系统。

⸻

⭐ World AI (English)

World AI is a distributed AI orchestration system for coordinating multiple local coding agents across machines.

It enables a single developer to orchestrate multi-agent workflows, execute structured development pipelines, and build a continuous knowledge feedback loop from real execution results.

⸻

🧠 Key Capabilities

* Multi-agent orchestration across local machines
* Structured workflow: Requirement → Task → Execution → Feedback
* Dynamic injection of project standards and documentation into agent context
* Continuous knowledge base built from execution results
* Centralized control of distributed AI coding workers

⸻

⚙️ System Overview

* Frontend (Vue): Project and workflow management UI
* Backend (Java Spring Boot): Orchestration engine and task scheduling
* Agent Runtime (Python): Local execution of coding agents (Codex / Qoder / CLI)
* Vector Store (Milvus): Project knowledge storage and retrieval

⸻

💡 One-line Summary

One developer, multiple machines, coordinated AI execution system.

## 📬 Feedback & Contact

第一次发布作品，仍在持续完善。
如果部署或使用过程中遇到问题，欢迎联系：
- Email: 1012078632@qq.com
- QQ: 1012078632
- GitHub Issues（推荐）
- 
Issues / Pull Requests / Suggestions are welcome.