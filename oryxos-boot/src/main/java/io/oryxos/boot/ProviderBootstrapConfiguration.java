package io.oryxos.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.ProfileRegistry;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class ProviderBootstrapConfiguration {

    @Bean
    static BeanFactoryPostProcessor workspaceDirectoryInitializer(Environment environment) {
        return beanFactory -> {
            var workspace = Path.of(environment.getProperty("oryxos.workspace", "./.oryxos"))
                    .toAbsolutePath().normalize();
            try {
                Files.createDirectories(workspace);
            }
            catch (IOException exception) {
                throw new BeanCreationException("无法创建 OryxOS 工作区: " + workspace, exception);
            }
        };
    }

    @Bean
    ProfileRegistry profileRegistry(@Value("${oryxos.workspace:./.oryxos}") String workspace) {
        var registry = new ProfileRegistry();
        new AgentLoader().loadAll(Path.of(workspace)).forEach(registry::register);
        return registry;
    }
}
