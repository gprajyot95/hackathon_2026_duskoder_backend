package com.example.backend;

import com.example.backend.config.CacheConfig;
import com.example.backend.service.SchemaMetadataService;
import com.example.backend.service.StoredFunctionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;

@SpringBootTest
class CaffeineCacheServiceTest {

    @MockBean
    private StoredFunctionService storedFunctionService;

    @Autowired
    private SchemaMetadataService schemaMetadataService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        Mockito.reset(storedFunctionService);
        if (cacheManager.getCache(CacheConfig.SCHEMA_METADATA_CACHE) != null) {
            cacheManager.getCache(CacheConfig.SCHEMA_METADATA_CACHE).clear();
        }
    }

    @Test
    void testCaffeineCacheHitAndMiss() {
        String mockSchemaJson = "[{\"table_name\":\"customers\",\"column_name\":\"customer_id\"}]";
        Mockito.when(storedFunctionService.callStoredFunction(Mockito.anyString()))
                .thenReturn(mockSchemaJson);

        // First call: Cache miss -> Loads from StoredFunctionService and caches it
        String result1 = schemaMetadataService.getCachedSchemaMetadata();
        Assertions.assertEquals(mockSchemaJson, result1);
        Mockito.verify(storedFunctionService, Mockito.times(1)).callStoredFunction(Mockito.anyString());

        // Second call: Cache hit -> Served directly from Caffeine cache, StoredFunctionService NOT called again
        String result2 = schemaMetadataService.getCachedSchemaMetadata();
        Assertions.assertEquals(mockSchemaJson, result2);
        Mockito.verify(storedFunctionService, Mockito.times(1)).callStoredFunction(Mockito.anyString());

        // Assert Caffeine cache presence
        Assertions.assertTrue(schemaMetadataService.isCachePresent());
    }

    @Test
    void testSchemaRefreshUpdatesCache() {
        String oldSchema = "[{\"table_name\":\"customers\"}]";
        String updatedSchema = "[{\"table_name\":\"customers\"},{\"table_name\":\"accounts\"}]";

        Mockito.when(storedFunctionService.callStoredFunction(Mockito.anyString()))
                .thenReturn(oldSchema)
                .thenReturn(updatedSchema);

        // Initial fetch
        String result1 = schemaMetadataService.getCachedSchemaMetadata();
        Assertions.assertEquals(oldSchema, result1);

        // Trigger refresh
        boolean refreshed = schemaMetadataService.refreshSchemaMetadata();
        Assertions.assertTrue(refreshed);

        // Subsequent getCachedSchemaMetadata returns updated schema from Caffeine cache
        String result2 = schemaMetadataService.getCachedSchemaMetadata();
        Assertions.assertEquals(updatedSchema, result2);
    }
}
