package io.oryxos.storage.config;

import io.oryxos.storage.JpaLlmCallRecorder;
import io.oryxos.storage.entity.LlmCallEntity;
import io.oryxos.storage.repository.LlmCallRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = LlmCallEntity.class)
@EnableJpaRepositories(basePackageClasses = LlmCallRepository.class)
@ComponentScan(basePackageClasses = JpaLlmCallRecorder.class)
public class StorageConfiguration {
}
