package com.example.backend.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SqlValidationService {

    private static final Logger logger = LoggerFactory.getLogger(SqlValidationService.class);

    private static final List<String> DISALLOWED_KEYWORDS = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "ALTER", "DROP",
            "TRUNCATE", "CREATE", "GRANT", "REVOKE", "CALL", "DO", "EXECUTE",
            "COPY", "BEGIN", "COMMIT", "ROLLBACK", "RENAME", "VACUUM", "INTO"
    );

    /**
     * Validates that the generated SQL statement is strictly a single read-only SELECT query.
     */
    public ValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.failure("SQL query string is empty or null.");
        }

        String cleanedSql = sanitize(sql);

        // 1. Check multiple statements
        if (containsMultipleStatements(cleanedSql)) {
            logger.warn("SQL Validation failed: Multiple SQL statements detected");
            return ValidationResult.failure("Security Violation: Multiple SQL statements are not permitted.");
        }

        // 2. Check statement type (Must start with SELECT or WITH)
        String upperSql = cleanedSql.toUpperCase();
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("WITH")) {
            logger.warn("SQL Validation failed: Statement does not start with SELECT or WITH");
            return ValidationResult.failure("Security Violation: Only read-only SELECT queries are allowed.");
        }

        // 3. Reject disallowed keywords (word boundary check)
        for (String keyword : DISALLOWED_KEYWORDS) {
            // Regex to check whole word match ignoring string literal internals
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(cleanedSql).find()) {
                logger.warn("SQL Validation failed: Disallowed keyword '{}' detected in query", keyword);
                return ValidationResult.failure("Security Violation: Disallowed SQL operation '" + keyword + "' detected.");
            }
        }

        logger.info("SQL Validation succeeded for query");
        return ValidationResult.success();
    }

    private String sanitize(String sql) {
        // Remove trailing semicolons & leading/trailing whitespace
        String trimmed = sql.trim();
        // Remove block comments /* ... */ and line comments -- ...
        trimmed = trimmed.replaceAll("(?s)/\\*.*?\\*/", "");
        trimmed = trimmed.replaceAll("--.*?\n", "");
        trimmed = trimmed.replaceAll("--.*?$", "");
        return trimmed.trim();
    }

    private boolean containsMultipleStatements(String sql) {
        // Remove single trailing semicolon if present
        String trimmed = sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql;
        return trimmed.contains(";");
    }
}
