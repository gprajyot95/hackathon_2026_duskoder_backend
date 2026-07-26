# Backend Service - Spring Boot, Neon PostgreSQL, Caffeine Cache & Google Gemini AI

This directory contains the production-ready Spring Boot (Java 11) backend microservice.

---

## 1. Overview & Architecture

1. **Database Connection & Caching**: On startup, connects to **Neon PostgreSQL** and executes `SELECT * FROM get_database_schema()`. Caches the JSON schema string into **Caffeine Cache (In-Memory)** under key `databaseSchema`.
2. **Real-time Schema Listener**: Listens continuously on PostgreSQL channel `schema_changed` via `PostgreSqlNotificationListener` using PostgreSQL `LISTEN/NOTIFY`.
3. **Two-Stage Gemini AI Pipeline**: Exposes `POST /api/ai/query` allowing users to query database schema and records using natural language via Google Gemini AI (`gemini-3.6-flash`).
4. **Read-Only SQL Validation**: Validates all AI-generated SQL queries via `SqlValidationService` (strictly single `SELECT` statements; blocks DML/DDL) before execution.
5. **ChatGPT-Style Chat Sessions**: Manages persistent user conversation sessions in PostgreSQL (`chat_session` & `chat_message` tables) with non-blocking asynchronous AI title generation.

---

## 2. Configuration & Environment Variables

| Property / Environment Variable | Default Value | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `<configured via env>` | Neon PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `<configured via env>` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `<configured via env>` | Database password |
| `GEMINI_API_KEY` | `<configured via env>` | Google Gemini API Authentication Key |
| `GEMINI_MODEL` | `gemini-3.6-flash` | Gemini LLM model identifier |
| `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com/v1beta/models` | Gemini REST API base endpoint |
| `GEMINI_TIMEOUT_MS` | `45000` | HTTP client timeout in milliseconds |
| `GEMINI_INSTRUCTION_PATH` | `classpath:instruction.md` | Path to system instruction file |
| `STORED_FUNCTION_NAME` | `get_database_schema` | PostgreSQL schema metadata function |
| `CACHE_NAME` | `schemaMetadata` | Caffeine cache name |
| `CACHE_KEY` | `databaseSchema` | Cache key used in Caffeine |
| `CACHE_TTL_HOURS` | `6` | Caffeine cache TTL in hours |
| `CACHE_MAX_SIZE` | `100` | Caffeine maximum cache size |
| `NOTIFICATION_ENABLED` | `true` | Enable/disable LISTEN/NOTIFY listener |
| `NOTIFICATION_CHANNEL` | `schema_changed` | PostgreSQL notification channel |
| `SERVER_PORT` | `8080` | Web server HTTP port |

---

## 3. Database Schema & Data Models

### `chat_session` Table
```sql
CREATE TABLE IF NOT EXISTS chat_session (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT 'New Chat',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### `chat_message` Table
```sql
CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) REFERENCES chat_session(session_id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    sender VARCHAR(50) NOT NULL,
    message_type VARCHAR(50) DEFAULT 'text',
    message_text TEXT,
    title VARCHAR(255),
    summary TEXT,
    sql_query TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 4. REST API Endpoints

### AI Query Pipeline
- **`POST /api/ai/query`**
  - Accepts natural language user prompt (`{"question": "...", "userId": "..."}`).
  - Runs Two-Stage Gemini processing pipeline.
  - Returns structured query explanation, executed SQL, and dataset output.

### Chat Session Management
- **`GET /api/chat/sessions?userId={userId}`**
  - Returns list of conversation summaries for user ordered by `updated_at DESC`.
- **`GET /api/chat/sessions/{sessionId}`**
  - Returns complete session details and message history ordered by `created_at ASC`.
- **`POST /api/chat/sessions`**
  - Creates a new empty `ChatSession` record.
- **`DELETE /api/chat/sessions/{sessionId}`**
  - Permanently deletes a chat session and all its messages.
- **`POST /api/chat/message`**
  - Saves a user or assistant message to `chat_message`.
  - Triggers non-blocking asynchronous AI title generation via `CompletableFuture.runAsync` on first user prompt.

### Cache & System Health
- **`GET /api/cache/data`**: Returns cached JSON database schema metadata.
- **`POST /api/cache/refresh`**: Invokes PostgreSQL schema function and updates Caffeine cache.
- **`GET /api/health`**: Service health & Caffeine cache status.

---

## 5. Two-Stage AI Processing Pipeline

1. **Stage 1 (Query Generation Mode)**:
   - Evaluates user question against cached schema metadata and `instruction.md`.
   - Answers schema questions (`type=text`, `requiresDatabase=false`) directly without touching PostgreSQL.
2. **SQL Validation & Execution**:
   - Validates generated SQL (`SqlValidationService`, strictly single `SELECT`).
   - Executes valid query on Neon PostgreSQL (`SqlExecutionService`).
3. **Stage 2 (Response Generation Mode)**:
   - Passes executed query results to Gemini along with dynamic runtime context to produce human-friendly explanations matching `instruction.md`.
   - Raw database rows are formatted and never exposed directly to the client.

---

## 6. Asynchronous AI Chat Title Generation

- On the first user message of a new chat session (`"New Chat"`), the backend calls `llmService.generateChatTitle(userQuestion)`.
- The AI prompt asks Gemini for a **concise 2–5 word title** (e.g., `"Admin Users"`, `"Invoice Workflow"`).
- Execution is wrapped in `CompletableFuture.runAsync(...)`, allowing `POST /api/chat/message` to return instantly in **~5ms** while title generation completes in the background.
- Fallback logic extracts 4 clean capitalized words if the AI API is offline.

---

## 7. How to Start & Stop Service

### Starting the Service
```bash
cd Backend
./mvnw spring-boot:run
```

### Building Packaged JAR
```bash
cd Backend
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### Stopping the Service
```bash
# Terminate process listening on port 8080
fuser -k 8080/tcp
```
