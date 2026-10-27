# Quickstart: 第 17 节验收路径

当前使用已批准的 `.link` 项目引用契约；合法绑定不依赖Windows软连接权限。完整自动验收已通过；下方2026-10-27早期原生绑定失败结果仅作为历史记录。

## 前提

JDK 21、仓库 Maven Wrapper、现有 `.m2/repository`，第 16 节 Provider 代码和 V1 迁移。默认不需要密钥，不访问外部网络。默认绑定测试使用普通UTF-8 `.link` 文件，无需管理员权限。公共Skill保持单一实体，不复制；原生链接攻击附加IT仅在显式测试属性开启时运行。

## 自动验收

```powershell
$env:JAVA_HOME = "D:/IDE/JDK/JDK21"
$env:MAVEN_USER_HOME = "$PWD/.m2"
$env:MAVEN_OPTS = "-Duser.home=$PWD"
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$PWD/.m2/lesson17-tmp"
$env:TEMP = "$PWD/.m2/lesson17-tmp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
.\mvnw.cmd -o '-Dmaven.repo.local=E:\Program\OryxOS_Demo\.m2\repository' -pl oryxos-core,oryxos-storage,oryxos-boot -am test
.\mvnw.cmd -o '-Dmaven.repo.local=E:\Program\OryxOS_Demo\.m2\repository' clean verify
```

core 的 ReActLoopTest 验证无工具一轮终止、多个工具顺序执行、回填、10 轮停止和 Provider 失败；PromptBuilderTest 验证顺序、日期时间、20 轮截断与工具组完整；ToolExecutorTest 验证成功/失败/异常/拒绝/重试/超时及审计失败；AgentServiceTest 验证 ProfileContext 正常/异常清理和保存；ContextLoaderTest 验证项目引用热读、元数据、移动、非法引用与路径边界。

storage 测试使用文件型 SQLite：执行 V1→V2→V3、保存含多工具调用的 Session、写成功失败审计、关闭并重建连接或上下文后恢复原始数据。Boot 测试用 Provider 桩与无副作用 Tool，走 AgentService 全链路，核对 llm_calls 和 tool_invocations 同一 session_id，实际执行次数为一。

## 人工检查

1. 审查 ReActLoop 由项目自行实现；实际 execute 只出现在统一执行器，schema-only callback 不可执行。
2. 修改临时 Agent 正文与 Skill 绑定后重建 Prompt，确认立即生效且正文不泄入。
3. 检查 Session 保存前后完整消息，确认裁剪只影响模型输入。
4. 真实模型天气/穿搭 Demo：等待 HTTP 工具及 CLI 后续能力、真实凭据与外部调用授权，单独报告未验证；不得将本节桩链路称为真实天气 Demo 完成。

## 失败处理

任何测试或 clean verify 非零均未完成；不得删除断言、禁用用例或放宽质量门禁。离线依赖缺失单独报告，不冒充通过。实现后需按 speckit-converge 对比工件与代码，追加任务必须重新验证。

## 历史验证记录（2026-10-27，原生绑定契约）

| 检查 | 实际结果 | 日志 |
| --- | --- | --- |
| 审查修复后聚焦回归 | 21项通过：core18、storage2、boot1；不代表完整测试套件 | `.m2/lesson17-logs/review-fixes-green.log` |
| 完整 test（不筛选测试） | core36项：33通过、0断言失败、3环境错误、0跳过；下游因core失败未继续 | `.m2/lesson17-logs/full-test.log` |
| clean verify | 非零，Windows原生软连接创建权限不足阻断；未达到打包及完整质量门禁 | `.m2/lesson17-logs/full-clean-verify.log` |

3个环境错误均位于 ContextLoaderTest：合法绑定热读、绝对/断链/越界绑定拒绝、Skill文件逃逸验证。错误发生在 Files.createSymbolicLink 创建测试夹具时。普通目录绑定拒绝和 Bootstrap 越界检查已有实际执行，但不能据此宣称全部软连接边界通过。

保留所有测试和原断言，不使用复制目录或管理员权限替代；未运行真实外部天气 Demo。原生软连接需要在允许普通进程创建该链接的环境（例如已配置相应能力的开发机或Linux）补验。当时本节整体状态为“未完成验收”，已由下方项目引用契约验收结果替代。

- 最新独立编译/Checkstyle/SpotBugs：15个构建项目全部通过，日志 `quality-final.log`。
- 最新聚焦验证：SessionTest 4项、SQLite恢复/工具审计2项、Boot链路及架构2项，共8项通过，日志 `final-storage-boot.log`。
- 日志均在 `.m2/lesson17-logs/`。JaCoCo和完整集成测试未能随失败的clean verify完成，不能据独立静态结果判整体通过。

## 当前项目引用使用方式

在 `.oryxos/agents/weather/skills/news.link` 普通UTF-8文件中写入一行 `../../../skills/news`。路径相对于该文件父目录解析，目标为同一工作区 `.oryxos/skills/news/`；Prompt仅呈现元数据和公共SKILL.md真实可读取路径。移动整个项目后无需修改引用。

旧原生链接须由使用者明确迁移为引用文件；程序只提示，不自动删除或转换。禁止绝对/盘符/UNC路径、多行引用、越界、断引用及目录复制。文件工具必须按绑定解析结果约束可见性。

`ContextLoaderNativeLinksIT` 保留现存原生链接和文件逃逸的附加攻击验证，需要在本就允许创建原生链接的环境显式加 `-Doryxos.tests.native-links=true`；默认不执行该附加场景，不改变普通.link全套必测断言，不要求修改系统或使用管理员权限。

## 最终自动验证结果（2026-10-27，当前.link契约）

- 普通权限完整 `test` 通过：15个构建项目SUCCESS，87项测试通过，无失败或跳过。
- 普通权限完整 `clean verify` 通过：15个构建项目SUCCESS，汇总91项，89通过、2可选验证按条件未执行；Checkstyle、SpotBugs、JaCoCo、Boot应用启动与模块边界均通过。
- 2项未执行验证：ProviderSmokeIT需要真实调用授权及凭据双门禁；ContextLoaderNativeLinksIT需要测试-only显式开关及可创建原生链接的既有环境。本次不修改系统、不使用管理员权限。普通.link测试全部真实执行，不存在依赖这两个附加验证的正常绑定流程。
- 日志：`.m2/lesson17-logs/project-links-full-test.log`、`project-links-clean-verify.log`。可执行JAR：`oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar`。
- 真实天气Demo仍未执行，CLI/HTTP工具等后续能力未提前实现。当前运行时入口与约定见 `contracts/react-runtime.md`。
