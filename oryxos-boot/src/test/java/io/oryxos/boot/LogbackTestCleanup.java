package io.oryxos.boot;

import org.slf4j.LoggerFactory;

/**
 * 测试辅助：重置 Logback 上下文，释放文件 appender 持有的日志文件句柄。
 * 长驻入口（serve/gateway/chat）启用工作区文件日志后，appender 在应用上下文关闭后
 * 仍保持打开，Windows 上会锁住 @TempDir 里的文件导致清理失败，测试结束前必须释放。
 */
final class LogbackTestCleanup {

    private LogbackTestCleanup() {
    }

    static void releaseFileAppenders() {
        var factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof ch.qos.logback.classic.LoggerContext context) {
            context.reset();
        }
    }
}
