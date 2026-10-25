package id.neravoxa.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} method execution and provides a dedicated, bounded executor for
 * outbound push-notification work. Isolating FCM on its own pool means a slow/unavailable
 * Firebase endpoint can never starve the servlet (request) threads or the common pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Dedicated pool for FCM dispatch — referenced by name in {@code @Async("fcmExecutor")}. */
    @Bean(name = "fcmExecutor")
    public Executor fcmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("fcm-");
        // If the queue fills up, run on the caller thread rather than dropping the notification.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
