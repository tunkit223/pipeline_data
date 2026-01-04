package com.piplineData.loadService.config;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    // DataSource chính cho Destination DB (PostgreSQL)
    @Primary
    @Bean(name = "destinationDataSource")
    public DataSource destinationDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        // Set timezone cho PostgreSQL
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        return new HikariDataSource(config);
    }

    // Cache để lưu dynamic datasources cho Source DBs
    private final Map<String, DataSource> dynamicDataSources = new HashMap<>();

    /**
     * Tạo DataSource động cho Source DB
     * @param dbSource tên nguồn DB (ví dụ: "uit", "crm", "erp")
     * @param url JDBC URL
     * @param username username
     * @param password password
     * @param driverClassName driver class (ví dụ: oracle.jdbc.OracleDriver, org.postgresql.Driver)
     */
    public DataSource createDynamicDataSource(String dbSource, String url,
                                              String username, String password,
                                              String driverClassName) {

        if (dynamicDataSources.containsKey(dbSource)) {
            log.info("Reusing existing DataSource for: {}", dbSource);
            return dynamicDataSources.get(dbSource);
        }

        log.info("Creating new DataSource for: {}", dbSource);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        HikariDataSource dataSource = new HikariDataSource(config);
        dynamicDataSources.put(dbSource, dataSource);

        return dataSource;
    }

    /**
     * Lấy DataSource đã tạo trước đó
     */
    public DataSource getDataSource(String dbSource) {
        return dynamicDataSources.get(dbSource);
    }

    /**
     * Đóng tất cả dynamic datasources
     */
    public void closeAllDynamicDataSources() {
        dynamicDataSources.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
        dynamicDataSources.clear();
    }
}