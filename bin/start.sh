#!/usr/bin/env bash
# 启动 OryxOS Web Service（REST API + /admin 管理台，同一进程）。
# DeepSeek 等 Provider 凭证从 config/application.yml 读取，不需要 export 环境变量。
# 用法：bin/start.sh [端口]（默认 8080）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" # 工作区（.oryxos/、oryxos.db、config/）都相对 CWD，必须在项目根跑

PORT="${1:-${PORT:-8080}}"
CONFIG_DIR="$ROOT/config"
CONFIG="$CONFIG_DIR/application.yml"
EXAMPLE="$CONFIG_DIR/application.yml.example"
PIDFILE="$ROOT/bin/oryxos.pid"
CONSOLE_LOG="$ROOT/logs/console.log"
APP_LOG="$ROOT/.oryxos/logs/oryxos.log"

# 1. 选择 Java：优先 JAVA_HOME，避免 PATH 上的占位符/旧版本
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then
  JAVA="java"
else
  echo "[ERROR] 找不到可用的 Java 21。请设置 JAVA_HOME 指向 JDK 21。" >&2
  exit 1
fi

# 2. 找一个可执行的 boot JAR（新版 Maven 可能同时留下 -exec.jar 与普通 jar）
JAR="$(ls -1t "$ROOT"/oryxos-boot/target/oryxos-boot-*-exec.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "[ERROR] 找不到可执行 JAR，先构建：mvn -pl oryxos-boot -am package -DskipTests" >&2
  exit 1
fi

# 3. 配置在不在——首次从模板生成并提示填 key
if [ ! -f "$CONFIG" ]; then
  if [ ! -f "$EXAMPLE" ]; then
    echo "[ERROR] 缺少 $CONFIG，也没有模板 $EXAMPLE" >&2
    exit 1
  fi
  cp "$EXAMPLE" "$CONFIG"
  echo "[INFO] 已生成 $CONFIG"
  echo "[ACTION] 请打开该文件，填入 deepseek 的 api-key，然后重跑：bin/start.sh"
  exit 1
fi

# 4. 是否已在运行
if [ -f "$PIDFILE" ]; then
  OLD_PID="$(cat "$PIDFILE" 2>/dev/null || true)"
  if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "[INFO] OryxOS 已在运行 (pid $OLD_PID)。先 bin/stop.sh 再启动。"
    exit 0
  fi
  rm -f "$PIDFILE"
fi

mkdir -p "$ROOT/logs"

# 从外部 yaml 读配置（-D 系统属性，不经 picocli 子命令参数）。
# 同时保留工作区 .oryxos/application.yaml 的原有加载位置；两者都是 optional。
# serve 同时提供 REST API 与 /admin 管理台。
# 业务日志由 Spring Boot 写 $APP_LOG（滚动归档）；这里只把控制台输出（横幅、
# logback 初始化前的 JVM 级错误）收到 console.log，避免与滚动日志互相覆盖。
nohup "$JAVA" \
  -Dspring.config.additional-location="optional:file:$CONFIG_DIR/,optional:file:$ROOT/.oryxos/application.yaml" \
  -jar "$JAR" serve --port "$PORT" \
  >> "$CONSOLE_LOG" 2>&1 &
PID=$!
echo "$PID" > "$PIDFILE"
echo "[..] OryxOS 启动中 (pid $PID)，端口 $PORT，等待就绪…"

# 失败诊断：同时贴控制台输出与应用日志尾
log_tails() {
  tail -n 20 "$CONSOLE_LOG" >&2 || true
  if [ -f "$APP_LOG" ]; then
    echo "---- $APP_LOG ----" >&2
    tail -n 20 "$APP_LOG" >&2 || true
  fi
}

# 5. 等健康检查通过再报成功；进程中途死掉或超时则报失败并贴日志尾
READY=0
for _ in $(seq 1 45); do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "[ERROR] 进程已退出，启动失败。日志尾：" >&2
    log_tails
    rm -f "$PIDFILE"
    exit 1
  fi
  if curl -fs -o /dev/null "http://localhost:$PORT/api/v1/health" 2>/dev/null; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "[ERROR] 等待就绪超时（进程仍在，但 /api/v1/health 未通）。日志尾：" >&2
  log_tails
  exit 1
fi

echo "[OK] OryxOS 已就绪 (pid $PID)"
echo "     REST:     http://localhost:$PORT/api/v1/health"
echo "     管理台:   http://localhost:$PORT/admin/"
echo "     API 文档: http://localhost:$PORT/swagger-ui/index.html"
echo "     日志:     $APP_LOG（滚动，控制台原始输出在 $CONSOLE_LOG）"
echo "     停止:     bin/stop.sh"

# 当前仓库若未打包 /admin 前端资源，明确提示，而不是让人打开一个 404
if ! curl -fs -o /dev/null "http://localhost:$PORT/admin/" 2>/dev/null; then
  echo "[WARN] /admin/ 未返回 200：当前构建未包含管理台前端资源。"
fi
