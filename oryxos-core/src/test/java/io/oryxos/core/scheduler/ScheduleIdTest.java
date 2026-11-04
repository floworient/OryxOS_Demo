package io.oryxos.core.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ScheduleIdTest {

    @Test
    void sameProfileAndKeyAlwaysProduceSameIdentity() {
        ScheduleId first = ScheduleId.from("weather", "daily");
        ScheduleId second = ScheduleId.from("weather", "daily");
        ScheduleId third = ScheduleId.from("weather", "daily");

        assertThat(first).isEqualTo(second).isEqualTo(third);
        assertThat(first.value()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void differentIdentityPartsAndAmbiguousConcatenationsRemainDistinct() {
        assertThat(ScheduleId.from("weather", "daily"))
                .isNotEqualTo(ScheduleId.from("news", "daily"))
                .isNotEqualTo(ScheduleId.from("weather", "weekly"));
        assertThat(ScheduleId.from("ab", "c")).isNotEqualTo(ScheduleId.from("a", "bc"));
    }

    @Test
    void blankIdentityPartsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> ScheduleId.from("", "daily"));
        assertThatIllegalArgumentException().isThrownBy(() -> ScheduleId.from("weather", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> ScheduleId.from(null, "daily"));
        assertThatIllegalArgumentException().isThrownBy(() -> ScheduleId.from("weather", null));
    }

    @Test
    void nonCanonicalUuidTextIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ScheduleId("1-1-1-1-1"));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ScheduleId("abcdef01-abcd-abcd-abcd-abcdef012345".toUpperCase()));
    }
}
