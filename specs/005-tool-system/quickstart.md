# Tool 体系 Quickstart

## 1. 配置 Sandbox

在工作区 `.oryxos/application.yaml` 中显式放行最小能力：

```yaml
oryxos:
  http:
    allowed-domains:
      - api.duckduckgo.com
  tools:
    file:
      readable-roots: []
      writable-roots: []
    shell:
      allowed-executables:
        node: C:/Program Files/nodejs/node.exe
```

Agent 自有目录无需额外加入文件白名单；公共 Skill 只有通过 `skills/*.link` 绑定后才可读，且始终不可写。

## 2. 配置 MCP（可选）

创建 `.oryxos/mcp_servers.yaml`：

```yaml
servers:
  - name: local-example
    transport: stdio
    command: node
    args: ["C:/tools/example-mcp/server.js"]
    request_timeout: 30
    env:
      API_TOKEN: ${EXAMPLE_TOKEN}
```

在 Agent 的 `AGENT.md` frontmatter 中加入：

```yaml
tools:
  - read_file
  - grep
  - http_get
  - web_search
mcp_servers:
  - local-example
```

## 3. 查看工具

```powershell
.\mvnw.cmd -pl oryxos-boot -am package -DskipTests
java -jar oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT-exec.jar tool list --workspace .oryxos
```

预期：列表包含配置并成功初始化的 MCP 工具、声明的内置工具以及既有 `notify`。列表展示全局注册结果；某 Agent 的模型清单仍按 `tools` / `mcp_servers` 过滤。

## 4. 自动验收

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-tool,oryxos-channel-cli,oryxos-boot -am test
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

自动测试不需要外网、真实 MCP server 或搜索服务。真实 DuckDuckGo/MCP 验证为可选手工检查，未执行时必须明确报告。

## 5. 安全提示

- Shell alias 最终映射到绝对 executable；把 Python、Node 或 PowerShell 加入白名单等于授予 OryxOS 进程用户代码执行权限。
- HTTP 工具不接受模型提供认证凭证；需认证的企业系统使用 MCP server。
- 应用层白名单不是容器级沙箱，不适合不可信多租户代码。

