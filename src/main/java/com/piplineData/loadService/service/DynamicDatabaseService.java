package com.piplineData.loadService.service;

import com.piplineData.loadService.config.DatabaseConfig;
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

    /**
     * Thực thi query trên Source DB động
     * @param dbSource tên nguồn DB
     * @param sql câu SQL cần thực thi
     * @param dbUrl JDBC URL
     * @param dbUsername username
     * @param dbPassword password
     * @param driverClassName driver class
     */
    public List<Map<String, Object>> executeQueryOnSourceDb(
            String dbSource, String sql, String dbUrl,
            String dbUsername, String dbPassword, String driverClassName) {

        try {
            DataSource dataSource = databaseConfig.createDynamicDataSource(
                    dbSource, dbUrl, dbUsername, dbPassword, driverClassName
            );

            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            log.info("Executing query on source DB [{}]: {}", dbSource, sql);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("Fetched {} records from source DB [{}]", results.size(), dbSource);

            return results;

        } catch (Exception e) {
            log.error("Failed to execute query on source DB [{}]: {}", dbSource, e.getMessage());
            throw new DataSyncException("Failed to fetch data from source DB: " + dbSource, e);
        }
    }

    /**
     * Thực thi query trên Destination DB
     */
    public List<Map<String, Object>> executeQueryOnDestDb(DataSource destDataSource, String sql) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(destDataSource);

            log.info("Executing query on destination DB: {}", sql);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("Fetched {} records from destination DB", results.size());

            return results;

        } catch (Exception e) {
            log.error("Failed to execute query on destination DB: {}", e.getMessage());
            throw new DataSyncException("Failed to fetch data from destination DB", e);
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
