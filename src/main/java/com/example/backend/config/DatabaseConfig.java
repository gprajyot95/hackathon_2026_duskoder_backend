package com.example.backend.config;

import java.net.URI;

public class DatabaseConfig {

    public static class DbCredentials {
        private final String jdbcUrl;
        private final String username;
        private final String password;

        public DbCredentials(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    public static DbCredentials resolveCredentials(String rawUrl, String rawUsername, String rawPassword) {
        String url = rawUrl != null ? rawUrl.trim() : "";
        String username = rawUsername != null ? rawUsername.trim() : "";
        String password = rawPassword != null ? rawPassword.trim() : "";

        String extractedUser = null;
        String extractedPass = null;

        // Check if credentials are embedded in URL (e.g. postgresql://user:pass@host/db or jdbc:postgresql://user:pass@host/db)
        String cleanUrl = url;
        if (cleanUrl.startsWith("jdbc:")) {
            cleanUrl = cleanUrl.substring(5);
        }

        if (cleanUrl.startsWith("postgresql://") || cleanUrl.startsWith("postgres://")) {
            try {
                URI uri = new URI(cleanUrl);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    extractedUser = parts[0];
                    extractedPass = parts[1];
                }

                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();

                StringBuilder jdbcBuilder = new StringBuilder("jdbc:postgresql://");
                if (host != null) jdbcBuilder.append(host);
                if (port > 0) jdbcBuilder.append(":").append(port);
                if (path != null) jdbcBuilder.append(path);
                if (query != null) jdbcBuilder.append("?").append(query);

                url = jdbcBuilder.toString();
            } catch (Exception ignored) {
            }
        }

        if (!url.startsWith("jdbc:postgresql://") && !url.startsWith("jdbc:")) {
            url = "jdbc:postgresql://" + url;
        }

        if (username.isEmpty() && extractedUser != null) {
            username = extractedUser;
        }
        if (password.isEmpty() && extractedPass != null) {
            password = extractedPass;
        }

        // Default fallbacks if needed
        if (username.isEmpty()) {
            username = "neondb_owner";
        }
        if (password.isEmpty()) {
            password = "npg_8btGxWBV5DCp";
        }

        return new DbCredentials(url, username, password);
    }
}
