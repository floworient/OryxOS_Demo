# Notify Adapter 契约

```java
public interface NotifyChannelAdapter {
    String channelType();
    void send(NotifyTarget target, String content);
}

public record NotifyTarget(String channelType, Map<String, String> config) {}
```

## 通用约束

- `channelType()` 返回非空、稳定、小写类型；注册表拒绝重复类型。
- `send` 只解释本类型配置，不查询 Profile、数据库或环境变量。
- 传入 `target.config().get("url")` 已由 resolver 解析且由 Sandbox 批准。
- 一次 `send` 最多产生一次外部发送，不在 Adapter 内重试。
- 失败抛出异常，不返回伪成功；异常消息不得包含 URL、token 或响应 body。

## WebhookNotifyAdapter

- 类型：`webhook`
- Method：`POST`
- Content-Type：`application/json`
- Body：`{"content": <content>}`
- Success：任意 2xx。
- Failure：3xx、4xx、5xx、连接/超时/编码错误均抛出安全异常。
- Redirect：不自动跟随。
