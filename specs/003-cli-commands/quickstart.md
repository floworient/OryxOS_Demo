# Quickstart: 第 18 节 CLI 验收

## 前提

- JDK 21。
- 使用仓库 Maven Wrapper。
- 自动验收不需要真实模型密钥或外网。
- 下列示例使用 `oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar`。

## 自动验证

### 执行基线（2026-10-28）

- 初次 Wrapper 启动因沙箱将 `user.home` 解析为 `C:\` 而失败；直接重跑又因 JUnit 临时目录位于不可访问的 `%TEMP%` 而出现 29 个 `AccessDeniedException`，两次均未发现代码断言失败。
- 将 `user.home` 保持为 `C:\Users\hasee`，并把 `java.io.tmpdir`、`TEMP`、`TMP` 指向仓库内 `.tmp/` 后，指定 Reactor 共 15 个模块全部通过：Core 56、Provider 27、Storage 4、Web 3、Boot 2 个测试，其他目标模块无测试或随依赖构建成功。
- 有效基线命令退出码为 `0`，Maven 报告 `BUILD SUCCESS`，总耗时 `01:29 min`。后续本机验证沿用同一临时目录设置；`.tmp/` 仅为测试运行数据，不属于交付物。

```powershell
./mvnw.cmd -pl oryxos-core,oryxos-storage,oryxos-channel-cli,oryxos-cli,oryxos-boot -am test
./mvnw.cmd clean verify
```

预期：命令树、轻重分流、工作区、Profile 安全、CLI Channel、Session 摘要/恢复、Boot 运行时装配和现有回归全部通过；Checkstyle、SpotBugs、JaCoCo、Failsafe 通过。

## 工作区与轻命令

```powershell
$env:ORYXOS_ROOT = Join-Path $env:TEMP 'oryxos-cli-quickstart'
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar init
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar status
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile create demo
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile list
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile show demo
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile delete demo
```

预期：init 重复执行不覆盖；status 如实报告；create 后可 list/show；delete 后活动列表不含 demo，archive 下保留完整目录。

## 命令树

```powershell
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar --help
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile --help
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar provider list --help
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar --version
```

预期：不需要密钥；帮助列出契约中的 12 条业务路径；版本来自 JAR manifest。

## 查询与运行模式

```powershell
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar provider list
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar tool list
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar session list
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080
```

预期：三个 list 命令只显示真实项，空集合退出 0；serve 启动后 `/actuator/health` 返回 UP。gateway 以同一工作区启动聚合运行时，只报告实际装配渠道。

## chat 可控验收

自动测试使用 Provider 桩，输入 `第一问`、`第二问`、`/quit`，并验证：

- 两次且仅两次 `AgentService.process`。
- 同一 `cli + user + profile` Session。
- 完整消息、LLM 与 Tool 审计按既有链路保存并可重启恢复。
- `/quit`、空白行和 EOF 不触发模型。

真实模型聊天属于可选联网验证；缺少密钥时必须报告未验证，不得用桩结果冒充真实外呼。

## 最终验收记录（2026-10-28）

- `./mvnw.cmd test`：15 个 reactor 模块全部成功，退出码 `0`，最终耗时 `02:38 min`。
- `./mvnw.cmd clean verify`：15 个 reactor 模块全部成功，退出码 `0`，最终耗时 `03:54 min`。
- `OryxOsCliPackagingIT`：3 个进程级场景全部通过；覆盖根/4 个分组/12 条业务命令帮助、版本、无密钥 `init/status`、未知命令退出码 `2`，并确认只有 Boot 模块生成 exec JAR。
- Packaging IT 总耗时 `22.042 s`；其中 17 条帮助路径耗时 `11.023 s`，版本、`init`、`status` 与未知命令 4 个独立进程合计 `2.555 s`。Profile 轻命令在 `WorkspaceCommandTest` 中合计低于 1 秒，且命令树测试确认轻命令运行时启动次数为零。
- 最终测试报告合计 53 个 test suite、140 个测试，失败 `0`、错误 `0`、跳过 `2`；跳过项不属于本节新增 CLI 验收。
- 全 reactor 的 Checkstyle、SpotBugs、JaCoCo 与 Failsafe 均已执行；CLI 与 Boot 的 SpotBugs 均为 `0`。
- Spec-Kit `check-prerequisites.ps1 -Json -RequireSpec -RequireTasks -IncludeTasks` 成功识别 `specs/003-cli-commands` 及 spec/plan/tasks/quickstart/contracts 等制品。
- `git diff --check` 退出码为 `0`；高置信密钥形态扫描只命中既有文档中的 `sk-TEST-SENTINEL-DO-NOT-LEAK` 测试哨兵，没有真实凭据。用户本地 `.claude/settings.local.json` 仍为未跟踪文件，未修改、未纳入本功能。
- 本轮未执行真实模型、真实外部 HTTP 服务、飞书、企业微信或钉钉外呼；这些能力依赖真实密钥、网络或尚未注册的渠道，自动验收使用受控桩，不冒充真实端到端结果。

## Spec-Kit 一致性分析

| 契约 | 实现与验证依据 | 结论 |
| --- | --- | --- |
| FR-001～FR-003、SC-001 | Picocli 命令树、轻重分流、Packaging IT | 一致 |
| FR-004～FR-008、SC-002～SC-004 | Root Resolver、WorkspaceService、Profile 安全与归档测试 | 一致 |
| FR-009～FR-011、SC-005～SC-006 | CliChannel、Session 恢复、Boot chat/serve/gateway 与失败清理测试 | 一致 |
| FR-012～FR-016、SC-007 | Provider/Tool/Session 真实来源、排序、脱敏、stdout/stderr 测试 | 一致 |
| FR-017、SC-008 | 单元、集成、进程级测试及 Checkstyle/SpotBugs/JaCoCo/Failsafe | 一致 |

未发现 spec、plan、tasks 与实现之间仍需新增任务的冲突；真实密钥和外部渠道验证保持为明确未执行项，不影响无网络必需验收。

