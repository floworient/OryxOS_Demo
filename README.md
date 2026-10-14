<a id="top"></a>

<div align="center">
  <img src="docs/images/oryxos-logo.svg" alt="OryxOS logo" width="160">
  <h1>OryxOS</h1>
  <p><strong>Java 原生的企业级 Agent OS</strong></p>
  <p>在企业自己的基础设施中，统一运行、连接和治理业务 Agent。</p>
  <p>
    <a href="#status"><img alt="Project status: bootstrap" src="https://img.shields.io/badge/status-bootstrap-f59e0b"></a>
    <a href="#technology"><img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&amp;logoColor=white"></a>
    <a href="#technology"><img alt="Spring Boot 3.5" src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&amp;logoColor=white"></a>
    <a href="#build"><img alt="Maven multi-module build" src="https://img.shields.io/badge/Maven-14_modules-C71A36?logo=apachemaven&amp;logoColor=white"></a>
  </p>
  <p>
    <a href="#overview">项目介绍</a> ·
    <a href="#features">核心能力</a> ·
    <a href="#agent-definition">定义 Agent</a> ·
    <a href="#architecture">架构</a> ·
    <a href="#modules">模块</a> ·
    <a href="#roadmap">路线图</a> ·
    <a href="#documentation">文档</a> ·
    <a href="#contributing">参与贡献</a>
  </p>
</div>

> [!IMPORTANT]
> OryxOS 目前处于工程引导阶段。Maven 14 模块骨架已经建立并可用于编译打包，Provider、ReAct、Memory、Tool 等运行时能力仍在建设中，当前没有可安装的正式版本。

<a id="overview"></a>

## 项目介绍

OryxOS 是一个面向企业私有化部署的 Java Agent OS。它为多个业务 Agent 提供统一的模型接入、ReAct 执行、记忆、工具、渠道、调度、Web API 和审计能力。业务方通过一个目录定义 Agent，无需为每个场景重复开发一套 Agent 后端。

OryxOS 计划部署在企业自己的服务器或 Kubernetes 集群中，适合以下场景：

- 数据、凭证和执行记录需要留在企业内部。
- Agent 需要接入现有 ERP、CRM、CMDB、知识库和监控系统。
- 企业希望复用 Java/Spring 运维、监控和安全体系。
- 多个 Agent 需要共享模型、工具、渠道、记忆和审计基础设施。

OryxOS 位于 Spring AI 等开发框架之上。它不是可视化工作流平台，也不是托管式 Agent SaaS；它交付的是一个可以由企业掌控的 Agent 运行底座。

<a id="features"></a>

## 核心能力

| 能力 | 解决的问题 | 核心设计 |
| --- | --- | --- |
| **LLM Provider** | 如何切换云端或本地模型 | 统一接口、显式 Provider 路由、Agent 与供应商解耦 |
| **ReAct Loop** | Agent 如何自主完成多步骤任务 | 运行时控制思考、执行、观察和终止，不依赖框架自动执行 Tool |
| **Memory** | 如何跨会话保存状态和偏好 | 会话记忆、长期记忆和可扩展的检索后端 |
| **Tool** | Agent 如何操作真实系统 | 内置 Tool、MCP Server、Java `@Tool` 和统一审计 |
| **Web Service** | 现有业务系统如何调用 Agent | REST API、统一响应、Session 与系统状态接口 |

CLI、企业 IM 和定时任务作为不同触发入口，最终复用同一条 `AgentService` 执行链路。

<a id="agent-definition"></a>

## 定义 Agent

一个 `.oryxos/agents/<name>/` 目录就是一个业务 Agent。`AGENT.md` 的 YAML frontmatter 描述运行配置，Markdown 正文描述 Agent 的职责和工作方式。

```text
.oryxos/
├── agents/
│   └── daily-weather/
│       ├── AGENT.md
│       ├── skills/
│       │   └── digest-format -> ../../../skills/digest-format
│       ├── scripts/
│       └── REFERENCE.md
├── skills/
│   └── digest-format/
│       └── SKILL.md
├── MEMORY.md
└── oryxos.db
```

示例 `AGENT.md`：

```markdown
---
name: daily-weather
provider: deepseek
tools:
  - http_get
  - notify
schedules:
  - key: morning-weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: 查询今天的天气，生成穿搭建议并发送通知。
---

# 每日天气助手

查询指定城市当天的天气，根据温度、降水和风力生成简洁的穿搭建议，
然后发送到配置的通知渠道。
```

公共 Skill 位于 `.oryxos/skills/<name>/`。Agent 通过自身 `skills/` 下的受控相对软连接选择可见 Skill。运行时只预载 Skill 的名称、描述和路径，正文与附属资源由 Agent 按需读取。

<a id="architecture"></a>

## 架构

![OryxOS 架构图](docs/images/oryxos-architecture.svg)

架构遵循以下原则：

- Spring AI Alibaba 负责模型协议适配和 Tool Schema 生成，ReAct 循环由 OryxOS 实现。
- Spring AI 自动工具执行保持关闭，所有工具由 `ToolExecutor` 统一调度和审计。
- Agent 目录是运行定义和上下文来源，不是可执行 Tool，也不会被解析成固定工作流。
- CLI、HTTP、企业 IM 和定时任务复用 `AgentService`，入口层只负责协议与消息适配。
- 核心阶段的 Sandbox 是应用层白名单；容器或 MicroVM 级隔离属于后续能力。

详细的模块边界、数据模型与调用流程参见[技术方案](docs/TechnicalSolution.md)。

<a id="modules"></a>

## 模块结构

根目录的 Maven 聚合工程包含以下 14 个模块：

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 核心契约、Agent 上下文和运行时编排 |
| `oryxos-provider` | 模型接入与 Provider 路由 |
| `oryxos-memory` | 会话记忆与长期记忆门面 |
| `oryxos-tool` | 工具注册、执行、MCP 与 Sandbox |
| `oryxos-storage` | 数据源、持久化实现与数据库迁移 |
| `oryxos-web` | REST API、统一响应与 OpenAPI |
| `oryxos-channel-cli` | 终端消息渠道适配 |
| `oryxos-cli` | Picocli 命令入口 |
| `oryxos-boot` | Spring Boot 启动与依赖装配 |
| `oryxos-persona` | 内置及自定义人格模板 |
| `oryxos-knowledge` | 知识解析、索引与检索 |
| `oryxos-channel-feishu` | 飞书渠道适配 |
| `oryxos-channel-wecom` | 企业微信渠道适配 |
| `oryxos-channel-dingtalk` | 钉钉渠道适配 |

<a id="technology"></a>

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 语言与运行时 | Java 21 |
| 应用框架 | Spring Boot 3.5、Spring MVC、Virtual Threads |
| LLM 接入 | Spring AI Alibaba |
| 持久化 | SQLite、PostgreSQL、Spring Data JPA、Flyway |
| 命令行 | Picocli |
| 工具协议 | MCP、Spring AI Tool Schema |
| API 文档 | OpenAPI 3、springdoc-openapi |
| 构建系统 | Maven 多模块 |

具体版本和模块依赖关系以根目录 `pom.xml` 及各模块 POM 为准。

<a id="status"></a>

## 项目状态

- [x] Agent OS 行业调研与项目定位
- [x] 核心需求、非功能需求和验收标准
- [x] 技术选型、模块边界和关键流程设计
- [x] AI 辅助开发实施指南
- [x] Maven 14 模块工程骨架、模块依赖和启动占位代码
- [x] Java 21 与 Maven 构建基线
- [ ] 核心运行时契约与实现
- [ ] 首个可运行的 Provider + ReAct + CLI 链路
- [ ] 可供试用的预览版本

> [!NOTE]
> 当前代码用于确立工程结构和依赖边界。模块中的占位类不代表对应业务能力已经完成，公开 API、配置和命令仍可能在首个预览版本前调整。

<a id="build"></a>

## 构建

环境要求：

- JDK 21
- Maven 3.6.3 或更高版本

在项目根目录使用 Maven Wrapper 执行（Windows 使用 `mvnw.cmd`）：

```bash
./mvnw clean package
```

该命令编译并打包 Maven 聚合工程中的 14 个模块。打包完成后，可直接运行 CLI 并查看版本：

```bash
java -jar oryxos-cli/target/oryxos-cli-0.1.0-SNAPSHOT-exec.jar
java -jar oryxos-cli/target/oryxos-cli-0.1.0-SNAPSHOT-exec.jar --version
```

启动 Spring Boot 聚合应用：

```bash
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar
```

启动完成后访问 [http://localhost:8080](http://localhost:8080) 查看 OryxOS 项目首页，API 文档位于 [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)。

当前 CLI 的业务子命令和完整 Web 运行配置尚未实现，因此 `oryxos init`、`oryxos chat` 等最终用户命令暂不作为可用功能提供。

<a id="roadmap"></a>

## 路线图

- [x] **Project Bootstrap**：建立 Maven 14 模块工程、依赖关系和 Java 21 构建基线。
- [ ] **Foundation**：实现核心契约和 Provider 抽象，跑通模型调用。
- [ ] **Agent Runtime**：实现 ReAct Loop、`ToolExecutor`、CLI 和基础 Session。
- [ ] **Memory & Tools**：实现长期记忆、内置 Tool、MCP Client 和应用层 Sandbox。
- [ ] **Web API**：提供 REST API、OpenAPI 和统一错误响应。
- [ ] **Persistence & Audit**：完成 SQLite 持久化、Flyway 迁移和调用审计。
- [ ] **Multi-Agent Runtime**：支持多 Agent、动态调度和端到端示例。
- [ ] **Enterprise Integration**：增加知识库、人格和飞书、企业微信、钉钉渠道。
- [ ] **Governance**：完善 Agent 生命周期、向量检索、SSO、多租户和 Tool Policy。

近期端到端验收场景包括每日天气、每日科技日报和每日 GitHub 日报，用于验证定时触发、Tool、Skill 渐进加载、长期记忆、通知和审计链路。

<a id="documentation"></a>

## 文档

| 文档 | 内容 |
| --- | --- |
| [行业调研](docs/IndustryResearch.md) | Agent OS 业界格局、Java 生态缺位与项目定位 |
| [需求分析](docs/DemandAnalysis.md) | 功能范围、非功能需求、数据模型和验收标准 |
| [技术方案](docs/TechnicalSolution.md) | 架构决策、模块设计、关键流程和工程结构 |
| [AI 编程指南](docs/AiProgrammingGuide.md) | 规范驱动的开发顺序和任务拆解 |
| [编码 Agent 指南](AGENTS.md) | 在本仓库执行开发任务时遵守的约束 |

文档之间出现版本差异时，产品目标以需求分析为依据，架构与明确修订的实现规则以技术方案中的最新内容为准。

<a id="contributing"></a>

## 参与贡献

OryxOS 正处于早期阶段，欢迎参与需求校对、架构讨论、接口设计、测试用例和核心模块实现。

提交贡献前请：

1. 阅读[需求分析](docs/DemandAnalysis.md)和[技术方案](docs/TechnicalSolution.md)。
2. 较大的功能先明确目标、范围和验收方式，避免实现与路线图偏离。
3. 保持改动聚焦，并为行为变化补充必要的测试和文档。
4. 确保示例不包含真实 API Key、Webhook、内部地址或业务数据。

正式的 `CONTRIBUTING.md`、Issue 模板和 Pull Request 模板将在后续补充。

<a id="security"></a>

## 安全

OryxOS 将执行模型发起的工具调用。部署时必须谨慎配置工具可见范围、文件路径、命令和网络白名单。通用 Shell 或解释器拥有 OryxOS 进程用户的权限，应用层参数限制不能替代操作系统或容器隔离。

项目尚未建立公开安全漏洞报告渠道。在正式渠道发布前，请勿在公开 Issue 中披露真实凭证、内部地址或未修复漏洞的利用细节。

<a id="license"></a>

## 许可证

项目许可证尚未确定。在仓库加入正式 `LICENSE` 文件前，仓库内容不应被视为已经授予开源许可证下的使用、修改或分发权利。

<p align="right"><a href="#top">返回顶部</a></p>
