package io.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.storage.repository.MemoryEntryRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class JpaMemoryStoreFailureTest {

    @Test
    void mapsRepositoryOutageToSanitizedStoreUnavailable() {
        MemoryEntryRepository repository = mock(MemoryEntryRepository.class);
        when(repository.findByOwnerKeyOrderByCreatedAtAscIdAsc("alpha"))
                .thenThrow(new DataAccessResourceFailureException("jdbc:sqlite:secret-path"));
        var store = new JpaMemoryStore(repository, Clock.systemUTC(), 4000, 20, 4000);

        assertThatThrownBy(() -> store.load(MemoryOwner.agent("alpha")))
                .isInstanceOfSatisfying(MemoryException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo(MemoryException.Code.STORE_UNAVAILABLE))
                .hasMessageNotContaining("secret-path");
    }
}
