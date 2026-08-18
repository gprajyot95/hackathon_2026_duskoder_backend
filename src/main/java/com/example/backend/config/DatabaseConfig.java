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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:#{null}}")
    private String dbUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String dbUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String dbPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        String effectiveUrl = convertToJdbcUrl(dbUrl);
        logger.info("Initializing DataSource with URL: {}", sanitizeUrl(effectiveUrl));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(effectiveUrl);
        config.setDriverClassName("org.postgresql.Driver");

        if (dbUsername != null && !dbUsername.trim().isEmpty()) {
            config.setUsername(dbUsername.trim());
        }
        if (dbPassword != null && !dbPassword.trim().isEmpty()) {
            config.setPassword(dbPassword.trim());
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);

        return new HikariDataSource(config);
    }

    public static String convertToJdbcUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        String cleanUrl = rawUrl.trim();
        if (cleanUrl.startsWith("jdbc:")) {
            cleanUrl = cleanUrl.substring(5);
        }
        try {
            URI uri = URI.create(cleanUrl);
            String userInfo = uri.getUserInfo();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();

            if (userInfo != null && !userInfo.isEmpty()) {
                String[] parts = userInfo.split(":", 2);
                String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String password = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";

                StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
                jdbc.append(host);
                if (port > 0) {
                    jdbc.append(":").append(port);
                }
                if (path != null) {
                    jdbc.append(path);
                }
                jdbc.append("?user=").append(URLEncoder.encode(user, StandardCharsets.UTF_8));
                jdbc.append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8));
                if (query != null && !query.isEmpty()) {
                    jdbc.append("&").append(query);
                }
                return jdbc.toString();
            }
        } catch (Exception e) {
            logger.warn("Could not parse URI for credentials: {}", e.getMessage());
        }
        if (!rawUrl.startsWith("jdbc:")) {
            return "jdbc:" + rawUrl;
        }
        return rawUrl;
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
