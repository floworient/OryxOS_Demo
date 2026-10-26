package io.oryxos.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OryxOS 公共 REST API 的 OpenAPI 元数据。 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI oryxOsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OryxOS API")
                .version("v1")
                .description("OryxOS Agent 运行时公共管理与调用 API"));
    }
}
