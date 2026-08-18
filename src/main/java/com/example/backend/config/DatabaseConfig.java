package com.example.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    public static final String DEFAULT_URL = "jdbc:postgresql://ep-dawn-violet-azxq73u3.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require";
    public static final String DEFAULT_USER = "neondb_owner";
    public static final String DEFAULT_PASSWORD = "npg_8btGxWBV5DCp";

    @Value("${spring.datasource.url:#{null}}")
    private String rawUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String rawUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String rawPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        DbCredentials credentials = resolveCredentials(rawUrl, rawUsername, rawPassword);
        logger.info("Initializing DataSource for host: {}, user: {}", credentials.getHost(), credentials.getUsername());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(credentials.getJdbcUrl());
        config.setUsername(credentials.getUsername());
        config.setPassword(credentials.getPassword());
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);

        return new HikariDataSource(config);
    }

    public static DbCredentials resolveCredentials(String rawUrl, String rawUsername, String rawPassword) {
        String url = (rawUrl != null && !rawUrl.isBlank()) ? rawUrl.trim() : DEFAULT_URL;
        String user = (rawUsername != null && !rawUsername.isBlank()) ? rawUsername.trim() : null;
        String password = (rawPassword != null && !rawPassword.isBlank()) ? rawPassword.trim() : null;

        String cleanUrl = url;
        if (cleanUrl.startsWith("jdbc:")) {
            cleanUrl = cleanUrl.substring(5);
        }

        String host = "unknown";
        try {
            URI uri = URI.create(cleanUrl);
            if (uri.getHost() != null) {
                host = uri.getHost();
            }
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isEmpty()) {
                String[] parts = userInfo.split(":", 2);
                if (user == null) {
                    user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                }
                if (password == null && parts.length > 1) {
                    password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse URI metadata from URL: {}", e.getMessage());
        }

        if (user == null || user.isEmpty()) {
            user = DEFAULT_USER;
        }

        if (password == null || password.isEmpty()) {
            password = DEFAULT_PASSWORD;
        }

        // Clean up JDBC URL format
        String jdbcUrl = url;
        if (!jdbcUrl.startsWith("jdbc:")) {
            jdbcUrl = "jdbc:" + jdbcUrl;
        }
        // Strip authority userInfo (user:pass@) if present so org.postgresql.Driver gets a clean host:port URL
        if (jdbcUrl.contains("@")) {
            int atIndex = jdbcUrl.indexOf("@");
            int schemeIndex = jdbcUrl.indexOf("://");
            if (schemeIndex != -1 && atIndex > schemeIndex) {
                jdbcUrl = jdbcUrl.substring(0, schemeIndex + 3) + jdbcUrl.substring(atIndex + 1);
            }
        }

        return new DbCredentials(jdbcUrl, user, password, host);
    }

    public static class DbCredentials {
        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String host;

        public DbCredentials(String jdbcUrl, String username, String password, String host) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
            this.host = host;
        }

        public String getJdbcUrl() { return jdbcUrl; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getHost() { return host; }
    }
}
