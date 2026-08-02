package org.mingharness.config;

import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 统一调度器关闭策略，避免延迟任务阻止应用和测试进程及时退出。 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(TaskSchedulingProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, properties.getPool().getSize()));
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        if (properties.getShutdown().isAwaitTermination()) {
            scheduler.setAwaitTerminationMillis(properties.getShutdown().getAwaitTerminationPeriod().toMillis());
        }
        return scheduler;
    }
}
