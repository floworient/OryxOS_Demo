package io.oryxos.boot;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.scheduler.AgentScheduler;
import io.oryxos.core.scheduler.InMemoryScheduledTaskStore;
import io.oryxos.core.scheduler.ScheduledTaskStore;
import io.oryxos.core.session.SessionManager;
import java.time.Clock;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulerConfiguration {

    private static final String SKIP_REGISTRATION = "oryxosCliSkipScheduleRegistration";

    static void disableRegistration(ConfigurableApplicationContext context) {
        context.getBeanFactory().registerSingleton(SKIP_REGISTRATION, new Object());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("oryxos-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(ScheduledTaskStore.class)
    public ScheduledTaskStore scheduledTaskStore() {
        return new InMemoryScheduledTaskStore();
    }

    @Bean(destroyMethod = "close")
    public AgentScheduler agentScheduler(
            TaskScheduler taskScheduler,
            ProfileRegistry profiles,
            AgentService agents,
            SessionManager sessions,
            ScheduledTaskStore store,
            ListableBeanFactory beans) {
        var scheduler = new AgentScheduler(
                taskScheduler, profiles, agents, sessions, store, Clock.systemUTC());
        try {
            if (!beans.containsBean(SKIP_REGISTRATION)) {
                scheduler.registerAll();
            }
            return scheduler;
        } catch (RuntimeException failure) {
            scheduler.close();
            throw failure;
        }
    }
}
