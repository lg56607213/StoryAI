package com.storyai.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "workflowTaskExecutor")
    public Executor workflowTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("workflow-");
        executor.initialize();
        return executor;
    }

    /**
     * 미디어(낭독 영상) 전용 풀. ffmpeg 합성은 한 건당 수 분 걸려서
     * 워크플로우(스토리·삽화) 스레드를 오래 점유하면 안 되므로 분리한다.
     */
    @Bean(name = "mediaTaskExecutor")
    public Executor mediaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-");
        executor.initialize();
        return executor;
    }
}
