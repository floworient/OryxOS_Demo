# Runtime Contracts: 第 17 节

本节只暴露 Java 运行时入口，无新增 REST、CLI、Profile 配置键。

## 入口与关联

- `ReActLoop.run(Session session, String userMessage, Profile profile): String`
- `AgentService.process(Session session, String userMessage): String`
- `PromptBuilder.build(Session session, Profile profile): Prompt`
- `ToolExecutor.execute(String sessionId, ToolCallIntent call): ToolResult`
- `ProfileContext.set(Profile)`、`current(): Profile`（未设置时 null）、`clear()`。
- `ContextLoader.load(Profile): String` 返回系统指令源文本；时间由 PromptBuilder 追加。
- `SessionManager.save(Session)`、`find(String sessionId): Optional<Session>`；save 必须完成可靠写入，失败抛异常。
- `ToolInvocationRecorder.record(ToolInvocationRecord)`；保存失败抛异常，不接受静默降级。

前序 `ProviderService.chat(String,Profile,Prompt)`、`Profile`、`ToolCallIntent`、`ToolResult` 和 `OryxTool` 不改签名。课件简写 Response/ToolCall 不新增为 public 类型。

## 调用顺序

AgentService 依据 Session.profileName 从 ProfileRegistry 解析 Profile，设置 ProfileContext，执行循环，保存 Session，finally 清理。未知 Profile 在执行前报错。Provider 每次调用携带 sessionId；工具每次尝试同样携带 sessionId。

工具池由显式 Map 注入，不从框架自动执行。ToolExecutor 判断注册、Profile.tools、参数 JSON 与检查策略后执行。PromptBuilder 向模型传递的工具与同一 Profile.tools 对齐；未知声明报错。默认生产策略拒绝全部，测试显式放行桩；无可信 Profile 时拒绝。

正常工具一次；retryable 失败最多重试三次，总尝试最多四次；100/200/400 ms 退避。参数错误、安全拒绝、超时和未标记异常不重试。每次尝试单独记录。工具异常反馈失败结果而非抛出原始敏感堆栈，审计异常必须向外抛。

超时由内部可注入执行机制控制，生产等待最多 60 秒；工作线程显式 set/clear ProfileContext。取消仅保证调用方有界等待，不保证能停止不合作工具，不作为代码隔离或自动重试依据。

## 消息及上下文

UserMessage → AssistantMessage（含全部原 ToolCall ID）→ ToolResponseMessage（相同 ID/name）→ 下一轮 AssistantMessage。无工具调用时直接返回 content，包括空字符串。达到上限追加“达到最大轮数，已停止”。不生成额外模型调用。

历史按 UserMessage 划分轮次，最近 N 轮包含当前用户轮；每轮多个 assistant/tool 消息整体保留。Session 存完整历史，Prompt 仅取视图。系统上下文在每次 build 重建，不作为历史持久化。

当前日期时间处于首个 system 消息末尾，以带时区的时间文本呈现；Clock 可注入，生产用系统时钟。可选长期记忆槽为空则省略，不能重复注入会话历史。工具定义在 Prompt options，固定 internalToolExecutionEnabled=false。

## 路径

- 工作区来自现有 `oryxos.workspace`；Profile.sourcePath 为现有 AGENT.md 路径。
- Bootstrap 只能解析到工作区允许边界内；不存在 WARN，存在但越界报错。
- Skill 本地目录项必须是普通 UTF-8 `<binding>.link` 文件，只含一条相对于其父目录的 Skill 目录路径；目标及 SKILL.md 的真实路径均受公共 skills 根约束。绝对、盘符、UNC、多行、目录复制、断引用及逃逸全部报错；原生链接明确提示迁移。
- 只解析 Skill frontmatter name/description，输出由项目引用解析并校验后的公共 SKILL.md 真实路径，不提供虚假的文件系统目录别名；后续文件工具必须复用绑定解析结果进行资源可见性校验。元数据缺失或非法报错，未绑定 Skill 不扫描正文。

## 已批准的必要支撑范围

新增 Session/SessionManager、ToolInvocationRecord/Recorder、SessionEntity/Repository/JpaSessionManager、JpaToolInvocationRecorder 和 sessions 表用于实现课件要求的保存及依赖倒置。除此不提前增加 ToolRegistry/Sandbox/MemoryService 产品体系。批准不允许修改前序公共接口。
