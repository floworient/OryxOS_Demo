package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.LongTermMemoryStoreContract;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.storage.config.StorageConfiguration;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = JpaMemoryStoreTest.TestApplication.class)
class JpaMemoryStoreTest extends LongTermMemoryStoreContract {

    private static final Path DATABASE = Path.of(System.getProperty("java.io.tmpdir"),
            "oryxos-memory-store-" + UUID.randomUUID() + ".db");

    @Autowired
    private MemoryEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private JpaMemoryStore memory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.community.dialect.SQLiteDialect");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlite");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        memory = new JpaMemoryStore(repository,
                Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC), 80, 2, 512);
    }

    @Override
    protected LongTermMemoryStore store() {
        return memory;
    }

    @Override
    protected MemoryOwner firstOwner() {
        return MemoryOwner.agent("first");
    }

    @Override
    protected MemoryOwner secondOwner() {
        return MemoryOwner.agent("second");
    }

    @Test
    void reportsKeywordCapabilityAndOrdersEqualTimestampsByDatabaseId() {
        assertThat(memory.capabilities()).isEqualTo(MemoryCapabilities.KEYWORD);
        for (int index = 0; index < 12; index++) {
            memory.append(firstOwner(), "entry-" + index, io.oryxos.core.memory.MemoryScope.ARCHIVAL);
        }
        assertThat(memory.archivalEntries(firstOwner())).extracting(item -> item.content())
                .containsExactly(java.util.stream.IntStream.range(0, 12)
                        .mapToObj(index -> "entry-" + index).toArray(String[]::new));
    }

    @Test
    void flywayScopeConstraintRejectsInvalidRowsWithoutPersistingThem() {
        assertThatThrownBy(() -> jdbc.update("insert into memory_entries "
                + "(owner_key, scope, content, created_at) values (?, ?, ?, ?)",
                "first", "UNKNOWN", "invalid", "2026-10-31T00:00:00Z"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void recallTreatsWildcardsLiterallyAndPreservesUnicodeCaseFoldingAcrossPages() {
        var scope = io.oryxos.core.memory.MemoryScope.ARCHIVAL;
        memory.append(firstOwner(), "literal 100%_", scope);
        memory.append(firstOwner(), "İSTANBUL", scope);
        for (int index = 0; index < 140; index++) {
            memory.append(firstOwner(), "无关 " + index, scope);
        }
        memory.append(secondOwner(), "100%_ istanbul", scope);
        assertThat(memory.recallByKeyword(firstOwner(), "100%_"))
                .extracting(item -> item.content()).containsExactly("literal 100%_");
        assertThat(memory.recallByKeyword(firstOwner(), "i"))
                .extracting(item -> item.content()).containsExactly("literal 100%_", "İSTANBUL");
        var snapshot = memory.load(firstOwner());
        assertThat(snapshot.archivalTruncated()).isTrue();
        assertThat(snapshot.archival().getLast().content()).isEqualTo("无关 139");
        var largeWindow = new JpaMemoryStore(repository, Clock.systemUTC(), 10000, 20, 512);
        assertThat(largeWindow.load(firstOwner()).archival()).hasSize(142);
        assertThat(largeWindow.load(firstOwner()).archivalTruncated()).isFalse();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(StorageConfiguration.class)
    static class TestApplication {
    }
}
