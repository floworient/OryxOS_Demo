# Handoff：010 Web 对话工作台（第一版）

> 生成时间：2026-11-06
> 当前分支：`main`（无提交，所有改动在工作区）
> 接手目标：完成第一版 Web 对话工作台的收尾验证，并解决 `http_get` 被沙箱拒绝的阻塞项。

## 1. 用户原始需求

> 现在还需要使用 speckit 完成第一版工作台

- 依据文档：`docs/superpowers/specs/2026-11-06-oryxos-web-chat-frontend-design.md`（状态：待评审）。
- 用户附带了一个 PDF，但本会话无法解析，需求以仓库内设计文档为准。若接手方能看到 PDF，需核对差异。
- 约束：不修改 `009-web-service-core` 已冻结的 `/api/v1` 接口契约；不自动 commit / push；不改含真实密钥的 `config/application.yml`（除非用户明确同意）。

## 2. 已完成的交付物

### Spec-Kit 链路（全部已生成）

| 文件 | 状态 |
| --- | --- |
| `specs/010-web-chat-console/spec.md` | 完成，3 个用户故事，FR-001..FR-020 |
| `plan.md` | 完成，Constitution Check 全 PASS |
| `research.md` | 完成，11 项技术决策 |
| `data-model.md` | 完成 |
| `contracts/console-api-consumption.md` | 完成 |
| `contracts/console-ui-contract.md` | 完成 |
| `quickstart.md` | 完成（其中沙箱白名单的配置方式**尚待修正**，见第 4 节） |
| `tasks.md` | T001–T045 已写好，但**尚未批量标记 `[X]`** |
| `checklists/requirements.md` | 全部勾选 |
| `.specify/feature.json` | 指向 `specs/010-web-chat-console` |

> 说明：`.specify/scripts/powershell/setup-tasks.ps1` 被权限策略拦截（ExecutionPolicy Bypass），因此 `tasks.md` 是参照模板手工生成的。

### 前端实现（`frontend/`，未提交）

- 技术栈：Vue 3.5.45 + TypeScript 5.9.3 + Vite 8.3.6 + Vitest 5.1.0，无 UI 库、无 Pinia、无 Router，原生 `fetch`。
- 源码：
  - `src/api/http.ts`、`src/api/oryxos.ts`、`src/api/types.ts`
  - `src/state/useConsole.ts`（核心状态机）、`src/state/localStore.ts`
  - `src/utils/errors.ts`
  - `src/components/`：`AgentSidebar.vue`、`ConversationPanel.vue`、`ChatComposer.vue`、`MessageBubble.vue`、`ToolChips.vue`、`StatusBar.vue`、`EmptyState.vue`
  - `src/App.vue`、`src/main.ts`、`src/styles/main.css`
- 测试：`frontend/tests/` 共 8 个文件、47 个用例，**全部通过**。
- 构建：`vue-tsc --noEmit` 通过，`vite build` 成功（约 81 kB JS / 6.9 kB CSS），产物输出到 `oryxos-web/src/main/resources/static/console/`（已 gitignore）。
- 关键行为：Enter 发送 / Shift+Enter 换行；“新对话”先归档当前活动会话再清空本地标识（后端 `POST /sessions` 会复用同 `(channel,userId,profileName)` 的活动会话，直接清 localStorage 无法真正新建）；按 Agent 隔离会话；刷新恢复历史；错误态与重试。

### Demo Agent

- `frontend/demo/agents/daily-weather/AGENT.md`：含 `identity`（`agent_name`、`prompt`，Loader 必需）、`provider: deepseek/deepseek-chat`、`tools: [http_get]`。
- `frontend/demo/application.yaml.example`、`frontend/demo/README.md`、`frontend/README.md`。
- 运行时副本（gitignored）：`.oryxos/agents/daily-weather/AGENT.md`、`.oryxos/application.yaml`。

### 其他

- `.gitignore` 追加：`frontend/node_modules/`、`oryxos-web/src/main/resources/static/console/`。
- `bin/`、`config/application.yml.example`、`frontend/`、`specs/010-web-chat-console/` 均为未跟踪文件。

## 3. 已验证的行为

- 后端 `oryxos-boot` 可启动，`/api/v1/profiles` 返回 `daily-weather`。
- 真实 DeepSeek 调用成功，Session 历史中能看到 `toolCalls`（`http_get`）与最终 assistant 回复。
- 前端单元测试、类型检查、生产构建均通过。
- 未做：浏览器内真实点击流程的端到端验证（未启动 Vite dev server 实际点过）。

## 4. 当前阻塞项：`http_get` 始终返回「工具安全检查拒绝」

### 现象

真实请求中模型正确发起了 `http_get`，URL 为：

```
https://api.open-meteo.com/v1/forecast?latitude=30.2741&longitude=120.1551&current=...&timezone=Asia%2FShanghai
```

工具结果为：

```json
{"success":false,"content":"","errorMessage":"工具安全检查拒绝","retryable":false}
```

### 已排除 / 已尝试

1. `.oryxos/application.yaml` 写入
   ```yaml
   oryxos:
     http:
       allowed-domains:
         - api.open-meteo.com
   ```
   **无效**。
2. 环境变量 `ORYXOS_HTTP_ALLOWEDDOMAINS_0=api.open-meteo.com` **无效**。
3. `JAVA_TOOL_OPTIONS='-Doryxos.http.allowed-domains[0]=api.open-meteo.com'` **无效**；日志确认 JVM 已接收该参数（`Picked up JAVA_TOOL_OPTIONS: ...`）。
4. `JAVA_TOOL_OPTIONS='-Doryxos.http.allowed-domains=api.open-meteo.com'`（逗号分隔单值）**无效**。
5. 已确认运行的是重新构建的 JAR（`project.build.outputTimestamp` 使 JAR mtime 固定为 2026-10-13，但 `stat` 的 Change 时间与构建时间一致）；源码 `ToolConfiguration.java`、`WhitelistSandbox.java` 与主分支一致。

### 已定位的代码路径

- `oryxos-boot/src/main/java/io/oryxos/boot/ToolConfiguration.java:41` 创建 `WhitelistSandbox(http.getAllowedDomains(), ...)`。
- `oryxos-boot/src/main/java/io/oryxos/boot/HttpSandboxProperties.java`：`@ConfigurationProperties("oryxos.http")`，`Set<String> allowedDomains`，默认空集。
- `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/WhitelistSandbox.java:66`：`enforce` 中白名单为空或 host 不匹配即抛 `SandboxViolationException(HTTP_DENIED)`，`HttpTools` 将其转成「工具安全检查拒绝」。
- `ToolConfigurationTest#bindsHttpAndShellSandboxPropertiesWithoutImplicitDefaults` 只断言 `HttpSandboxProperties` Bean 的绑定，**不断言 Sandbox 实际生效**，所以测试通过不能证明运行时白名单有效。
- `BootCliRuntime.application(...)` 用 `setDefaultProperties`（低优先级）设置 `oryxos.workspace` 与 `spring.config.additional-location`，理论上不会覆盖系统属性。

### 未完成的验证（中断点）

计划手动启动一次，带上命令行参数 `--oryxos.http.allowed-domains=api.open-meteo.com`，命令如下（**该命令被用户中断，执行状态未知**，接手前先确认 `bin/oryxos.pid` 与 8080 端口是否有遗留进程）：

```bash
cd E:/Program/OryxOS_Demo
bin/stop.sh
JAVA=D:/IDE/JDK/JDK21/bin/java
nohup "$JAVA" \
  -Dspring.config.additional-location="optional:file:$PWD/config/,optional:file:$PWD/.oryxos/application.yaml" \
  -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar \
  serve --port 8080 --oryxos.http.allowed-domains=api.open-meteo.com \
  > logs/manual.log 2>&1 &
echo $! > bin/oryxos.pid
```

### 建议的排查顺序

1. **先确认绑定是否真的发生**：临时在 `ToolConfiguration.toolSandbox(...)` 里打印 `http.getAllowedDomains()`（或用 `ApplicationContextRunner` 启动完整 `OryxOsApplication` 后断言 `Sandbox` 行为），定位是「属性没绑上」还是「绑上了但匹配失败」。
2. 若属性没绑上：检查 `serve` 子命令路径是否额外注册了 property source；检查 Picocli 是否有 `@Option` 同名键；对比 `--oryxos.http.allowed-domains=...` 命令行参数是否生效。
3. 若绑上了仍失败：把模型给的 URL 直接喂给 `new WhitelistSandbox(Set.of("api.open-meteo.com")).enforce(...)` 单测，确认 `normalize` / `matches` 对该 URL 的行为（注意 `%2F` 与 `%2C` 的 raw query）。
4. 找到可靠配置方式后，更新 `quickstart.md`、`frontend/demo/application.yaml.example`、`frontend/demo/README.md`，保证文档与实际一致。
5. **不要**为了绕过沙箱直接放宽 `WhitelistSandbox`；这是架构契约（AGENTS.md 第 6 节）。

## 5. 剩余任务（tasks.md 中 T042–T045）

- T042：真实端到端验证（浏览器 + Vite dev server + 后端），确认消息、工具标记、刷新恢复、新对话归档。
- T043：错误态 / 空工作区 / 离线态的实际验证。
- T044：创建 `specs/010-web-chat-console/verification.md`，如实记录已验 / 未验项。
- T045：运行完整 `env -u TERM ./mvnw test` 并记录结果。
- 完成后把 `tasks.md` 中已完成项标记 `[X]`（T001–T041 基本已完成，需逐条核对后勾选）。

## 6. 本机运行要点

- Java：`D:/IDE/JDK/JDK21/bin/java`；Maven 命令需 `env -u TERM`（见记忆 `local-run-environment.md`）。
- 构建：`env -u TERM ./mvnw -pl oryxos-boot -am package -DskipTests`
- 启动：`JAVA_HOME=D:/IDE/JDK/JDK21 bin/start.sh 8080`（注意沙箱白名单问题，见第 4 节）
- 前端：`cd frontend && npm install && npm run dev`（Vite 代理 `/api` → `http://localhost:8080`）
- 数据库：`.oryxos/oryxos.db`（SQLite）；审计表 `tool_invocations`，会话表 `sessions.messages_json`。
- 查询示例：`sqlite3 -json .oryxos/oryxos.db "select tool_name,input_json,result_json from tool_invocations order by rowid desc limit 3;"`（`input_json` 中 URL 被脱敏为 `[REDACTED]`，真实 URL 在 `sessions.messages_json` 里）。

## 7. 红线与注意事项

- 不自动 `git commit` / `git push`；提交前先给用户看变更摘要。
- 不修改 `config/application.yml`（含真实 DeepSeek key）；如需改配置，先征得用户同意或改用外部覆盖方式。
- 不删除文件 / 目录 / git 历史；不 `git reset --hard`、不强制推送。
- 不改冻结的 `/api/v1` 契约；不改 `.specify/memory/constitution.md`。
- 验证结论必须如实：工具真实执行未成功前，不能声称 P1 验收通过。
