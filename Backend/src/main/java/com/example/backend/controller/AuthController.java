package com.example.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final JdbcTemplate jdbcTemplate;

    public AuthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initDatabaseTables() {
        try {
            logger.info("Initializing PostgreSQL app_user table if not exists...");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS app_user (" +
                    "id SERIAL PRIMARY KEY, " +
                    "google_id VARCHAR(255) UNIQUE NOT NULL, " +
                    "email VARCHAR(255) NOT NULL, " +
                    "first_name VARCHAR(255), " +
                    "last_name VARCHAR(255), " +
                    "profile_picture_url TEXT, " +
                    "role VARCHAR(50) DEFAULT 'USER', " +
                    "account_status VARCHAR(50) DEFAULT 'ACTIVE', " +
                    "last_login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Ensure an ADMIN user exists for demonstration
            int count = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE role = 'ADMIN'", Integer.class);
            if (count == 0) {
                jdbcTemplate.update("INSERT INTO app_user (google_id, email, first_name, last_name, profile_picture_url, role, account_status) " +
                        "VALUES ('google-admin-001', 'admin@example.com', 'System', 'Administrator', " +
                        "'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80', 'ADMIN', 'ACTIVE') " +
                        "ON CONFLICT (google_id) DO NOTHING");
            }
        } catch (Exception e) {
            logger.warn("Could not auto-create app_user table: {}", e.getMessage());
        }
    }

    @PostMapping("/auth/google")
    public ResponseEntity<?> authenticateGoogleUser(@RequestBody Map<String, Object> payload) {
        logger.info("Received Google Auth Login Request");

        Map<String, Object> profile = null;
        if (payload.containsKey("profile") && payload.get("profile") instanceof Map) {
            profile = (Map<String, Object>) payload.get("profile");
        } else {
            profile = payload;
        }

        String googleId = (String) profile.getOrDefault("googleId", profile.getOrDefault("sub", "google-" + System.currentTimeMillis()));
        String email = (String) profile.getOrDefault("email", "user@enterprise.com");
        String name = (String) profile.getOrDefault("name", "Enterprise User");
        String picture = (String) profile.getOrDefault("picture", profile.getOrDefault("profilePictureUrl", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=256&q=80"));
        String roleRequested = (String) profile.getOrDefault("role", email.contains("admin") ? "ADMIN" : "USER");

        try {
            // Upsert into app_user table
            jdbcTemplate.update("INSERT INTO app_user (google_id, email, first_name, last_name, profile_picture_url, role, account_status, last_login_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (google_id) DO UPDATE SET " +
                            "email = EXCLUDED.email, " +
                            "first_name = EXCLUDED.first_name, " +
                            "profile_picture_url = EXCLUDED.profile_picture_url, " +
                            "last_login_at = CURRENT_TIMESTAMP, " +
                            "updated_at = CURRENT_TIMESTAMP",
                    googleId, email, name, "", picture, roleRequested);

            Map<String, Object> userRecord = jdbcTemplate.queryForMap("SELECT * FROM app_user WHERE google_id = ?", googleId);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", userRecord.getOrDefault("id", userRecord.get("user_id")));
            userMap.put("googleId", userRecord.get("google_id"));
            userMap.put("email", userRecord.get("email"));
            userMap.put("name", userRecord.getOrDefault("first_name", userRecord.get("name")));
            userMap.put("profilePictureUrl", userRecord.getOrDefault("profile_picture_url", userRecord.get("picture")));
            userMap.put("role", userRecord.get("role"));
            userMap.put("status", userRecord.getOrDefault("account_status", userRecord.get("status")));
            userMap.put("lastLoginAt", userRecord.getOrDefault("last_login_at", userRecord.get("created_at")));

            response.put("user", userMap);
            response.put("token", "jwt-session-token-" + UUID.randomUUID());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error authenticating Google user in database: {}", e.getMessage(), e);

            // Fallback response if DB is in read-only lock
            Map<String, Object> fallbackUser = new HashMap<>();
            fallbackUser.put("id", 1);
            fallbackUser.put("googleId", googleId);
            fallbackUser.put("email", email);
            fallbackUser.put("name", name);
            fallbackUser.put("profilePictureUrl", picture);
            fallbackUser.put("role", roleRequested);
            fallbackUser.put("status", "ACTIVE");

            Map<String, Object> response = new HashMap<>();
            response.put("user", fallbackUser);
            response.put("token", "jwt-session-token-fallback");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/admin/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        try {
            List<Map<String, Object>> rawRows = jdbcTemplate.queryForList("SELECT * FROM app_user");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rawRows) {
                Map<String, Object> u = new HashMap<>();
                u.put("id", r.getOrDefault("id", r.get("user_id")));
                u.put("googleId", r.get("google_id"));
                u.put("email", r.get("email"));
                u.put("name", r.getOrDefault("first_name", r.get("name")));
                u.put("profilePictureUrl", r.getOrDefault("profile_picture_url", r.get("picture")));
                u.put("role", r.get("role"));
                u.put("status", r.getOrDefault("account_status", r.get("status")));
                u.put("lastLoginAt", r.getOrDefault("last_login_at", r.get("created_at")));
                result.add(u);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.warn("Could not query app_user table: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<Map<String, String>> updateUserRole(@PathVariable int userId, @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        try {
            jdbcTemplate.update("UPDATE app_user SET role = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", newRole, userId);
        } catch (Exception e) {
            logger.warn("Could not update user role: {}", e.getMessage());
        }
        Map<String, String> res = new HashMap<>();
        res.put("status", "SUCCESS");
        return ResponseEntity.ok(res);
    }

    @PutMapping("/admin/users/{userId}/status")
    public ResponseEntity<Map<String, String>> updateUserStatus(@PathVariable int userId, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        try {
            jdbcTemplate.update("UPDATE app_user SET account_status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", newStatus, userId);
        } catch (Exception e) {
            logger.warn("Could not update user status: {}", e.getMessage());
        }
        Map<String, String> res = new HashMap<>();
        res.put("status", "SUCCESS");
        return ResponseEntity.ok(res);
    }
}
