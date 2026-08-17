package com.example.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SqlExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutionService.class);
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes a validated SELECT query against PostgreSQL.
     *
     * @param sql Validated SELECT query
     * @return List of row maps
     */
    public List<Map<String, Object>> executeSelect(String sql) {
        logger.info("Executing validated SELECT query: {}", sql);
        long startTime = System.currentTimeMillis();

        try {
            JdbcTemplate scopedTemplate = new JdbcTemplate(jdbcTemplate.getDataSource());
            scopedTemplate.setMaxRows(MAX_ROWS);
            scopedTemplate.setQueryTimeout(15); // 15s timeout

            List<Map<String, Object>> rows = scopedTemplate.queryForList(sql);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Executed SQL query successfully in {}ms (returned {} rows)", elapsed, rows.size());
            return rows;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("SQL query execution failed after {}ms: {}", elapsed, e.getMessage(), e);
            throw new RuntimeException("Database query execution failed: " + e.getMessage(), e);
        }
    }
}
