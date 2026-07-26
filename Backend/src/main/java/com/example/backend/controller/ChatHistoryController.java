package com.example.backend.controller;

import com.example.backend.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryController.class);
    private final JdbcTemplate jdbcTemplate;
    private final LlmService llmService;

    public ChatHistoryController(JdbcTemplate jdbcTemplate, LlmService llmService) {
        this.jdbcTemplate = jdbcTemplate;
        this.llmService = llmService;
    }

    @PostConstruct
    public void initChatTables() {
        try {
            logger.info("Initializing PostgreSQL chat_session and chat_message tables...");

            // 1. Create chat_session table if not exists
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_session (" +
                    "session_id VARCHAR(255) PRIMARY KEY, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "title VARCHAR(255) NOT NULL DEFAULT 'New Chat', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 2. Create chat_message table if not exists
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_message (" +
                    "id VARCHAR(255) PRIMARY KEY, " +
                    "session_id VARCHAR(255), " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "sender VARCHAR(50) NOT NULL, " +
                    "message_type VARCHAR(50) DEFAULT 'text', " +
                    "message_text TEXT, " +
                    "title VARCHAR(255), " +
                    "summary TEXT, " +
                    "sql_query TEXT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // 3. Ensure session_id column exists in chat_message
            try {
                jdbcTemplate.execute("ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS session_id VARCHAR(255)");
            } catch (Exception e) {
                logger.debug("Column session_id already exists in chat_message");
            }
        } catch (Exception e) {
            logger.warn("Could not auto-create chat tables: {}", e.getMessage());
        }
    }

    /**
     * GET /api/chat/sessions?userId={userId}
     * Returns list of chat sessions for user ordered by updated_at DESC.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getChatSessions(@RequestParam(defaultValue = "user-1") String userId) {
        logger.info("Fetching chat sessions summary for user: {}", userId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT session_id as \"sessionId\", user_id as \"userId\", title, " +
                            "created_at as \"createdAt\", updated_at as \"updatedAt\" " +
                            "FROM chat_session WHERE user_id = ? ORDER BY updated_at DESC",
                    userId);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            logger.warn("Error querying chat_session table: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * GET /api/chat/sessions/{sessionId}
     * Returns complete session details and message history ordered by created_at ASC.
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getChatSessionDetails(@PathVariable String sessionId) {
        logger.info("Fetching details and message history for session: {}", sessionId);
        try {
            List<Map<String, Object>> sessionRows = jdbcTemplate.queryForList(
                    "SELECT session_id as \"sessionId\", user_id as \"userId\", title, " +
                            "created_at as \"createdAt\", updated_at as \"updatedAt\" " +
                            "FROM chat_session WHERE session_id = ?", sessionId);

            if (sessionRows.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> session = new HashMap<>(sessionRows.get(0));

            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                    "SELECT id, session_id as \"sessionId\", user_id as \"userId\", sender, " +
                            "message_type as type, message_text as \"messageText\", title, summary, " +
                            "sql_query as sql, created_at as \"createdAt\" " +
                            "FROM chat_message WHERE session_id = ? ORDER BY created_at ASC",
                    sessionId);

            session.put("messages", messages);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            logger.error("Error retrieving session details for {}: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST /api/chat/sessions
     * Creates a new empty ChatSession.
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createChatSession(@RequestBody(required = false) Map<String, Object> body) {
        String userId = (body != null && body.containsKey("userId")) ? (String) body.get("userId") : "user-1";
        String sessionId = "session-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5);
        String title = (body != null && body.containsKey("title")) ? (String) body.get("title") : "New Chat";

        logger.info("Creating new ChatSession {} for user {}", sessionId, userId);
        try {
            jdbcTemplate.update(
                    "INSERT INTO chat_session (session_id, user_id, title, created_at, updated_at, last_message_at) " +
                            "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    sessionId, userId, title);
        } catch (Exception e) {
            logger.error("Failed to insert new chat_session: {}", e.getMessage());
        }

        Map<String, Object> res = new HashMap<>();
        res.put("sessionId", sessionId);
        res.put("userId", userId);
        res.put("title", title);
        res.put("createdAt", new Date());
        res.put("updatedAt", new Date());
        return ResponseEntity.ok(res);
    }

    /**
     * DELETE /api/chat/sessions/{sessionId}
     * Deletes a chat session and all its messages.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteChatSession(@PathVariable String sessionId) {
        logger.info("Deleting ChatSession {}", sessionId);
        try {
            jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
            jdbcTemplate.update("DELETE FROM chat_session WHERE session_id = ?", sessionId);
        } catch (Exception e) {
            logger.warn("Error deleting session {}: {}", sessionId, e.getMessage());
        }

        Map<String, String> res = new HashMap<>();
        res.put("status", "DELETED");
        res.put("sessionId", sessionId);
        return ResponseEntity.ok(res);
    }

    /**
     * Legacy GET /api/chat/history/{userId} (for backwards compatibility)
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getChatHistory(@PathVariable String userId) {
        logger.info("Fetching chat history for user: {}", userId);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, session_id as \"sessionId\", user_id as \"userId\", sender, " +
                            "message_type as type, message_text as \"messageText\", title, summary, " +
                            "sql_query as sql, created_at as \"createdAt\" " +
                            "FROM chat_message WHERE user_id = ? ORDER BY created_at ASC",
                    userId);
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            logger.warn("Error querying chat_message table: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * POST /api/chat/message
     * Saves a message under a ChatSession. Automatically generates AI title asynchronously on first user message.
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> saveChatMessage(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "msg-" + System.currentTimeMillis());
        String sessionId = (String) body.get("sessionId");
        String userId = (String) body.getOrDefault("userId", "user-1");
        String sender = (String) body.getOrDefault("sender", "user");
        String type = (String) body.getOrDefault("type", "text");
        String messageText = (String) body.getOrDefault("messageText", (String) body.get("answer"));
        String title = (String) body.get("title");
        String summary = (String) body.get("summary");
        String sql = (String) body.get("sql");

        // Ensure session exists
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "session-" + System.currentTimeMillis();
            try {
                jdbcTemplate.update(
                        "INSERT INTO chat_session (session_id, user_id, title, created_at, updated_at, last_message_at) " +
                                "VALUES (?, ?, 'New Chat', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                                "ON CONFLICT (session_id) DO NOTHING",
                        sessionId, userId);
            } catch (Exception e) {
                logger.warn("Could not auto-create session {}: {}", sessionId, e.getMessage());
            }
        }

        // Save message
        try {
            jdbcTemplate.update(
                    "INSERT INTO chat_message (id, session_id, user_id, sender, message_type, message_text, title, summary, sql_query, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (id) DO NOTHING",
                    id, sessionId, userId, sender, type, messageText, title, summary, sql);

            // Update session timestamp
            jdbcTemplate.update(
                    "UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP, last_message_at = CURRENT_TIMESTAMP WHERE session_id = ?",
                    sessionId);
        } catch (Exception e) {
            logger.warn("Could not save chat message to DB: {}", e.getMessage());
        }

        // Check if session title needs to be generated by AI (First user message in session)
        String currentSessionTitle = "New Chat";
        try {
            List<String> titles = jdbcTemplate.queryForList("SELECT title FROM chat_session WHERE session_id = ?", String.class, sessionId);
            if (!titles.isEmpty()) {
                currentSessionTitle = titles.get(0);
            }
        } catch (Exception e) {
            logger.debug("Could not query session title: {}", e.getMessage());
        }

        boolean isUserSender = "user".equalsIgnoreCase(sender) || "USER".equalsIgnoreCase(sender);

        if (isUserSender && ("New Chat".equalsIgnoreCase(currentSessionTitle) || currentSessionTitle == null)) {
            // Generate AI Title for session using Gemini AI asynchronously in background
            final String finalSessionId = sessionId;
            final String finalQuestion = messageText;
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Generating async AI title for session {}...", finalSessionId);
                    String aiTitle = llmService.generateChatTitle(finalQuestion);
                    if (aiTitle != null && !aiTitle.isBlank()) {
                        jdbcTemplate.update("UPDATE chat_session SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE session_id = ?", aiTitle, finalSessionId);
                        logger.info("Async updated ChatSession {} title to '{}'", finalSessionId, aiTitle);
                    }
                } catch (Exception e) {
                    logger.warn("Async AI title generation error for session {}: {}", finalSessionId, e.getMessage());
                }
            });
        }

        Map<String, Object> res = new HashMap<>();
        res.put("status", "SAVED");
        res.put("sessionId", sessionId);
        res.put("title", currentSessionTitle);
        return ResponseEntity.ok(res);
    }
}
