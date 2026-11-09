package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.oryxos.OryxOsApplication;
import io.oryxos.core.scheduler.ScheduleId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ScheduledTaskE2ETest {

    @TempDir
    Path workspace;

    @Test
    void startupRegistersManualRunPersistsAndDisabledCronSkips() throws Exception {
        prepareAgent(workspace);
        try (var context = launch(workspace)) {
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).build();
            var path = "/api/v2/schedules";
            var id = ScheduleId.from("memory-agent", "daily").value();
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].scheduleId").value(id))
                    .andExpect(jsonPath("$.data[0].runCount").value(0))
                    .andExpect(jsonPath("$.data[0].enabled").value(true));
            mvc.perform(options(path + "/" + id)
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
            mvc.perform(post(path + "/" + id + "/run")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true));
            mvc.perform(get("/api/v1/memory").param("agentName", "memory-agent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.archival[0].content").value("我喜欢喝美式咖啡"));
            mvc.perform(put(path + "/" + id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
            context.getBean(CapturedScheduler.class).callback.run();
            mvc.perform(get(path)).andExpect(jsonPath("$.data[0].runCount").value(1))
                    .andExpect(jsonPath("$.data[0].lastStatus").value("success"));
            mvc.perform(get(path + "/" + id + "/executions"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].success").value(true));
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into task_executions(schedule_id, started_at, success, duration_ms)
                    values ('unknown', 0, 1, 0)
                    """)).hasMessageContaining("FOREIGN KEY");
        }
    }

    static void prepareAgent(Path workspace) throws Exception {
        Path agent = Files.createDirectories(workspace.resolve("agents/memory-agent"));
        Files.writeString(agent.resolve("AGENT.md"), """
                ---
                name: memory-agent
                description: 定时记忆
                identity: {agent_name: 记忆助手, prompt: 保存用户事实}
                provider: {name: mock, model: mock-memory}
                tools: [save_memory]
                schedules:
                  - key: daily
                    cron: "0 0 8 1 1 *"
                    zone: Asia/Shanghai
                    message: "记住：我喜欢喝美式咖啡"
                ---
                使用 save_memory 保存事实。
                """);
    }

    static ConfigurableApplicationContext launch(Path workspace) {
        var app = new SpringApplication(RuntimeUnderTest.class, SchedulerCapture.class);
        return app.run("--server.port=0", "--oryxos.workspace=" + workspace,
                "--oryxos.providers[0].name=mock", "--oryxos.providers[0].type=MOCK",
                "--oryxos.providers[0].model=mock-memory", "--oryxos.memory.backend=sqlite");
    }

    /** 从测试类路径装配生产组件，排除其他测试自己的策略与 Provider。 */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "io.oryxos", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.oryxos\\..*(?:Test|IT)\\$.*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = OryxOsApplication.class)
    })
    static class RuntimeUnderTest {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SchedulerCapture {
        @Bean
        @Primary
        CapturedScheduler capturedScheduler() {
            return new CapturedScheduler();
        }
    }

    static class CapturedScheduler extends ThreadPoolTaskScheduler {
        private Runnable callback;

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            callback = task;
            return super.schedule(task, trigger);
        }
    }
}
