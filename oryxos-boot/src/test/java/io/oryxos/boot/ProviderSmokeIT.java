package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.storage.repository.LlmCallRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration")
@EnabledIfSystemProperty(named = "oryxos.provider.smoke", matches = "true")
@EnabledIfEnvironmentVariable(named = "ALIYUN_API_KEY", matches = ".+")
@SpringBootTest(classes = OryxOsApplication.class, properties = {
        "oryxos.workspace=${java.io.tmpdir}/oryxos-provider-smoke",
        "oryxos.providers[0].name=qwen",
        "oryxos.providers[0].type=DASHSCOPE",
        "oryxos.providers[0].model=qwen-plus",
        "oryxos.providers[0].api-key=${ALIYUN_API_KEY}"
})
class ProviderSmokeIT {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private LlmCallRepository repository;

    @Test
    void realDashScopeCallIsAuditedOnlyWhenExplicitlyEnabled() {
        var before = repository.count();
        var profile = new Profile("smoke", "真实 Provider 冒烟",
                new Profile.Identity("smoke", "只做连通性验证"),
                new Profile.ProviderRef("qwen", "qwen-plus", 0.0),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new Profile.Settings(1, 1), Path.of("smoke/AGENT.md"));

        var response = providerService.chat(null, profile, new Prompt("只回复 OK"));

        assertThat(response.providerName()).isEqualTo("qwen");
        assertThat(repository.count()).isEqualTo(before + 1);
    }
}
