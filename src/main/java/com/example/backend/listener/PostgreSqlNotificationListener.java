package com.example.backend.listener;

import com.example.backend.config.AppProperties;
import com.example.backend.service.SchemaMetadataService;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PostgreSqlNotificationListener implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSqlNotificationListener.class);

    private final SchemaMetadataService schemaMetadataService;
    private final AppProperties appProperties;
    private final ExecutorService executorService;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String dbUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String dbPassword;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastNotificationTime = new AtomicLong(0);

    private Connection dedicatedConnection;

    public PostgreSqlNotificationListener(SchemaMetadataService schemaMetadataService,
                                         AppProperties appProperties,
                                         ExecutorService postgresListenerExecutor) {
        this.schemaMetadataService = schemaMetadataService;
        this.appProperties = appProperties;
        this.executorService = postgresListenerExecutor;
    }

    @Override
    public void start() {
        if (!appProperties.getNotification().isEnabled()) {
            logger.info("PostgreSQL notification listener is disabled via configuration");
            return;
        }

        if (running.compareAndSet(false, true)) {
            logger.info("Starting PostgreSQL LISTEN service...");
            executorService.submit(this::listenLoop);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping PostgreSQL notification listener...");
            closeConnectionSilently();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Start after normal context initialization
        return Integer.MAX_VALUE;
    }

    private void listenLoop() {
        String channelName = appProperties.getNotification().getChannelName();
        long reconnectIntervalMs = appProperties.getNotification().getReconnectIntervalMs();
        long debounceMs = appProperties.getNotification().getDebounceMs();

        boolean wasReconnected = false;

        while (running.get()) {
            try {
                if (dedicatedConnection == null || dedicatedConnection.isClosed()) {
                    dedicatedConnection = createDedicatedConnection(channelName);
                    if (wasReconnected) {
                        logger.info("Listener reconnected successfully");
                    }
                    wasReconnected = true;
                }

                PGConnection pgConnection = dedicatedConnection.unwrap(PGConnection.class);
                // Non-blocking / short-timeout check for notifications (1000ms)
                PGNotification[] notifications = pgConnection.getNotifications(1000);

                if (notifications != null && notifications.length > 0) {
                    for (PGNotification notification : notifications) {
                        logger.info("Notification received on channel '{}': {}", notification.getName(), notification.getParameter());

                        long now = System.currentTimeMillis();
                        if (now - lastNotificationTime.get() < debounceMs) {
                            logger.info("Debouncing duplicate PostgreSQL notification (received within {}ms window)", debounceMs);
                            continue;
                        }
                        lastNotificationTime.set(now);

                        logger.info("Received PostgreSQL schema change notification.");
                        logger.info("Refreshing schema metadata...");

                        try {
                            boolean updated = schemaMetadataService.refreshSchemaMetadata();
                            if (updated) {
                                logger.info("Cache updated successfully");
                            } else {
                                logger.warn("Schema refresh attempt returned false, but listener remains active.");
                            }
                        } catch (Exception e) {
                            logger.error("Error refreshing schema metadata during notification processing: {}. Listener will remain active.", e.getMessage(), e);
                        }
                    }
                }
            } catch (SQLException sqle) {
                logger.warn("PostgreSQL notification connection issue: {}. Attempting reconnect in {}ms...", sqle.getMessage(), reconnectIntervalMs);
                closeConnectionSilently();
                sleepSilently(reconnectIntervalMs);
            } catch (Exception e) {
                logger.error("Unexpected error in PostgreSQL listener loop: {}. Retrying in {}ms...", e.getMessage(), reconnectIntervalMs, e);
                closeConnectionSilently();
                sleepSilently(reconnectIntervalMs);
            }
        }
        logger.info("PostgreSQL notification listener loop terminated.");
    }

    private Connection createDedicatedConnection(String channelName) throws SQLException {
        logger.info("Establishing dedicated physical connection to PostgreSQL for channel LISTEN...");
        String effectiveUrl = com.example.backend.config.DatabaseConfig.convertToJdbcUrl(dbUrl);
        Connection conn;
        if (dbUsername != null && !dbUsername.trim().isEmpty() && dbPassword != null && !dbPassword.trim().isEmpty()) {
            conn = DriverManager.getConnection(effectiveUrl, dbUsername, dbPassword);
        } else {
            conn = DriverManager.getConnection(effectiveUrl);
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LISTEN " + channelName + ";");
        }
        logger.info("Listening on channel {}", channelName);
        return conn;
    }

    private void closeConnectionSilently() {
        if (dedicatedConnection != null) {
            try {
                dedicatedConnection.close();
            } catch (Exception ignored) {
            } finally {
                dedicatedConnection = null;
            }
        }
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
