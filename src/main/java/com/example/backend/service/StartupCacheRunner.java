package com.example.backend.service;

import com.example.backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupCacheRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(StartupCacheRunner.class);

    private final SchemaMetadataService schemaMetadataService;
    private final AppProperties appProperties;

    public StartupCacheRunner(SchemaMetadataService schemaMetadataService,
                               AppProperties appProperties) {
        this.schemaMetadataService = schemaMetadataService;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        if (!appProperties.getStartup().isFetchOnStartup()) {
            logger.info("Startup schema metadata fetch is disabled via configuration");
            return;
        }

        logger.info("----------------------------------------------------------------------");
        logger.info("STARTUP TASK: Fetching PostgreSQL schema metadata & initializing Caffeine cache...");
        logger.info("----------------------------------------------------------------------");

        boolean success = schemaMetadataService.refreshSchemaMetadata();
        if (success) {
            logger.info("STARTUP TASK COMPLETED: Initial database schema loaded into Caffeine cache successfully.");
        } else {
            logger.warn("STARTUP TASK WARNING: Could not populate schema metadata on startup.");
        }
    }
}
