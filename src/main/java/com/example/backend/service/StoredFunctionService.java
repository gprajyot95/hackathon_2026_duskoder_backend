package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StoredFunctionService {

    private static final Logger logger = LoggerFactory.getLogger(StoredFunctionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StoredFunctionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a PostgreSQL stored function (e.g., SELECT * FROM get_database_schema())
     * and returns the JSON output string.
     *
     * @param functionName Name of the stored function to execute
     * @return String representation of the JSON returned by the function
     */
    public String callStoredFunction(String functionName) {
        logger.info("Executing PostgreSQL stored function: SELECT * FROM {}()", functionName);
        String sqlSelectAll = String.format("SELECT * FROM %s()", functionName);
        
        try {
            // First attempt: direct string output
            String jsonResult = jdbcTemplate.queryForObject(sqlSelectAll, String.class);
            if (jsonResult != null && !jsonResult.isBlank()) {
                logger.info("Successfully fetched direct string result from '{}'", functionName);
                return jsonResult;
            }
        } catch (Exception e) {
            logger.info("Direct string queryForObject for '{}' returned exception: {}. Trying result map/list parsing...", 
                    functionName, e.getMessage());
        }

        try {
            // Second attempt: query for map list and serialize to JSON
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlSelectAll);
            if (!rows.isEmpty()) {
                if (rows.size() == 1 && rows.get(0).size() == 1) {
                    Object singleValue = rows.get(0).values().iterator().next();
                    if (singleValue instanceof String) {
                        return (String) singleValue;
                    }
                    return objectMapper.writeValueAsString(singleValue);
                }
                String jsonResult = objectMapper.writeValueAsString(rows);
                logger.info("Successfully fetched and serialized row data from '{}'", functionName);
                return jsonResult;
            }
        } catch (Exception e) {
            logger.warn("Query for list on '{}' failed: {}. Trying 'SELECT {}()'", functionName, e.getMessage(), functionName);
        }

        try {
            // Third attempt: SELECT functionName()
            String sqlSelectFunc = String.format("SELECT %s()", functionName);
            String jsonResult = jdbcTemplate.queryForObject(sqlSelectFunc, String.class);
            logger.info("Successfully fetched result via 'SELECT {}()'", functionName);
            return jsonResult;
        } catch (Exception ex) {
            logger.error("Error executing PostgreSQL function '{}': {}", functionName, ex.getMessage(), ex);
            throw new RuntimeException("Failed to call PostgreSQL stored function: " + functionName, ex);
        }
    }

    /**
     * Executes a PostgreSQL stored function with parameters.
     */
    public String callStoredFunctionWithParams(String functionName, Object... params) {
        logger.info("Executing PostgreSQL stored function: {} with {} parameters", functionName, params.length);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) placeholders.append(", ");
            placeholders.append("?");
        }
        String sql = String.format("SELECT * FROM %s(%s)", functionName, placeholders.toString());
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            if (!rows.isEmpty()) {
                if (rows.size() == 1 && rows.get(0).size() == 1) {
                    Object val = rows.get(0).values().iterator().next();
                    return val instanceof String ? (String) val : objectMapper.writeValueAsString(val);
                }
                return objectMapper.writeValueAsString(rows);
            }
            return "[]";
        } catch (Exception e) {
            logger.error("Error executing parameterized PostgreSQL stored function '{}': {}", functionName, e.getMessage(), e);
            throw new RuntimeException("Failed to call PostgreSQL stored function with parameters: " + functionName, e);
        }
    }
}
