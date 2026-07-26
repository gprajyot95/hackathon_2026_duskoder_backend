package com.example.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SCHEMA_METADATA_CACHE = "schemaMetadata";
    public static final String SCHEMA_METADATA_KEY = "databaseSchema";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SCHEMA_METADATA_CACHE);
        cacheManager.setCaffeine(caffeineBuilder());
        return cacheManager;
    }

    @Bean
    public Caffeine<Object, Object> caffeineBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(6, TimeUnit.HOURS)
                .recordStats();
    }
}
