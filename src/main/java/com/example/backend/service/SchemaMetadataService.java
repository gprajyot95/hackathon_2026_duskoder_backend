package com.example.backend.service;

import com.example.backend.config.AppProperties;
import com.example.backend.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SchemaMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMetadataService.class);

    private final StoredFunctionService storedFunctionService;
    private final AppProperties appProperties;
    private final CacheManager cacheManager;

    public SchemaMetadataService(StoredFunctionService storedFunctionService,
                                 AppProperties appProperties,
                                 CacheManager cacheManager) {
        this.storedFunctionService = storedFunctionService;
        this.appProperties = appProperties;
        this.cacheManager = cacheManager;
    }

    /**
     * Retrieves database schema metadata.
     * Uses Spring @Cacheable with Caffeine in-memory cache.
     * On cache miss, loads metadata from PostgreSQL stored function and caches it.
     */
    @Cacheable(cacheNames = CacheConfig.SCHEMA_METADATA_CACHE, key = "'" + CacheConfig.SCHEMA_METADATA_KEY + "'", unless = "#result == null")
    public String getCachedSchemaMetadata() {
        logger.debug("Cache miss for schema metadata key '{}'. Loading from PostgreSQL database...", CacheConfig.SCHEMA_METADATA_KEY);
        String functionName = appProperties.getDb().getStoredFunctionName();
        try {
            String jsonOutput = storedFunctionService.callStoredFunction(functionName);
            if (jsonOutput != null && !jsonOutput.isBlank()) {
                logger.info("Successfully fetched schema metadata from PostgreSQL stored function '{}'", functionName);
                return jsonOutput;
            } else {
                logger.warn("Received empty schema payload from stored function '{}'", functionName);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to load schema metadata from PostgreSQL database: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Refreshes schema metadata from PostgreSQL stored function and updates Caffeine cache.
     *
     * @return true if successful, false otherwise
     */
    public boolean refreshSchemaMetadata() {
        String functionName = appProperties.getDb().getStoredFunctionName();
        logger.info("Schema refresh initiated. Executing PostgreSQL function '{}'...", functionName);
        try {
            String jsonOutput = storedFunctionService.callStoredFunction(functionName);
            if (jsonOutput != null && !jsonOutput.isBlank()) {
                Cache cache = cacheManager.getCache(CacheConfig.SCHEMA_METADATA_CACHE);
                if (cache != null) {
                    cache.put(CacheConfig.SCHEMA_METADATA_KEY, jsonOutput);
                    logger.info("Schema refresh successful. Updated Caffeine cache key '{}'", CacheConfig.SCHEMA_METADATA_KEY);
                }
                return true;
            } else {
                logger.warn("Schema refresh failed: empty payload from stored function '{}'", functionName);
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to refresh schema metadata in PostgreSQL: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Evicts schema metadata entry from Caffeine cache.
     */
    @CacheEvict(cacheNames = CacheConfig.SCHEMA_METADATA_CACHE, key = "'" + CacheConfig.SCHEMA_METADATA_KEY + "'")
    public void evictSchemaMetadataCache() {
        logger.info("Evicted schema metadata from Caffeine cache key '{}'", CacheConfig.SCHEMA_METADATA_KEY);
    }

    /**
     * Checks whether schema metadata currently exists in Caffeine cache.
     */
    public boolean isCachePresent() {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.SCHEMA_METADATA_CACHE);
            if (cache != null) {
                Cache.ValueWrapper valueWrapper = cache.get(CacheConfig.SCHEMA_METADATA_KEY);
                return valueWrapper != null && valueWrapper.get() != null;
            }
        } catch (Exception e) {
            logger.warn("Could not check cache presence: {}", e.getMessage());
        }
        return false;
    }
}
