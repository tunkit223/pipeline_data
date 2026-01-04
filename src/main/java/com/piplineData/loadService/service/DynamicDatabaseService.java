package com.piplineData.loadService.service;

import com.piplineData.loadService.config.DatabaseConfig;
import com.piplineData.loadService.entity.DatabaseSourceConfig;
import com.piplineData.loadService.exception.DataSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDatabaseService {

    private final DatabaseConfig databaseConfig;
    private final DatabaseSourceConfigService databaseSourceConfigService;

    /**
     * Lấy DataSource từ config code
     */
    public DataSource getDataSource(String configCode) {
        log.info("Getting DataSource for config: {}", configCode);
        DatabaseSourceConfig config = databaseSourceConfigService.findByCode(configCode);
        return databaseSourceConfigService.createDataSource(config);
    }

    /**
     * Thực thi query trên bất kỳ DataSource nào (Source hoặc Destination)
     */
    public List<Map<String, Object>> executeQueryOnDestDb(DataSource dataSource, String sql) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            log.info("Executing query: {}", sql);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("Fetched {} records", results.size());

            return results;

        } catch (Exception e) {
            log.error("Failed to execute query: {}", e.getMessage());
            throw new DataSyncException("Failed to fetch data", e);
        }
    }

    /**
     * Thực thi INSERT vào Destination DB
     */
    public int executeInsert(DataSource destDataSource, String sql, Object[] params) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(destDataSource);
            int rowsAffected = jdbcTemplate.update(sql, params);
            log.debug("Inserted {} rows", rowsAffected);
            return rowsAffected;
        } catch (Exception e) {
            log.error("Failed to execute insert: {}", e.getMessage());
            throw new DataSyncException("Failed to insert data", e);
        }
    }

    /**
     * Thực thi UPDATE vào Destination DB
     */
    public int executeUpdate(DataSource destDataSource, String sql, Object[] params) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(destDataSource);
            int rowsAffected = jdbcTemplate.update(sql, params);
            log.debug("Updated {} rows", rowsAffected);
            return rowsAffected;
        } catch (Exception e) {
            log.error("Failed to execute update: {}", e.getMessage());
            throw new DataSyncException("Failed to update data", e);
        }
    }

    /**
     * Thực thi DELETE trên Destination DB
     */
    public int executeDelete(DataSource destDataSource, String sql, Object[] params) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(destDataSource);
            int rowsAffected = jdbcTemplate.update(sql, params);
            log.debug("Deleted {} rows", rowsAffected);
            return rowsAffected;
        } catch (Exception e) {
            log.error("Failed to execute delete: {}", e.getMessage());
            throw new DataSyncException("Failed to delete data", e);
        }
    }
}
