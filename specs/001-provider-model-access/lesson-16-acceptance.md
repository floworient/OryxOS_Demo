# 第 16 节验收报告：核心契约与 Provider 接入

**验收日期**：2026-10-26  
**工作分支**：`feature/provider`  
**规格目录**：`specs/001-provider-model-access`

## 1. 验收结论

第 16 节的无密钥交付范围已实现并通过自动化硬门禁：完整 Profile/Agent 加载、显式 Provider 路由、DashScope 与 OpenAI-compatible 适配、Schema-only Tool Callback、禁用 Spring AI 自动工具执行、`llm_calls` SQLite/Flyway/JPA 持久化、失败映射与审计失败关闭均有自动化验证。

真实 DashScope 联网调用未执行。当前环境没有同时提供显式开关 `-Doryxos.provider.smoke=true` 与 `ALIYUN_API_KEY`，因此 `ProviderSmokeIT` 按双门禁跳过；不得把桩测试结果表述为真实供应商端到端通过。

## 2. 实际交付行为

| 交付项 | 实际行为与证据 |
| --- | --- |
| Profile 与 Agent 加载 | 从 Agent 目录的 `AGENT.md` frontmatter 派生完整 Profile；校验目录名、必填字段、废弃字段与 YAML 安全边界；坏 Agent 隔离且不阻断合法 Agent。 |
| Provider 显式路由 | 通过不可变的名称到 `ChatModel` 映射精确路由；重名拒绝；未使用同类型 Bean 扫描或注册顺序猜测。 |
| Provider 协议 | DashScope 工厂与 OpenAI-compatible 工厂均使用本地已核实的 Spring AI API；回环协议测试验证 OpenAI 路径、Authorization、model 与 messages。 |
| 调用与失败策略 | `ProviderService.chat(String, Profile, Prompt)` 执行单轮调用；认证、限流、超时、网络、空响应与审计失败映射到固定错误/状态；无 fallback 或静默重试。 |
| Tool Schema | 名称、说明、required、enum 与嵌套 object 保真；callback 不委托 `OryxTool.execute`，请求显式设置 `internalToolExecutionEnabled=false`。 |
| 审计持久化 | Flyway 在 SQLite 创建 `llm_calls`；成功、空响应、失败及未知 token 用量按契约落库；同一数据库文件重建上下文后记录仍可读取；写入失败时调用失败关闭。 |
| Boot 装配 | 按 Provider 配置绑定、Agent 加载、Profile 注册、启动校验与 Provider 映射顺序装配；默认测试不访问外部模型服务。 |

## 3. 硬门禁结果

### 3.1 全量测试

执行命令（使用仓库 Maven Wrapper 与隔离的项目本地仓库）：

```powershell
.\mvnw.cmd -o '-Dmaven.repo.local=E:\Program\OryxOS_Demo\.m2\repository' test
```

- 退出码：`0`
- Reactor：15 个模块全部 `SUCCESS`
- 完成时间：2026-10-26 17:02:13 +08:00
- Maven 总耗时：1 分 19 秒

最终 `verify` 生成的 XML 报告汇总：

- 测试数：48
- 失败：0
- 错误：0
- 跳过：1（`ProviderSmokeIT`，缺少真实调用双门禁条件）

### 3.2 清理构建与质量门禁

执行命令：

```powershell
.\mvnw.cmd -o '-Dmaven.repo.local=E:\Program\OryxOS_Demo\.m2\repository' clean verify
```

- 退出码：`0`
- 完成时间：2026-10-26 17:05:30 +08:00
- Maven 总耗时：3 分 10 秒
- Reactor：15 个模块全部 `SUCCESS`
- Checkstyle：所有模块 0 violations
- SpotBugs：所有有代码模块 0 bug / 0 error
- JaCoCo：所有适用模块均显示 `All coverage checks have been met`
- Boot 集成测试：`OryxOsApplicationIT` 2 项通过；`ProviderSmokeIT` 1 项按条件跳过

### 3.3 Quickstart 无密钥路径

`Lesson16QuickstartTest` 从最小 Provider 配置和临时 Agent 定义开始，执行 `AgentLoader → ProfileRegistry → ProviderService.chat` 的无网络闭环。

- 测试结果：通过
- 实测耗时：8.165 秒
- SC-001 上限：300 秒
- 判定：通过

其余 Quickstart 行为由对应测试覆盖：双 Provider 零串线、配置错误前置发现、凭据哨兵 0 泄露、审计跨重启、Tool Schema 保真与 0 次工具执行、空响应、审计失败关闭、真实 read timeout 及 OpenAI 回环协议。

### 3.4 存在性核对

机器核对 `tasks.md` 中列出的实现、测试、迁移和规格路径：除本报告在核对时尚待创建外，其余路径全部存在；本报告创建后，清单列出的文件均已存在。项目未要求且原仓库不存在的额外交付文件未强行创建。

## 4. 反作弊与范围门禁

- 在本节涉及的 core/provider/storage/boot 代码中搜索 `@Disabled` 与 `@Ignore`：0 命中。
- 未删除断言，未通过放宽阈值使测试变绿；最终 `git diff --check` 通过。
- 未新增 `oryxos.provider-timeout-ms` 等 plan 外配置键；Provider connect/read timeout 固定为 60 秒，测试通过包内重载使用短时长。
- 本节新增的产品依赖限定为计划内的 `spring-ai-model`、`snakeyaml`、`spring-ai-openai`、`hibernate-community-dialects`；版本分别由 Spring AI BOM 或 Spring Boot 管理。
- 依赖/API 本地核实结果记录在 `research.md`：Spring AI 1.1.2、Spring AI Alibaba 1.1.2.2、SnakeYAML 2.4、Hibernate 6.6.36.Final。
- 未修改既定 `ProviderService.chat(String, Profile, Prompt)`、Profile 字段、`oryxos.providers` 配置形状或 `llm_calls` 列名。
- 独立代码审查发现的凭据 JSON 序列化、Agent 真实路径边界、opaque/无 host URL、重名位置、整数溢出及持久化证据问题均已修复，并补充红—绿回归测试。
- 未运行 `package.sh`，未自动 commit 或 push。

## 5. 门禁与未验证项

- 硬门禁：通过。
- 软门禁：实现期间没有发现需要新增清单外对外概念、修改既定字面量、修改非课件指定的前序公共接口、解决课件与 `docs/TechnicalSolution.md` 冲突，或新增 plan 外第三方依赖的情况。
- 未验证项：真实 DashScope 调用及其真实 token usage。原因是当前环境未同时提供用户授权的联网开关与 `ALIYUN_API_KEY`。需要验证时按 `quickstart.md` 路径 B 显式执行。

## 6. 成功标准映射

| 成功标准 | 结果 |
| --- | --- |
| SC-001 无密钥闭环 ≤300 秒 | 通过，8.165 秒 |
| SC-002 多 Provider 精确路由 | 通过，自动化测试 0 次误投 |
| SC-003 配置错误在外呼前发现 | 通过，启动校验与零网络断言覆盖 |
| SC-004 凭据原文出现次数为 0 | 通过，哨兵扫描测试覆盖 |
| SC-005 调用审计持久化且可跨重启 | 通过，SQLite/Flyway/JPA 重启测试覆盖 |
| SC-006 工具实际执行次数为 0 | 通过 |
| SC-007 Provider 内部开销 ≤50 ms | 通过，即时桩 p95 测试覆盖 |
| SC-008 合法/非法 Agent 隔离 | 通过 |
| SC-009 Tool Schema 保持率 100% | 通过，规定样例字段逐项断言 |

