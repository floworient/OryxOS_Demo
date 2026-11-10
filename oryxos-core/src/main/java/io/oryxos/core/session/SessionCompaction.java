package io.oryxos.core.session;

public record SessionCompaction(String text, int processedUntil) {

    public SessionCompaction {
        if (text == null || text.isBlank() || processedUntil <= 0) {
            throw new IllegalArgumentException("会话摘要与处理位置无效");
        }
    }
}
