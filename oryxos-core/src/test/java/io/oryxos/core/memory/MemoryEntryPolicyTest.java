package io.oryxos.core.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryEntryPolicyTest {

    private static final MemoryOwner OWNER = MemoryOwner.agent("alpha");

    @Test
    void normalizesMultilineUnicodeWhitespaceForEveryBackend() {
        assertThat(MemoryEntryPolicy.normalizeContent(" first\n\tsecond　third ", 100))
                .isEqualTo("first second third");
    }

    @Test
    void sortsByTimeAndStableIdBeforeSelectingLatestResults() {
        Instant time = Instant.parse("2026-10-31T00:00:00Z");
        List<MemoryEntry> unordered = List.of(entry("b", "alpha-b", time),
                entry("c", "alpha-c", time.plusSeconds(1)), entry("a", "alpha-a", time));

        assertThat(MemoryEntryPolicy.latestKeywordMatches(unordered, "ALPHA", 2))
                .extracting(MemoryEntry::id).containsExactly("b", "c");
    }

    @Test
    void promptBudgetCountsXmlEscapingAndNeverReturnsPartialEntry() {
        List<MemoryEntry> entries = List.of(
                entry("a", "old", Instant.EPOCH), entry("b", "<&>", Instant.EPOCH.plusSeconds(1)));

        assertThat(MemoryEntryPolicy.newestWithinPromptLimit(entries, 16))
                .extracting(MemoryEntry::id).containsExactly("b");
    }

    private static MemoryEntry entry(String id, String content, Instant createdAt) {
        return new MemoryEntry(id, OWNER, MemoryScope.ARCHIVAL, content, createdAt);
    }
}
