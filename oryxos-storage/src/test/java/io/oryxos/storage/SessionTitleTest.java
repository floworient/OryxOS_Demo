package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.session.SessionManager;
import io.oryxos.core.session.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/** 会话名（title）持久化行为：自动默认名、保留、重命名。 */
class SessionTitleTest {

    @TempDir
    Path temporary;

    @Test
    void autoTitlesSessionWithFirstUserMessageAndTruncatesToThirtyChars() {
        String url = "jdbc:sqlite:" + temporary.resolve("title.db");
        try (var context = SessionArchiveTest.application(url).run()) {
            var sessions = context.getBean(SessionManager.class);
            var acquisition = sessions.acquire("web", "alice", "weather");
            var session = sessions.find(acquisition.sessionId()).orElseThrow();

            // 尚无用户消息：未命名
            session.appendAssistant("agent 的开场白");
            sessions.save(session);
            assertThat(titleOf(sessions, session.id())).isNull();

            // 首条用户消息成为会话名（换行折叠、截断 30 字符）
            session.append("今天\n上海的天气怎么样？后续还有很长很长的补充内容需要被截断掉请注意这行超过三十个字符");
            sessions.save(session);
            assertThat(titleOf(sessions, session.id()))
                    .isEqualTo("今天 上海的天气怎么样？后续还有很长很长的补充内容需要被截断");

            // 再次保存：会话名保留，不被覆盖
            session.append("再问一条");
            sessions.save(session);
            assertThat(titleOf(sessions, session.id()))
                    .isEqualTo("今天 上海的天气怎么样？后续还有很长很长的补充内容需要被截断");
        }
    }

    @Test
    void renamesSessionAndArchivedSessionKeepsTitle() {
        String url = "jdbc:sqlite:" + temporary.resolve("rename.db");
        try (var context = SessionArchiveTest.application(url).run()) {
            var sessions = context.getBean(SessionManager.class);
            var acquisition = sessions.acquire("web", "alice", "weather");
            var session = sessions.find(acquisition.sessionId()).orElseThrow();
            session.append("第一条消息");
            sessions.save(session);

            assertThat(sessions.rename(session.id(), "2026-11-09 每日简报")).isTrue();
            assertThat(titleOf(sessions, session.id())).isEqualTo("2026-11-09 每日简报");
            assertThat(sessions.list()).singleElement()
                    .satisfies(summary -> assertThat(summary.title()).isEqualTo("2026-11-09 每日简报"));

            // 重命名后继续写入：用户设置的名字优先于自动命名
            session.append("第二条消息");
            sessions.save(session);
            assertThat(titleOf(sessions, session.id())).isEqualTo("2026-11-09 每日简报");

            // 归档后会话名保留，重命名不存在会话返回 false
            assertThat(sessions.archive(session.id())).isTrue();
            assertThat(sessions.list()).singleElement()
                    .satisfies(summary -> assertThat(summary.status()).isEqualTo(SessionStatus.ARCHIVED))
                    .satisfies(summary -> assertThat(summary.title()).isEqualTo("2026-11-09 每日简报"));
            assertThat(sessions.rename("missing", "名字")).isFalse();
        }
    }

    private static String titleOf(SessionManager sessions, String sessionId) {
        return sessions.list().stream()
                .filter(summary -> summary.id().equals(sessionId))
                .flatMap(summary -> java.util.Optional.ofNullable(summary.title()).stream())
                .findFirst()
                .orElse(null);
    }
}
