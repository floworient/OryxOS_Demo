package io.oryxos.web.config;

import io.oryxos.web.api.AgentCallExecutor;
import io.oryxos.web.api.Utf8MessageValidator;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Web 同步 Agent 调用的受管执行资源。 */
@Configuration(proxyBeanMethods = false)
public class WebExecutionConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService agentCallExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    AgentCallExecutor agentCallExecutor(ExecutorService agentCallExecutorService) {
        return new AgentCallExecutor(agentCallExecutorService, Duration.ofSeconds(60));
    }

    @Bean
    Utf8MessageValidator utf8MessageValidator() {
        return new Utf8MessageValidator();
    }
}
