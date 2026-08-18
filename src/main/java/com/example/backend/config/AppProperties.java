package com.example.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Db db = new Db();
    private Cache cache = new Cache();
    private Startup startup = new Startup();
    private Notification notification = new Notification();
    private Gemini gemini = new Gemini();
    private Google google = new Google();
    private Github github = new Github();

    public Db getDb() {
        return db;
    }

    public void setDb(Db db) {
        this.db = db;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Startup getStartup() {
        return startup;
    }

    public void setStartup(Startup startup) {
        this.startup = startup;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public void setGemini(Gemini gemini) {
        this.gemini = gemini;
    }

    public Google getGoogle() {
        return google;
    }

    public void setGoogle(Google google) {
        this.google = google;
    }

    public Github getGithub() {
        return github;
    }

    public void setGithub(Github github) {
        this.github = github;
    }

    public static class Db {
        private String storedFunctionName = "get_database_schema";

        public String getStoredFunctionName() {
            return storedFunctionName;
        }

        public void setStoredFunctionName(String storedFunctionName) {
            this.storedFunctionName = storedFunctionName;
        }
    }

    public static class Cache {
        private String cacheName = "schemaMetadata";
        private String cacheKey = "databaseSchema";
        private long ttlHours = 6;
        private long maximumSize = 100;

        public String getCacheName() {
            return cacheName;
        }

        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getCacheKey() {
            return cacheKey;
        }

        public void setCacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        public long getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(long ttlHours) {
            this.ttlHours = ttlHours;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    public static class Startup {
        private boolean fetchOnStartup = true;

        public boolean isFetchOnStartup() {
            return fetchOnStartup;
        }

        public void setFetchOnStartup(boolean fetchOnStartup) {
            this.fetchOnStartup = fetchOnStartup;
        }
    }

    public static class Notification {
        private boolean enabled = true;
        private String channelName = "schema_changed";
        private long debounceMs = 1000;
        private long reconnectIntervalMs = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannelName() {
            return channelName;
        }

        public void setChannelName(String channelName) {
            this.channelName = channelName;
        }

        public long getDebounceMs() {
            return debounceMs;
        }

        public void setDebounceMs(long debounceMs) {
            this.debounceMs = debounceMs;
        }

        public long getReconnectIntervalMs() {
            return reconnectIntervalMs;
        }

        public void setReconnectIntervalMs(long reconnectIntervalMs) {
            this.reconnectIntervalMs = reconnectIntervalMs;
        }
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-3.6-flash";
        private int timeoutMs = 45000;
        private String instructionPath = "classpath:instruction.md";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getInstructionPath() {
            return instructionPath;
        }

        public void setInstructionPath(String instructionPath) {
            this.instructionPath = instructionPath;
        }
    }

    public static class Google {
        private String clientId = "";
        private String clientSecret = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    public static class Github {
        private String clientId = "";
        private String clientSecret = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
