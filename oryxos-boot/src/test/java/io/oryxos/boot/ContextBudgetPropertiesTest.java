package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;

class ContextBudgetPropertiesTest {

    @Test
    void bindsDefaultsAndACompleteExplicitRoute() {
        var defaults = bind(Map.of("oryxos.context.main-input-budget", "8192")).budget();
        assertThat(defaults.summaryInputBudget()).isEqualTo(4096);
        assertThat(defaults.summaryRoute()).isNull();
        var explicit = bind(Map.of("oryxos.context.main-input-budget", "16384",
                "oryxos.context.summary-provider", "small", "oryxos.context.summary-model", "summary")).budget();
        assertThat(explicit.mainInputBudget()).isEqualTo(16384);
        assertThat(explicit.summaryRoute().name()).isEqualTo("small");
        assertThat(explicit.summaryRoute().model()).isEqualTo("summary");
    }

    @Test
    void rejectsPartialRoutesAndInconsistentBudgets() {
        assertThatThrownBy(() -> bind(Map.of("oryxos.context.summary-provider", "small")).budget())
                .hasMessage("摘要 Provider 和模型必须同时配置");
        assertThatThrownBy(() -> bind(Map.of("oryxos.context.main-input-budget", "4096")).budget())
                .hasMessageContaining("配额组合无效");
    }

    private static ContextBudgetProperties bind(Map<String, Object> settings) {
        return new Binder(new MapConfigurationPropertySource(settings))
                .bind("oryxos.context", Bindable.of(ContextBudgetProperties.class)).get();
    }
}
