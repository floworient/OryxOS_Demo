# OryxOS 工程地基

本文记录基础工程的实际运行契约。OryxOS 是 Maven 多模块的模块化单体，最终由 `oryxos-boot` 打包为一个可执行 JAR，不包含服务发现、网关或其他微服务基础设施。

## 本地环境

- JDK 21
- Maven Wrapper（不依赖机器全局 Maven）

Windows：

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd clean package
```

Linux 或 macOS：

```bash
./mvnw test
./mvnw verify
./mvnw clean package
```

`verify` 会执行单元测试、`*IT` 集成测试、Checkstyle、SpotBugs/Find Security Bugs、JaCoCo 覆盖率门槛和 ArchUnit 架构测试。需要联网更新漏洞库的 OWASP Dependency-Check 放在 `security` Profile：

```powershell
.\mvnw.cmd -Psecurity -DskipTests verify
```

当前 JaCoCo 行覆盖率地基为 10%，并暂时排除纯模块标记类、启动类和尚未进入功能开发的 CLI 入口。新增核心能力时应随测试逐步提高门槛，不能通过扩大排除范围规避测试。

## HTTP 与 API

- 公开业务 API 使用 `/api/v1/**`。
- OpenAPI JSON：`/api-docs`
- Swagger UI：`/swagger-ui`
- Actuator 端点不使用业务响应信封包装。

业务响应统一为：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "timestamp": "2026-10-21T08:00:00Z"
}
```

HTTP 状态码保留协议语义；内部错误码提供稳定的程序判断依据。未知异常只返回 `INTERNAL_ERROR` 和安全消息，完整堆栈仅进入服务端日志。

## 日志与请求关联

默认 Profile 使用 Spring Boot 常规控制台日志；`prod` Profile 使用 Spring Boot ECS JSON 结构化日志：

```powershell
java -jar oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT-exec.jar --spring.profiles.active=prod
```

HTTP 请求使用 `X-Request-Id`。服务接受长度不超过 128 的字母、数字和 `._:-`；不合法或缺失时生成 UUID。该值写入 MDC 的 `requestId` 并回传响应 Header，请求结束后必须清理。

禁止记录 API Key、Authorization Header、Webhook URL、完整 Prompt、记忆正文和原始 Tool 结果。`llm_calls` 与 `tool_invocations` 是数据库审计记录，日志不能替代审计持久化。

## 健康检查与指标

默认暴露：

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

健康详情设置为 `never`。生产部署仍需在反向代理、网络策略或后续鉴权层限制管理端点访问，当前基础工程不声称已经完成外部鉴权。

指标标签必须是有界集合。禁止使用 Session ID、请求正文、URL、异常消息、Prompt 或用户标识作为 Prometheus 标签。

## 代码规范

`config/checkstyle/checkstyle.xml` 是当前可执行规范，以 Google Java Style 的可自动检查部分为基础。项目暂定最大行宽为 120，这是相对 Google 100 字符规则的明确偏差。规则调整应修改仓库配置并通过 `verify`，不能依赖个人 IDE 设置。

Checkstyle 排除写入 `config/checkstyle/suppressions.xml`；SpotBugs 排除写入 `config/spotbugs/exclude.xml`。安全漏洞排除写入 `config/dependency-check/suppressions.xml`，并必须记录 CVE、理由、责任人和复查日期。

## CI

`.github/workflows/build-and-quality.yml` 对 Pull Request 和 `main` 推送执行 Maven `verify`、密钥扫描，并在 Pull Request 中执行依赖漏洞扫描。测试、覆盖率和静态分析报告会作为构建产物上传。

网站部署仍由原有 `deploy-website.yml` 负责，工程地基初始化不改变其职责。
