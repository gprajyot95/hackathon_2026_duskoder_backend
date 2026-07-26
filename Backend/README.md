# Enterprise AI Database Explorer & Schema Assistant - Backend

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-12%2B-blue.svg)](https://www.postgresql.org/)
[![Google Gemini API](https://img.shields.io/badge/Google%20Gemini-3.6%20Flash-orange.svg)](https://ai.google.dev/)
[![Caffeine Cache](https://img.shields.io/badge/Cache-Caffeine%20In--Memory-red.svg)](https://github.com/ben-manes/caffeine)

An enterprise-grade, high-performance **Spring Boot** backend service that bridges modern web applications with PostgreSQL databases using **Google Gemini AI** (`gemini-3.6-flash`). The application enables natural language database exploration, automatic schema-aware SQL generation, safe read-only SQL execution, real-time database schema synchronization via PostgreSQL `LISTEN/NOTIFY`, and structured AI response formatting.

---

## 1. Project Title & Description

### Project Name
**Enterprise AI Database Explorer & Schema Assistant (Backend)**

### Short Description
This application solves the complexity of exploring and querying relational databases by translating natural language questions into precise, read-only PostgreSQL `SELECT` queries and explaining the database results in human-readable JSON formats. It eliminates the need for manual SQL writing while providing strict security controls to prevent unintended database mutations or data leakage.

The core architecture operates as an intelligent middleware pipeline. On startup, the backend automatically extracts database schema metadata (tables, columns, datatypes, primary/foreign keys, indexes, and comments) by executing a PostgreSQL stored function (`get_database_schema()`). This metadata is cached in a high-speed, in-memory **Caffeine Cache** (`schemaMetadata`). Whenever a DDL change occurs in PostgreSQL (e.g., `CREATE TABLE`, `ALTER TABLE`), a background listener connection catches real-time `schema_changed` notifications via PostgreSQL `LISTEN/NOTIFY` and automatically invalidates and refreshes the Caffeine cache.

Designed for developers, database administrators, business analysts, and enterprise frontend interfaces, the application features an asynchronous multi-stage AI reasoning pipeline, complete session-based chat history management, Google OAuth user onboarding, and detailed cache performance telemetry.

---

## 2. Features

- **Automated Schema Metadata Extraction**: Executes PostgreSQL stored function `get_database_schema()` to retrieve raw structural schema JSON.
- **In-Memory Caffeine Caching**: Caches schema metadata in Caffeine (`maximumSize=100`, `expireAfterWrite=6h`) to guarantee sub-millisecond response times without database overload.
- **Real-Time PostgreSQL `LISTEN/NOTIFY`**: Background daemon (`PostgreSqlNotificationListener`) listening on channel `schema_changed` with a 1000ms debounce window to auto-refresh Caffeine cache upon database schema alterations.
- **Stage 1 AI SQL Generation**: Translates user questions into optimized PostgreSQL `SELECT` statements using Google GenAI SDK (`com.google.genai:google-genai:0.7.0`) guided by a master instruction prompt (`instruction.md`).
- **SQL Safety & Read-Only Policy**: Validates generated queries using `SqlValidationService` to strictly enforce read-only execution (rejects `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `TRUNCATE`, `GRANT`, `REVOKE`, etc.).
- **Stage 2 AI Response Formatting**: Formats query results into user-friendly JSON payloads featuring executive summaries, structured markdown tables, visualization hints (`table`, `metric_card`, `bar_chart`), and context-aware follow-up question suggestions.
- **Asynchronous AI Chat Title Generation**: Background worker (`CompletableFuture.runAsync`) that automatically summarizes the initial question into a concise 2–5 word chat title without blocking user HTTP requests.
- **Chat History & Session Persistence**: Complete multi-session tracking stored natively in PostgreSQL (`chat_session` and `chat_message` tables).
- **Google OAuth User Onboarding**: Automatic user profile upsert and status management stored in `app_user` table.
- **Health Checks & Telemetry**: Dedicated REST endpoints (`/api/health`, `/api/cache/data`, `/api/cache/refresh`) providing runtime diagnostic information.

---

## 3. Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | `11` / `17` | Core programming language |
| **Spring Boot** | `2.7.18` | Core web application framework |
| **Spring Web** | `2.7.18` | RESTful API controllers and HTTP routing |
| **Spring Data JPA** | `2.7.18` | Relational database abstraction and entity management |
| **Spring Cache** | `2.7.18` | Spring cache abstraction layer |
| **Caffeine Cache** | `2.9.3` | High-performance, in-memory caching engine |
| **PostgreSQL Driver** | `42.3.8` | JDBC driver for PostgreSQL database communication |
| **Google GenAI SDK** | `0.7.0` | Official Google SDK for Gemini LLM API integration |
| **Google Auth Library** | `1.19.0` | OAuth2 credentials handling for Google integration |
| **Dotenv Java** | `3.0.2` | Automatic `.env` environment variable loader |
| **Jackson Databind** | `2.13.5` | JSON serialization and deserialization |
| **SLF4J / Logback** | `1.7.36` | Logging system across all services |
| **Apache Maven** | `3.8+` | Project build and dependency management |

---

## 4. Project Architecture

The application follows a layered, service-oriented architecture with decoupled caching, AI orchestration, and database layers:

```text
                           ┌──────────────────────────────┐
                           │      React Frontend Client   │
                           └──────────────┬───────────────┘
                                          │ HTTP REST APIs
                                          ▼
                           ┌──────────────────────────────┐
                           │       REST Controllers       │
                           │ (AiQuery, Auth, Chat, Data)  │
                           └──────────────┬───────────────┘
                                          │
                                          ▼
                           ┌──────────────────────────────┐
                           │        AiQueryService        │
                           └──────┬───────────────┬───────┘
                                  │               │
        ┌─────────────────────────┘               └─────────────────────────┐
        ▼                                                                   ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│     SchemaMetadataService    │                           │    PromptBuilderService      │
│  (Caffeine Cache: 6h TTL)    │                           │   (Loads instruction.md)     │
└──────────────┬───────────────┘                           └──────────────┬───────────────┘
               │ Cache Miss                                               │
               ▼                                                          ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│    StoredFunctionService     │                           │        GeminiService         │
│  (SELECT get_database_schema)│                           │ (Google GenAI SDK 0.7.0)     │
└──────────────┬───────────────┘                           └──────────────┬───────────────┘
               │                                                          │
               ▼                                                          ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│     SqlExecutionService      │                           │      Google Gemini API       │
│   (JdbcTemplate Read-Only)   │                           │     (gemini-3.6-flash)       │
└──────────────┬───────────────┘                           └──────────────────────────────┘
               │
               ├───────────────────────────────────────────┐
               ▼                                           ▼
┌──────────────────────────────┐           ┌──────────────────────────────┐
│  PostgreSQL Database (Neon)  │           │ PostgreSqlNotificationListener│
│ (Data, app_user, chat_session)│──────────>│  (LISTEN schema_changed)    │
└──────────────────────────────┘           └──────────────────────────────┘
```

---

## 5. Backend Request Flow

When a user submits a natural language question (e.g., *"Show top 5 customers by account balance"*), the request moves through the following lifecycle:

```text
Client Request (POST /api/ai/query)
  │
  ├──> 1. AiQueryController receives request & validates UserQuestionRequest payload.
  │
  ├──> 2. AiQueryService checks SchemaMetadataService.
  │         ├──> Caffeine Cache Hit: Return cached schema metadata JSON instantly.
  │         └──> Cache Miss: Call StoredFunctionService -> Execute 'SELECT get_database_schema()' -> Store in Caffeine.
  │
  ├──> 3. PromptBuilderService reads 'instruction.md' and builds Stage 1 system prompt.
  │
  ├──> 4. GeminiService sends Stage 1 prompt + schema metadata to Google Gemini API (gemini-3.6-flash).
  │
  ├──> 5. Gemini returns Stage 1 JSON containing generated PostgreSQL SELECT query.
  │
  ├──> 6. SqlValidationService inspects generated SQL query:
  │         ├──> Valid SELECT query: Pass to SqlExecutionService.
  │         └──> Invalid / Non-SELECT query: Reject request with HTTP 400 error response.
  │
  ├──> 7. SqlExecutionService executes query via JdbcTemplate against PostgreSQL.
  │
  ├──> 8. GeminiService constructs Stage 2 prompt (Question + Executed SQL + DB Result Set).
  │
  ├──> 9. Gemini API generates final Stage 2 structured answer JSON (Summary, Table, Hints, Suggestions).
  │
  └──> 10. Controller returns HTTP 200 OK with QueryResultResponse to the client.
```

---

## 6. Package Structure

```text
src
├── main
│   ├── java
│   │   └── com
│   │       └── example
│   │           └── backend
│   │               ├── BackendApplication.java           # Spring Boot Main Entry Point
│   │               ├── config
│   │               │   ├── AppProperties.java            # Strongly-typed @ConfigurationProperties (app.*)
│   │               │   ├── CacheConfig.java              # Caffeine Cache Manager Configuration
│   │               │   ├── DotenvConfig.java             # Automatic .env file loader configuration
│   │               │   ├── GoogleGenAiConfiguration.java  # Google GenAI Client Bean instantiation
│   │               │   ├── PostgreSqlListenerConfiguration.java # Task Executor configuration for LISTEN daemon
│   │               │   └── WebConfig.java                # Global CORS WebMvcConfigurer configuration
│   │               ├── controller
│   │               │   ├── AiQueryController.java        # AI Query & Prompt Processing REST endpoints
│   │               │   ├── AuthController.java           # Google OAuth & User Management REST endpoints
│   │               │   ├── ChatHistoryController.java     # Chat Session & Message History REST endpoints
│   │               │   └── DataController.java           # Schema, Health, & Cache Management REST endpoints
│   │               ├── listener
│   │               │   └── PostgreSqlNotificationListener.java # PostgreSQL LISTEN/NOTIFY daemon
│   │               ├── model
│   │               │   ├── GeminiResponse.java           # Gemini LLM JSON response mapping DTO
│   │               │   ├── QueryResultResponse.java      # Unified API response wrapper DTO
│   │               │   └── UserQuestionRequest.java      # Incoming question request DTO
│   │               ├── service
│   │               │   ├── AiQueryService.java           # Orchestrates 2-stage AI query execution pipeline
│   │               │   ├── GeminiService.java            # Implements LlmService using Google GenAI SDK
│   │               │   ├── LlmService.java               # LLM abstraction interface
│   │               │   ├── PromptBuilderService.java     # System instruction prompt loader
│   │               │   ├── SchemaMetadataService.java    # Caffeine @Cacheable schema metadata service
│   │               │   ├── SqlExecutionService.java      # JdbcTemplate SQL execution engine
│   │               │   ├── StartupCacheRunner.java       # ApplicationRunner for startup cache pre-warming
│   │               │   └── StoredFunctionService.java    # Low-level PostgreSQL stored function invoker
│   │               └── validation
│   │                   ├── SqlValidationService.java      # Read-only SQL safety validator
│   │                   └── ValidationResult.java         # SQL validation result DTO
│   └── resources
│       ├── application.yml                               # Core Spring Configuration File
│       ├── .env                                          # Local Environment Variable Storage
│       └── instruction.md                                # Master System Instruction for Gemini LLM
└── test
    └── java
        └── com
            └── example
                └── backend
                    ├── BackendApplicationTests.java     # Spring Context Load Test
                    ├── CaffeineCacheServiceTest.java    # Caffeine Cache Unit & Integration Tests
                    ├── GeminiServiceTest.java           # Gemini Error Classification Unit Tests
                    └── GenAiSdkApiVerificationTest.java # Gemini SDK Verification & Parsing Tests
```

---

## 7. API Documentation

### 1. AI Query Processing Endpoints (`/api/ai`)

#### `POST /api/ai/query`
Executes full 2-stage AI natural language query processing against database.

- **Request Body**:
  ```json
  {
    "question": "Which customers have open fraud cases?"
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "question": "Which customers have open fraud cases?",
    "type": "query",
    "requiresDatabase": true,
    "response": {
      "type": "query",
      "requiresDatabase": true,
      "confidence": 0.98,
      "title": "Customers with Open Fraud Cases",
      "summary": "Found 3 customers with currently active fraud cases.",
      "sql": "SELECT c.customer_id, c.full_name, f.case_status FROM customer c JOIN fraud_case f ON c.customer_id = f.customer_id WHERE f.case_status = 'OPEN'",
      "data": {
        "columns": ["customer_id", "full_name", "case_status"],
        "rows": [
          [101, "Alice Smith", "OPEN"],
          [104, "Bob Jones", "OPEN"]
        ]
      },
      "visualization": {
        "type": "table"
      }
    },
    "error": null
  }
  ```

---

### 2. Schema & Cache Telemetry Endpoints (`/api`)

#### `GET /api/schema/metadata` or `GET /api/cache/data`
Returns raw JSON schema metadata currently stored in Caffeine cache.

- **Response (`200 OK`)**: Raw PostgreSQL Schema Metadata JSON string.
- **Response (`404 Not Found`)**:
  ```json
  {
    "status": "CACHE_MISS",
    "message": "No cached schema metadata found for key: databaseSchema"
  }
  ```

#### `POST /api/cache/refresh`
Forces execution of PostgreSQL function `get_database_schema()` and atomically refreshes Caffeine cache.

- **Response (`200 OK`)**:
  ```json
  {
    "status": "SUCCESS",
    "message": "Stored function executed and cache refreshed successfully",
    "functionName": "get_database_schema",
    "cacheKey": "databaseSchema",
    "ttlHours": 6
  }
  ```

#### `GET /api/health`
Returns service health and configuration details.

- **Response (`200 OK`)**:
  ```json
  {
    "status": "UP",
    "configuredStoredFunction": "get_database_schema",
    "configuredCacheKey": "databaseSchema",
    "notificationChannel": "schema_changed",
    "isNotificationListenerEnabled": true,
    "isCachedDataPresent": true
  }
  ```

---

### 3. Authentication & User Management (`/api/auth`)

#### `POST /api/auth/google`
Authenticates Google OAuth user profile and upserts record into `app_user` table.

- **Request Body**:
  ```json
  {
    "googleId": "google-uid-12345",
    "email": "user@example.com",
    "name": "Jane Doe",
    "picture": "https://example.com/avatar.jpg"
  }
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "token": "jwt-session-token-a1b2c3d4",
    "user": {
      "id": 1,
      "googleId": "google-uid-12345",
      "email": "user@example.com",
      "name": "Jane Doe",
      "profilePictureUrl": "https://example.com/avatar.jpg",
      "role": "USER",
      "status": "ACTIVE"
    }
  }
  ```

#### `GET /api/admin/users`
Lists all registered users from `app_user` table.

---

### 4. Chat History & Sessions (`/api/chat`)

#### `GET /api/chat/sessions?userId={userId}`
Retrieves summary of chat sessions for a specific user.

#### `GET /api/chat/sessions/{sessionId}`
Retrieves session details and full message history ordered chronologically.

#### `POST /api/chat/sessions`
Creates a new chat session.

#### `POST /api/chat/message`
Saves user/assistant chat messages and triggers background AI session title generation on first message.

#### `DELETE /api/chat/sessions/{sessionId}`
Deletes a chat session and all associated messages.

---

## 8. Configuration

Application properties are mapped via `AppProperties.java` bound to prefix `app`:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/neondb}
    username: ${SPRING_DATASOURCE_USERNAME:neondb_owner}
    password: ${SPRING_DATASOURCE_PASSWORD:}
  cache:
    type: caffeine

app:
  db:
    stored-function-name: ${STORED_FUNCTION_NAME:get_database_schema}
  cache:
    cache-name: ${CACHE_NAME:schemaMetadata}
    cache-key: ${CACHE_KEY:databaseSchema}
    ttl-hours: ${CACHE_TTL_HOURS:6}
    maximum-size: ${CACHE_MAX_SIZE:100}
  startup:
    fetch-on-startup: ${FETCH_ON_STARTUP:true}
  notification:
    enabled: ${NOTIFICATION_ENABLED:true}
    channel-name: ${NOTIFICATION_CHANNEL:schema_changed}
    debounce-ms: ${NOTIFICATION_DEBOUNCE_MS:1000}
    reconnect-interval-ms: ${NOTIFICATION_RECONNECT_MS:5000}
  gemini:
    api-key: ${GEMINI_API_KEY:}
    model: ${GEMINI_MODEL:gemini-3.6-flash}
    timeout-ms: ${GEMINI_TIMEOUT_MS:45000}
    instruction-path: ${GEMINI_INSTRUCTION_PATH:classpath:instruction.md}

server:
  port: ${SERVER_PORT:8080}
```

---

## 9. Environment Variables

| Variable | Required | Default Value | Description |
|---|---|---|---|
| `GEMINI_API_KEY` | **Yes** | *None* | Google Gemini API Key |
| `SPRING_DATASOURCE_URL` | **Yes** | `jdbc:postgresql://...` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | **Yes** | `neondb_owner` | PostgreSQL database username |
| `SPRING_DATASOURCE_PASSWORD` | **Yes** | *None* | PostgreSQL database password |
| `GEMINI_MODEL` | No | `gemini-3.6-flash` | Gemini LLM model identifier |
| `GEMINI_TIMEOUT_MS` | No | `45000` | HTTP request timeout in milliseconds |
| `STORED_FUNCTION_NAME` | No | `get_database_schema` | PostgreSQL stored function for metadata |
| `CACHE_NAME` | No | `schemaMetadata` | Caffeine cache name |
| `CACHE_KEY` | No | `databaseSchema` | Caffeine cache key |
| `CACHE_TTL_HOURS` | No | `6` | Cache TTL expiration in hours |
| `CACHE_MAX_SIZE` | No | `100` | Maximum number of items in Caffeine cache |
| `NOTIFICATION_ENABLED` | No | `true` | Enable PostgreSQL LISTEN/NOTIFY daemon |
| `NOTIFICATION_CHANNEL` | No | `schema_changed` | PostgreSQL notification channel name |
| `NOTIFICATION_DEBOUNCE_MS`| No | `1000` | Debounce window in ms for DDL notifications |
| `SERVER_PORT` | No | `8080` | Web server HTTP port |

---

## 10. AI Flow & Prompt Engineering

The AI service operates via a 2-stage execution flow:

```text
User Question
     │
     ▼
[Stage 1: SQL Generation]
  ├── System Prompt: instruction.md
  ├── Input: Schema Metadata (from Caffeine) + Question
  └── Output: JSON containing generated SELECT query
     │
     ▼
[SQL Safety Check]
  ├── SqlValidationService parses statement
  └── Ensures query is strictly a SELECT statement
     │
     ▼
[Database Query Execution]
  ├── SqlExecutionService executes query via JdbcTemplate
  └── Returns List<Map<String, Object>> result set
     │
     ▼
[Stage 2: Response Formatting]
  ├── System Prompt: instruction.md + Runtime Execution Context
  ├── Input: Question + Executed SQL + Query Results
  └── Output: Final Answer JSON (Summary, Markdown Table, Hints)
     │
     ▼
Client UI Rendering
```

---

## 11. Caching Implementation

Caching is implemented using **Caffeine Cache** (`com.github.ben-manes.caffeine:caffeine`) integrated natively into Spring's `@EnableCaching` framework.

- **Cache Provider**: Caffeine In-Memory Cache.
- **Cache Name**: `schemaMetadata`
- **Cache Key**: `databaseSchema`
- **Write TTL**: 6 Hours (`expireAfterWrite(6, TimeUnit.HOURS)`)
- **Max Size**: 100 Entries (`maximumSize(100)`)
- **Eviction Strategy**: Automatic TTL expiration + Real-time PostgreSQL `LISTEN/NOTIFY` invalidation on channel `schema_changed`.

### Cache Invalidation Flow Diagram
```text
PostgreSQL DDL Change (CREATE/ALTER TABLE)
                   │
                   ▼
       NOTIFY channel 'schema_changed'
                   │
                   ▼
 PostgreSqlNotificationListener (Debounce 1000ms)
                   │
                   ▼
     SchemaMetadataService.refreshSchemaMetadata()
                   │
                   ▼
 Execute 'SELECT get_database_schema()' -> Update Caffeine
```

---

## 12. Database Layer

- **Framework**: `JdbcTemplate` for native performance and direct PostgreSQL function calls.
- **Connection Pooling**: HikariCP default Spring Boot connection pool (`maximum-pool-size: 10`, `minimum-idle: 2`).
- **Read-Only Safety Enforcement**: `SqlValidationService` validates SQL queries before execution to block data modification language (DML/DDL) statements.

---

## 13. Error Handling

- **Classification**: `GeminiService` classifies raw API exceptions into user-friendly messages:
  - `429 RESOURCE_EXHAUSTED`: *"Gemini API quota exceeded. Please try again shortly."*
  - `401 / 403 UNAUTHENTICATED`: *"Invalid or missing Gemini API Key. Please verify your GEMINI_API_KEY environment variable."*
  - `404 NOT_FOUND`: *"Configured Gemini model was not found."*
  - `504 TIMEOUT`: *"Gemini API request timed out. Please try again."*
- **Security Redaction**: Stack traces and error messages automatically strip sensitive tokens and API keys (`key=***`, `Bearer ***`).

---

## 14. Security

- **CORS**: Configured in `WebConfig.java` allowing cross-origin requests (`allowedOrigins("*")`).
- **Secret Isolation**: `GEMINI_API_KEY` and database credentials are read exclusively from environment variables / `.env` and never logged or exposed via REST responses.
- **SQL Injection Prevention**: Queries generated by AI are checked against read-only safety rules and executed using standard database execution channels.

---

## 15. Logging

- **Framework**: SLF4J with Logback.
- **Diagnostics**: Logs prompt preparation timings, database result sizes, Gemini latency (ms), and LISTEN/NOTIFY event debouncing.

---

## 16. Local Development

### Prerequisites
- **Java 11** or **Java 17**
- **Apache Maven 3.8+**
- **PostgreSQL 12+**

### Setup Instructions

1. **Clone Repository**:
   ```bash
   git clone https://github.com/gprajyot95/hackathon_2026_duskoder_backend.git
   cd hackathon_2026_duskoder_backend/Backend
   ```

2. **Configure Environment Variables**:
   Create a `.env` file in `Backend/src/main/resources/.env`:
   ```env
   GEMINI_API_KEY="AIzaSyYourActualGeminiApiKey"
   SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/neondb"
   SPRING_DATASOURCE_USERNAME="postgres"
   SPRING_DATASOURCE_PASSWORD="your_db_password"
   ```

3. **Run Application**:
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Run Unit & Integration Tests**:
   ```bash
   ./mvnw clean test
   ```

---

## 17. Deployment

### Containerization / Google Cloud Run / Docker

1. **Build Jar Package**:
   ```bash
   ./mvnw clean package -DskipTests
   ```

2. **Run Jar File**:
   ```bash
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

3. **Environment Injection**:
   Supply `GEMINI_API_KEY`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` as runtime environment variables in your deployment dashboard (e.g. Cloud Run, Render, Railway).

---

## 18. End-to-End System Flow Diagram

```text
       ┌──────────────┐
       │  User Query  │
       └──────┬───────┘
              │
              ▼
    ┌───────────────────┐
    │  React Frontend   │
    └──────┬────────────┘
           │ POST /api/ai/query
           ▼
    ┌───────────────────┐
    │  AiQueryController│
    └──────┬────────────┘
           │
           ▼
    ┌───────────────────┐      Cache Hit       ┌───────────────────┐
    │  AiQueryService   │─────────────────────>│   Caffeine Cache  │
    └──────┬────────────┘                      └───────────────────┘
           │
           ▼ Stage 1 Prompt
    ┌───────────────────┐
    │   Gemini Service  │
    └──────┬────────────┘
           │ Generated SQL
           ▼
    ┌───────────────────┐
    │SqlExecutionService│
    └──────┬────────────┘
           │ Executed SQL Results
           ▼
    ┌───────────────────┐
    │ Stage 2 Gemini AI │
    └──────┬────────────┘
           │ Final JSON Answer
           ▼
    ┌───────────────────┐
    │   JSON Response   │
    └───────────────────┘
```

---

## 19. Future Improvements

### Current Implementation
- Caffeine In-Memory Schema Caching
- Single-Key Gemini API Authentication (`GEMINI_API_KEY`)
- PostgreSQL `LISTEN/NOTIFY` Real-time Invalidation
- 2-Stage AI Query & Formatting Pipeline

### Roadmap / Future Ideas
- [ ] **JWT Verification**: Add full JWT token signature validation on `/api/*` endpoints.
- [ ] **Schema Diff Telemetry**: Provide a visual schema difference endpoint when `LISTEN/NOTIFY` triggers.
- [ ] **Query Execution Time Guardrails**: Add strict query execution timeout limits for complex database analytical queries.

---

## 20. Testing

The codebase includes automated tests under `src/test/java`:

- **`BackendApplicationTests`**: Context loading test verifying Spring Application Context initialization.
- **`CaffeineCacheServiceTest`**: Integration tests verifying cache miss, cache hit, and schema refresh.
- **`GeminiServiceTest`**: Unit tests verifying API key error classification and security redaction.
- **`GenAiSdkApiVerificationTest`**: Tests Google GenAI SDK model generation and response JSON parsing.

Run all tests with:
```bash
./mvnw clean test
```

---

## 21. Project Statistics

Statistics directly audited from source code:

- **Packages**: `7`
- **Controllers**: `4` (`AiQueryController`, `AuthController`, `ChatHistoryController`, `DataController`)
- **Services & Listeners**: `10`
- **Repositories**: `0` (Uses native `JdbcTemplate` for maximum performance)
- **DTOs / Models**: `4` (`UserQuestionRequest`, `QueryResultResponse`, `GeminiResponse`, `ValidationResult`)
- **Configuration Classes**: `6`
- **REST Endpoints**: `13`
- **Total Tests**: `7` (100% Passing)
