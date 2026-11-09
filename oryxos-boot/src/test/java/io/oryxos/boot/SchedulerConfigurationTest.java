package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.scheduler.InMemoryScheduledTaskStore;
import io.oryxos.core.scheduler.ScheduleId;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.session.SessionManager;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

class SchedulerConfigurationTest {

    @Test
    void providesConfiguredSchedulerAndDefaultStore() {
        runner(new ProfileRegistry()).run(context -> {
            assertThat(context).hasSingleBean(TaskScheduler.class);
            assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
            assertThat(context).hasSingleBean(ScheduledTaskStore.class);
            assertThat(context.getBean(ScheduledTaskStore.class))
                    .isInstanceOf(InMemoryScheduledTaskStore.class);

            ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("oryxos-scheduler-");
            assertThat(scheduler.isRemoveOnCancelPolicy()).isTrue();
            assertThat(ReflectionTestUtils.getField(
                            scheduler, "waitForTasksToCompleteOnShutdown"))
                    .isEqualTo(true);
        });
    }

    @Test
    void customStoreReplacesDefaultAndShutdownDoesNotRetirePersistedDefinition() {
        ScheduledTaskStore customStore = mock(ScheduledTaskStore.class);
        when(customStore.reconcile(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProfileRegistry profiles = mock(ProfileRegistry.class);
        when(profiles.all()).thenReturn(List.of(profileWithSchedule()));

        runner(profiles)
                .withBean(ScheduledTaskStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(ScheduledTaskStore.class);
                    assertThat(context.getBean(ScheduledTaskStore.class)).isSameAs(customStore);
                    verify(profiles).all();
                    verify(customStore).reconcile(any());
                });

        verify(customStore, never()).retire(ScheduleId.from("weather", "daily"));
    }

    @Test
    void codebaseContainsNoBusinessScheduledMethods() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        TypeFilter includeAll = (metadataReader, metadataReaderFactory) -> true;
        scanner.addIncludeFilter(includeAll);
        List<String> scheduledMethods = new ArrayList<>();

        for (var candidate : scanner.findCandidateComponents("io.oryxos")) {
            Class<?> type = Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Scheduled.class)) {
                    scheduledMethods.add(type.getName() + "#" + method.getName());
                }
            }
        }

        assertThat(scheduledMethods).isEmpty();
    }

    private static ApplicationContextRunner runner(ProfileRegistry profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(SchedulerConfiguration.class)
                .withBean(ProfileRegistry.class, () -> profiles)
                .withBean(AgentService.class, () -> mock(AgentService.class))
                .withBean(SessionManager.class, () -> mock(SessionManager.class));
    }

    private static Profile profileWithSchedule() {
        return new Profile(
                "weather",
                "description",
                new Profile.Identity("weather", "prompt"),
                new Profile.ProviderRef("provider", "model", null),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Profile.ScheduleRef(
                        "daily", "0 0 8 * * *", "Asia/Shanghai", "查询天气")),
                List.of(),
                new Profile.Settings(8, 20),
                Path.of(".", "weather", "AGENT.md"));
    }
}
