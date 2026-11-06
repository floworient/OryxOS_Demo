# Requirements Quality Checklist: Web Service 核心能力

**Purpose**: 检查 Web Service 规格是否完整、明确、可验证，并与批准设计及项目宪章一致。
**Created**: 2026-11-04
**Feature**: [spec.md](../spec.md)

## Scope & User Value

- [x] CHK001 核心 10 端点、6 个 Controller 和三个独立用户旅程已明确。
- [x] CHK002 认证、流式、动态管理、写 Memory、Tool 执行、审计查询与集群能力明确排除。
- [x] CHK003 连续会话、一次性调用和能力发现均有独立测试描述。

## Session & Persistence

- [x] CHK004 活动期复用、归档后新代、旧历史保留的身份语义明确。
- [x] CHK005 ACTIVE/ARCHIVED 的 find/details/archive/save 行为无歧义。
- [x] CHK006 Flyway V6、无损迁移和 active 部分唯一约束已定义。
- [x] CHK007 Scheduler/CLI/web-invoke 的查询与写权限边界已定义。

## API Contract

- [x] CHK008 每个核心端点的请求、响应、状态码和错误码可验证。
- [x] CHK009 32 KiB 使用 UTF-8 字节、历史最近 100 条和排序规则已量化。
- [x] CHK010 60 秒/504、尽力中断和不回滚外部副作用已明确。
- [x] CHK011 统一 ApiResponse 与 OpenAPI/CORS/virtual thread 要求已明确。

## Security & Boundaries

- [x] CHK012 Profile prompt/path、Tool args/result、Provider 凭证与异常原文禁止暴露。
- [x] CHK013 Agent 运行只经 AgentService，Tool 只经 ToolExecutor 的边界明确。
- [x] CHK014 Web 不依赖 storage/provider 实现，ProviderCatalog/SessionManager 契约位置明确。
- [x] CHK015 单进程锁和开放 CORS 的真实能力限制已披露。

## Testability & Delivery

- [x] CHK016 每项成功标准均可用自动化测试或明确命令测量。
- [x] CHK017 TDD RED/GREEN、真实 SQLite/AgentService IT 与无公网桩边界已定义。
- [x] CHK018 全仓 test、clean package、网站构建和未验证项报告均纳入交付。
- [x] CHK019 规格中不存在 `NEEDS CLARIFICATION` 或未决产品行为。

