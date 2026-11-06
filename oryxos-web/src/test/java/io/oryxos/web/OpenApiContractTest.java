package io.oryxos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderCatalog;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.web.agent.AgentApiController;
import io.oryxos.web.agent.AgentInvocationWebService;
import io.oryxos.web.api.GlobalExceptionHandler;
import io.oryxos.web.config.CorsConfiguration;
import io.oryxos.web.config.OpenApiConfiguration;
import io.oryxos.web.memory.MemoryApiController;
import io.oryxos.web.memory.MemoryQueryService;
import io.oryxos.web.notify.NotifyChannelApiController;
import io.oryxos.web.notify.NotifyChannelMasker;
import io.oryxos.web.profile.ProfileApiController;
import io.oryxos.web.session.SessionApiController;
import io.oryxos.web.session.SessionWebService;
import io.oryxos.web.system.SystemApiController;
import io.oryxos.web.tool.ToolApiController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = OpenApiContractTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Test
    void corsAllowsDebugRequestsOnlyUnderApi() throws Exception {
        mvc.perform(options("/api/v1/sessions")
                        .header("Origin", "https://debug.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type")));
    }

    @Test
    void openApiContainsExactlyTheTenCoreOperations() throws Exception {
        var request = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        request.andExpect(jsonPath("$.paths['/api/v1/sessions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions/{id}/messages'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sessions/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agents/{name}/invoke'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/profiles'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/memory'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tools'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/info'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/agents/{name}/memory'].get").exists());

        JsonNode document = JSON.readTree(request.andReturn().getResponse().getContentAsByteArray());
        assertSuccessEnvelope(document, "/api/v1/sessions", "post");
        assertSuccessEnvelope(document, "/api/v1/sessions/{id}/messages", "post");
        assertSuccessEnvelope(document, "/api/v1/sessions/{id}", "get");
        assertSuccessEnvelope(document, "/api/v1/sessions/{id}", "delete");
        assertSuccessEnvelope(document, "/api/v1/agents/{name}/invoke", "post");
        assertSuccessEnvelope(document, "/api/v1/profiles", "get");
        assertSuccessEnvelope(document, "/api/v1/memory", "get");
        assertSuccessEnvelope(document, "/api/v1/tools", "get");
        assertSuccessEnvelope(document, "/api/v1/health", "get");
        assertSuccessEnvelope(document, "/api/v1/info", "get");
    }

    private static void assertSuccessEnvelope(JsonNode document, String path, String method) {
        JsonNode responses = document.path("paths").path(path).path(method).path("responses");
        JsonNode schema = responses.properties().stream()
                .filter(entry -> entry.getKey().startsWith("2"))
                .map(entry -> entry.getValue().path("content").path("application/json")
                        .path("schema"))
                .filter(node -> !node.isMissingNode())
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少成功响应 schema: " + method + " " + path));
        String reference = schema.path("$ref").asText();
        assertThat(reference).startsWith("#/components/schemas/ApiResponse");
        JsonNode envelope = document.at(reference.substring(1));
        assertThat(envelope.path("properties").properties().stream()
                .map(java.util.Map.Entry::getKey).toList())
                .contains("code", "message", "data", "timestamp");
    }

    @Test
    void everyDocumentedOperationUsesTheJsonEnvelopeAndRealStatusCodes() throws Exception {
        JsonNode document = openApiDocument();

        List<String[]> operations = List.of(
                new String[] {"/api/v1/sessions", "post"},
                new String[] {"/api/v1/sessions/{id}/messages", "post"},
                new String[] {"/api/v1/sessions/{id}", "get"},
                new String[] {"/api/v1/sessions/{id}", "delete"},
                new String[] {"/api/v1/agents/{name}/invoke", "post"},
                new String[] {"/api/v1/agents/{name}/memory", "get"},
                new String[] {"/api/v1/profiles", "get"},
                new String[] {"/api/v1/memory", "get"},
                new String[] {"/api/v1/tools", "get"},
                new String[] {"/api/v1/health", "get"},
                new String[] {"/api/v1/info", "get"},
                new String[] {"/api/v1/notify-channels", "post"},
                new String[] {"/api/v1/notify-channels", "get"},
                new String[] {"/api/v1/notify-channels/{name}", "get"},
                new String[] {"/api/v1/notify-channels/{name}", "put"},
                new String[] {"/api/v1/notify-channels/{name}", "delete"});

        for (String[] operation : operations) {
            String path = operation[0];
            String method = operation[1];
            JsonNode responses = document.path("paths").path(path).path(method).path("responses");
            assertThat(responses.isMissingNode())
                    .as("OpenAPI 缺少 %s %s", method.toUpperCase(), path).isFalse();
            assertThat(responses.properties().stream()
                    .filter(entry -> entry.getKey().startsWith("2"))
                    .filter(entry -> !entry.getKey().equals("204"))
                    .filter(entry -> entry.getValue().has("content"))
                    .allMatch(entry -> {
                        JsonNode content = entry.getValue().path("content");
                        return content.has("application/json")
                                && content.path("application/json").path("schema")
                                        .path("$ref").asText()
                                        .startsWith("#/components/schemas/ApiResponse");
                    }))
                    .as("%s %s 的成功响应必须为 application/json 统一信封", method.toUpperCase(), path)
                    .isTrue();
        }

        JsonNode sessionCreate = document.path("paths").path("/api/v1/sessions")
                .path("post").path("responses");
        assertThat(sessionCreate.has("200")).isTrue();
        assertThat(sessionCreate.has("201")).isTrue();
        assertThat(sessionCreate.path("201").has("content")).isTrue();
        assertThat(document.path("paths").path("/api/v1/notify-channels").path("post")
                .path("responses").has("201")).isTrue();
        assertThat(document.path("paths").path("/api/v1/notify-channels/{name}").path("delete")
                .path("responses").has("204")).isTrue();
    }

    private JsonNode openApiDocument() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        return JSON.readTree(response.getResponse().getContentAsByteArray());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({CorsConfiguration.class, OpenApiConfiguration.class, GlobalExceptionHandler.class,
            SessionApiController.class, AgentApiController.class, ProfileApiController.class,
            MemoryQueryService.class, MemoryApiController.class, ToolApiController.class,
            SystemApiController.class, NotifyChannelApiController.class})
    static class TestApplication {

        @Bean
        ProfileRegistry profiles() { return new ProfileRegistry(); }

        @Bean
        SessionWebService sessions() { return mock(SessionWebService.class); }

        @Bean
        AgentInvocationWebService agents() { return mock(AgentInvocationWebService.class); }

        @Bean
        MemoryService memory() { return mock(MemoryService.class); }

        @Bean
        ToolRegistry tools() { return mock(ToolRegistry.class); }

        @Bean
        ProviderCatalog providers() { return mock(ProviderCatalog.class); }

        @Bean
        NotifyChannelService notifyChannels() { return mock(NotifyChannelService.class); }

        @Bean
        NotifyChannelMasker notifyChannelMasker() { return new NotifyChannelMasker(); }
    }
}
