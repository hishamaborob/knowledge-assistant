package com.kbassistant.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Task executor for @Async ingestion pipeline.
     *
     * With spring.threads.virtual.enabled=true, Spring Boot wraps Tomcat's
     * request-handling threads with virtual threads automatically. But @Async
     * beans use a separate ThreadPoolTaskExecutor — we need to configure it
     * separately to also use virtual threads.
     *
     * Virtual thread executor: unbounded, non-blocking, no pool tuning needed.
     * Traditional ThreadPool: requires careful core/max/queue tuning to avoid
     * queue exhaustion under load spikes.
     */
    @Bean(name = "ingestionTaskExecutor")
    public TaskExecutor ingestionTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
    }
}
