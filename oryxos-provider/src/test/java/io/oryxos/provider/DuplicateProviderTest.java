package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import org.junit.jupiter.api.Test;

class DuplicateProviderTest {

    @Test
    void duplicateNameIsRejectedInsteadOfLastWriteWins() {
        var duplicate = new ProviderDefinition("same", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        assertThatThrownBy(() -> new ProviderRegistry(List.of(duplicate, duplicate), Map.of()))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> {
                            assertThat(error.errorCode()).isEqualTo(ProviderErrorCode.DUPLICATE_PROVIDER);
                            assertThat(error.getMessage()).contains("索引 0", "索引 1");
                        });
    }
}
