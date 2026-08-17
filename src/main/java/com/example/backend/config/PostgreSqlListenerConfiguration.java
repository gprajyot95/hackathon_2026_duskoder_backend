package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class PostgreSqlListenerConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService postgresListenerExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "pg-notification-listener");
            thread.setDaemon(true);
            return thread;
        });
    }
}
