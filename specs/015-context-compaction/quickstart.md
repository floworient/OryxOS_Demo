# 验收指南：会话上下文压缩

以下命令在实现后从仓库根目录运行。使用 JDK 21、Maven Wrapper、回环协议服务和临时 SQLite。测试不读取真实模型密钥。

## 预算与协议

```powershell
./mvnw.cmd -pl oryxos-core,oryxos-provider -am test
```

预期固定首条系统消息和工具顺序。跨时间或记忆变更时动态字段更新。大工具结果的发送副本带裁剪标记，调用 ID、执行次数和原文保持一致。必需内容超限时没有模型请求。

## 固定样本与重启

```powershell
./mvnw.cmd -pl oryxos-boot -am "-Dit.test=ContextCompactionIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

向同一会话回放 32 个固定用户轮次。主模型桩每轮发起两次工具调用，返回两份各 6 KiB 的可复现结果，随后给出固定回复。摘要桩使用确定的 384 字节以内文本。第 24 轮后重启。独立单测验证摘要失败保留上次游标。运行时时间戳的表示长度可能导致不同回放间有少量估算单位差异。

基线使用相同输入、工具、稳定区和模型桩，采用旧实现的 20 轮窗口及完整工具结果。基线省略时间戳并使用最小记忆块，避免高估节省。记录每次主调用和摘要调用的请求估算单位和输出估算单位。费率使用固定测试费率，输入每单位 2，输出每单位 8，所有输入统一按未命中计费，结果以相对费用表示。应满足 `新累计输入 < 基线累计输入` 且 `新总费用 < 基线总费用`。真实账单验收采用实际用量和费率，并独立记录缓存命中与未命中。

## 全量回归

```powershell
./mvnw.cmd test
./mvnw.cmd clean package
git diff --check
```

预期各命令退出 0。交付记录包含模块测试、集成回放、构建结果及真实模型未测范围。

Windows 工作树与 Maven 缓存位于不同盘符时，如果 Surefire 的 manifest classpath 无法加载模块测试类，可给验证命令追加 `-Dsurefire.useManifestOnlyJar=false`。
