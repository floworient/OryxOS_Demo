package io.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownMemoryStoreConcurrencyTest {

    @TempDir
    Path temporary;

    @Test
    void concurrentAppendsForSameOwnerDoNotLoseEntries() throws Exception {
        Files.createDirectories(temporary.resolve("agents/concurrent"));
        Files.createDirectories(temporary.resolve("memory"));
        var store = new MarkdownMemoryStore(temporary,
                Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC),
                ZoneOffset.UTC, 10_000, 100, 512);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(index -> executor.submit(() -> store.append(
                            MemoryOwner.agent("concurrent"), "entry-" + index, MemoryScope.ARCHIVAL)))
                    .toList();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(store.archivalEntries(MemoryOwner.agent("concurrent")))
                .extracting(entry -> entry.content()).containsExactlyInAnyOrder(
                        java.util.stream.IntStream.range(0, 32).mapToObj(index -> "entry-" + index)
                                .toArray(String[]::new));
    }
}
