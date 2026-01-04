package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.service.DynamicDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Service để thực thi SQL động (selector, insertor)
 * Sử dụng destination database (pipeline_data)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutorService {

    private final JdbcTemplate jdbcTemplate;
    private final DynamicDatabaseService dynamicDatabaseService;

    /**
     * Execute SELECT query và trả về kết quả
     * 
     * @param sql Rendered SQL query
     * @return List of rows as Map
     */
    public List<Map<String, Object>> executeSelect(String sql) {
        log.info("Executing SELECT query");
        log.debug("SQL: {}", sql);

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("Query returned {} rows", results.size());
            return results;
        } catch (Exception e) {
            log.error("Failed to execute SELECT query: {}", e.getMessage());
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute INSERT/UPDATE/DELETE query
     * 
     * @param sql Rendered SQL query
     * @return Number of rows affected
     */
    public int executeUpdate(String sql) {
        log.info("Executing UPDATE/INSERT/DELETE query");
        log.debug("SQL: {}", sql);

        try {
            int rowsAffected = jdbcTemplate.update(sql);
            log.info("Query affected {} rows", rowsAffected);
            return rowsAffected;
        } catch (Exception e) {
            log.error("Failed to execute UPDATE query: {}", e.getMessage());
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute SQL và trả về single value
     */
    public Object executeScalar(String sql, Class<?> returnType) {
        log.info("Executing scalar query");
        log.debug("SQL: {}", sql);

        try {
            return jdbcTemplate.queryForObject(sql, returnType);
        } catch (Exception e) {
            log.error("Failed to execute scalar query: {}", e.getMessage());
            throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute query trên external datasource (source database)
     */
    public List<Map<String, Object>> executeSelectOnSource(String sourceDbConfigCode, String sql) {
        log.info("Executing SELECT on source database: {}", sourceDbConfigCode);
        
        try {
            DataSource sourceDataSource = dynamicDatabaseService.getDataSource(sourceDbConfigCode);
            JdbcTemplate sourceTemplate = new JdbcTemplate(sourceDataSource);
            
            List<Map<String, Object>> results = sourceTemplate.queryForList(sql);
            log.info("Query returned {} rows from source", results.size());
            return results;
        } catch (Exception e) {
            log.error("Failed to execute query on source: {}", e.getMessage());
            throw new RuntimeException("Source SQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate SQL syntax (basic check)
     */
    public boolean validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        // Basic validation
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("SELECT") 
            || trimmed.startsWith("INSERT") 
            || trimmed.startsWith("UPDATE") 
            || trimmed.startsWith("DELETE")
            || trimmed.startsWith("WITH");
    }
}
