package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProfileSettingsTest {

    @Test
    void rejectsNonPositiveMaxIterations() {
        assertThatThrownBy(() -> new Profile.Settings(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile.Settings(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxHistoryTurns() {
        assertThatThrownBy(() -> new Profile.Settings(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile.Settings(10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
