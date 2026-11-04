# 第 24 课 Sandbox 研究与适配决定

日期：2026-11-01；依据本地课件、已批准设计及当前源码，未执行外部查询。

## R1：接口兼容

- Decision：保留三种强类型重载及批准值；新增完整构造器，旧构造器空额外根兼容。
- Rationale：005 FR-019 和 Action/Decision 已要求使用批准结果，MCP/搜索/Notify 均有调用方。
- Alternatives：字符串 Action/void 需全链路迁移；双接口易漂移，均不采用。接口形态本身不是安全漏洞。
- Evidence：`oryxos-tool/src/main/java/io/oryxos/tool/sandbox/Sandbox.java`、`WhitelistSandbox.java` 与 `specs/005-tool-system/data-model.md`。

## R2：文件根和公共 Skill

- Decision：普通额外根读写；Skill 保护优先；Shell cwd 不扩权。额外根必须存在、绝对、无原生链接；公共根不存在也保留词法边界。
- Rationale：AGENTS 第 4 节和 005 FR-010～012 禁止宽泛公共根绕过绑定。
- Alternatives：只按额外根 startsWith 放行破坏绑定；根不存在时自动创建目录没有需求，不采用。
- Evidence：SkillBindingResolver 的 source/binding 检查；WhitelistSandbox 的 realPath 与最近父目录逻辑。现有额外根为空，本次补齐配置。

## R3：目录遍历

- Decision：FileTools 列举/搜索逐项授权；visitor 在进入未授权目录前剪枝，公共根不从宽泛祖先进入。
- Rationale：当前 searchablePaths 使用 Files.walk，不逐项 Sandbox；加入额外根后仅检查起点不足以保护未绑定资源。
- Alternatives：直接放行整棵树不满足可见性；缓存 `.link` 违反现读规则。
- Evidence：FileTools 的 list、searchablePaths、grep、glob；JDK 21 自带 FileVisitor，无新增依赖。

## R4：失败原因和审计

- Decision：统一异常继承 SecurityException，tool 层转换固定、安全、不可重试 ToolResult；保留核心审计写入/次数。后续用户明确区分审计输入脱敏，允许依据失败证据单独修正参数脱敏，不涉及识别具体 Sandbox 异常。
- Rationale：当前 ToolExecutor 的 ExecutionException 和通用 catch 都泛化错误；直接抛异常无法得到类别原因，core 不能反向依赖 tool。
- Alternatives：把任意异常消息入审计可能泄密；给 core 添加具体异常识别破坏模块边界。
- Evidence：ToolExecutor.checkedExecute/execute；四类 Tool 的通用 catch；NotifyTargetResolver 已先 enforce 再 Adapter。
- 后续 RED 证据：真实 SQLite input_json 暴露 Shell arguments 的分离凭证和 --key=value 凭证；固定 errorMessage 已不含凭证，仍不能保护 input_json。参见 verification.md。

## R5：配置与装配

- Decision：Boot 新增 FileSandboxProperties（oryxos.file.allowed-paths），保留 oryxos.tool.allowed-commands / oryxos.http.allowed-domains，单一 toolSandbox Bean；启动快照。
- Rationale：ToolConfiguration 已显式创建 Sandbox，绑定类模式可复用。
- Alternatives：复制课件前缀形成第二份授权源，不采用；无热更新/管理 API。
- Evidence：ToolProperties、HttpSandboxProperties、ToolConfiguration、application.yaml；005 verification。

## R6：测试依赖与链路

- Decision：现有 JUnit/AssertJ/Mockito、JDK 回环 HTTP、安全 Java helper、boot 中的 SQLite/JPA IT。
- Rationale：boot 已依赖 storage/tool，可验证真实 ToolExecutor 与持久化。
- Alternatives：recorder mock 不能证明持久化；云模型/Webhook 对此授权验收无必要。
- Evidence：父 POM 锁定 JDK 21、Boot 3.5.8、AI 1.1.2/Alibaba 1.1.2.2；现有 ToolRegistryAuditIT、NotifyToolAuditIT。
- 未决项：无所需第三方 API 未决项；未更换技术，因此无需研究代理。原生链接平台能力在实施时实际检查，缺失须披露。
