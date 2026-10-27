package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ProfileContextTest {

    @Test
    void contextIsThreadLocalAndCleared() throws Exception {
        assertThat(ProfileContext.current()).isNull();
        var profile = mock(Profile.class);
        try {
            ProfileContext.set(profile);
            assertThat(ProfileContext.current()).isSameAs(profile);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executor.submit(ProfileContext::current).get()).isNull();
            }
        }
        finally {
            ProfileContext.clear();
        }
        assertThat(ProfileContext.current()).isNull();
    }
}
