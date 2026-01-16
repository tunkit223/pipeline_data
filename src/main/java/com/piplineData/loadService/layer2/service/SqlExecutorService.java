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

    /**
     * Execute batch INSERT using PreparedStatement
     * Automatically creates table if not exists and inserts data from selector results
     * 
     * @param insertSql INSERT statement with placeholders (?, ?, ...)
     * @param selectorResults Data rows from selector query
     * @return Number of rows inserted
     */
    public int executeBatchInsert(String insertSql, List<Map<String, Object>> selectorResults) {
        if (selectorResults == null || selectorResults.isEmpty()) {
            log.warn("No data to insert");
            return 0;
        }

        log.info("Executing batch insert for {} rows", selectorResults.size());
        
        try {
            // Extract table name and create table if needed
            String tableName = extractTableName(insertSql);
            if (tableName != null && !tableName.isEmpty()) {
                // Get column names from first row
                Map<String, Object> firstRow = selectorResults.get(0);
                createTableIfNotExists(tableName, firstRow);
            }
            
            // Execute batch insert
            return jdbcTemplate.batchUpdate(insertSql, selectorResults, selectorResults.size(),
                (ps, row) -> {
                    int index = 1;
                    for (Object value : row.values()) {
                        ps.setObject(index++, value);
                    }
                }).length;
        } catch (Exception e) {
            log.error("Failed to execute batch insert: {}", e.getMessage(), e);
            throw new RuntimeException("Batch insert failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract table name from INSERT statement
     */
    private String extractTableName(String insertSql) {
        String upper = insertSql.trim().toUpperCase();
        if (!upper.startsWith("INSERT INTO")) {
            return null;
        }
        
        int intoIndex = upper.indexOf("INTO");
        int valuesIndex = upper.indexOf("VALUES");
        if (valuesIndex < 0) valuesIndex = upper.indexOf("(");
        
        if (intoIndex > 0 && valuesIndex > intoIndex) {
            String tablePart = insertSql.substring(intoIndex + 4, valuesIndex).trim();
            // Remove column list if exists
            int parenIndex = tablePart.indexOf('(');
            if (parenIndex > 0) {
                tablePart = tablePart.substring(0, parenIndex).trim();
            }
            return tablePart;
        }
        
        return null;
    }

    /**
     * Create table if not exists based on first row data types
     */
    private void createTableIfNotExists(String tableName, Map<String, Object> sampleRow) {
        log.info("Creating table if not exists: {}", tableName);
        
        // Extract schema name if exists (format: schema.table)
        String schemaName = null;
        String pureTableName = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            schemaName = parts[0];
            pureTableName = parts[1];
            
            // Create schema if not exists
            ensureSchemaExists(schemaName);
        }
        
        StringBuilder createTableSql = new StringBuilder();
        createTableSql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : sampleRow.entrySet()) {
            if (!first) createTableSql.append(", ");
            first = false;
            
            String columnName = entry.getKey();
            String dataType = inferDataType(entry.getValue());
            createTableSql.append(columnName).append(" ").append(dataType);
        }
        
        createTableSql.append(")");
        
        log.debug("CREATE TABLE SQL: {}", createTableSql);
        jdbcTemplate.execute(createTableSql.toString());
        log.info("Table {} created or already exists", tableName);
    }

    /**
     * Ensure schema exists, create if not
     */
    private void ensureSchemaExists(String schemaName) {
        try {
            String checkSchemaSql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
            List<String> schemas = jdbcTemplate.queryForList(checkSchemaSql, String.class, schemaName);
            
            if (schemas.isEmpty()) {
                log.info("Schema {} does not exist, creating...", schemaName);
                String createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
                jdbcTemplate.execute(createSchemaSql);
                log.info("✅ Schema {} created successfully", schemaName);
            } else {
                log.debug("Schema {} already exists", schemaName);
            }
        } catch (Exception e) {
            log.warn("Failed to check/create schema {}: {}", schemaName, e.getMessage());
        }
    }

    /**
     * Infer PostgreSQL data type from Java object
     */
    private String inferDataType(Object value) {
        if (value == null) return "TEXT";
        
        if (value instanceof Integer) return "INTEGER";
        if (value instanceof Long) return "BIGINT";
        if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) return "NUMERIC(18,2)";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof java.sql.Date || value instanceof java.util.Date) return "TIMESTAMP";
        
        return "TEXT";
    }
}

