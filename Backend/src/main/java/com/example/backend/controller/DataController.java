package com.example.backend.controller;

import com.example.backend.config.AppProperties;
import com.example.backend.service.SchemaMetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DataController {

    private final SchemaMetadataService schemaMetadataService;
    private final AppProperties appProperties;

    public DataController(SchemaMetadataService schemaMetadataService,
                          AppProperties appProperties) {
        this.schemaMetadataService = schemaMetadataService;
        this.appProperties = appProperties;
    }

    /**
     * Gets the JSON schema metadata cached in Caffeine in-memory cache.
     */
    @GetMapping(value = {"/cache/data", "/schema/metadata"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getCachedData() {
        String cacheKey = appProperties.getCache().getCacheKey();
        String cachedJson = schemaMetadataService.getCachedSchemaMetadata();

        if (cachedJson != null && !cachedJson.isBlank()) {
            return ResponseEntity.ok(cachedJson);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("status", "CACHE_MISS");
            response.put("message", "No cached schema metadata found for key: " + cacheKey);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * Triggers execution of PostgreSQL stored function and updates Caffeine cache.
     */
    @PostMapping(value = "/cache/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> refreshCache() {
        String functionName = appProperties.getDb().getStoredFunctionName();
        String cacheKey = appProperties.getCache().getCacheKey();
        long ttlHours = appProperties.getCache().getTtlHours();

        Map<String, Object> response = new HashMap<>();
        boolean success = schemaMetadataService.refreshSchemaMetadata();

        if (success) {
            response.put("status", "SUCCESS");
            response.put("message", "Stored function executed and cache refreshed successfully");
            response.put("functionName", functionName);
            response.put("cacheKey", cacheKey);
            response.put("ttlHours", ttlHours);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Failed to refresh schema metadata cache");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Service health status and configuration info.
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getHealth() {
        String cacheKey = appProperties.getCache().getCacheKey();
        boolean cacheExists = schemaMetadataService.isCachePresent();

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("configuredStoredFunction", appProperties.getDb().getStoredFunctionName());
        health.put("configuredCacheKey", cacheKey);
        health.put("notificationChannel", appProperties.getNotification().getChannelName());
        health.put("isNotificationListenerEnabled", appProperties.getNotification().isEnabled());
        health.put("isCachedDataPresent", cacheExists);
        return ResponseEntity.ok(health);
    }
}
