package com.piplineData.loadService.service;

import com.piplineData.loadService.entity.DatabaseSourceConfig;
import com.piplineData.loadService.exception.DataSyncException;
import com.piplineData.loadService.repository.DatabaseSourceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSourceConfigService {

    private final DatabaseSourceConfigRepository repository;

    /**
     * Tạo mới database source config
     */
    @Transactional
    public DatabaseSourceConfig create(DatabaseSourceConfig config) {
        log.info("Creating database source config: {}", config.getConfigCode());

        if (repository.existsByConfigCode(config.getConfigCode())) {
            throw new DataSyncException("Config code already exists: " + config.getConfigCode());
        }

        return repository.save(config);
    }

    /**
     * Lấy tất cả configs đang active
     */
    public List<DatabaseSourceConfig> findAllActive() {
        return repository.findAllByIsActiveTrue();
    }

    /**
     * Lấy config theo code
     */
    public DatabaseSourceConfig findByCode(String configCode) {
        return repository.findByConfigCodeAndIsActiveTrue(configCode)
                .orElseThrow(() -> new DataSyncException(
                        "Database config not found or inactive: " + configCode
                ));
    }

    /**
     * Lấy config theo ID
     */
    public DatabaseSourceConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DataSyncException("Database config not found: " + id));
    }

    /**
     * Update config
     */
    @Transactional
    public DatabaseSourceConfig update(Long id, DatabaseSourceConfig updatedConfig) {
        log.info("Updating database source config: {}", id);

        DatabaseSourceConfig existing = findById(id);

        // Update fields
        existing.setConfigName(updatedConfig.getConfigName());
        existing.setDbType(updatedConfig.getDbType());
        existing.setHost(updatedConfig.getHost());
        existing.setPort(updatedConfig.getPort());
        existing.setDatabaseName(updatedConfig.getDatabaseName());
        existing.setUsername(updatedConfig.getUsername());

        if (updatedConfig.getPassword() != null && !updatedConfig.getPassword().isEmpty()) {
            existing.setPassword(updatedConfig.getPassword());
        }

        if (updatedConfig.getJdbcUrl() != null) {
            existing.setJdbcUrl(updatedConfig.getJdbcUrl());
        }

        if (updatedConfig.getDriverClassName() != null) {
            existing.setDriverClassName(updatedConfig.getDriverClassName());
        }

        existing.setConnectionProperties(updatedConfig.getConnectionProperties());
        existing.setDescription(updatedConfig.getDescription());
        existing.setIsActive(updatedConfig.getIsActive());

        return repository.save(existing);
    }

    /**
     * Xóa config (soft delete)
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting database source config: {}", id);

        DatabaseSourceConfig config = findById(id);
        config.setIsActive(false);
        repository.save(config);
    }

    /**
     * Test connection đến database
     */
    public boolean testConnection(String configCode) {
        log.info("Testing connection for config: {}", configCode);

        DatabaseSourceConfig config = findByCode(configCode);

        try {
            DataSource dataSource = createDataSource(config);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            // Thực hiện simple query để test connection
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            log.info("Connection test successful for: {}", configCode);
            return true;

        } catch (Exception e) {
            log.error("Connection test failed for {}: {}", configCode, e.getMessage());
            return false;
        }
    }

    /**
     * Tạo DataSource từ config
     */
    public DataSource createDataSource(DatabaseSourceConfig config) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setUrl(config.getJdbcUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());

        return dataSource;
    }
}
