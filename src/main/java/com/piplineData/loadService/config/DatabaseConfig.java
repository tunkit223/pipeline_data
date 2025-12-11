package com.piplineData.loadService.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Slf4j
@FieldDefaults(level = AccessLevel.PACKAGE, makeFinal = true)
public class DatabaseConfig {

    @Primary
    @Bean(name = "destinationDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource destinationDataSource(){
        return new HikariDataSource();
    }

    Map<String, DataSource> dynamicDataSources = new HashMap<>();

    //Tạo dynamic data source
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

    //Lấy datasource đã tạo
    public DataSource getDataSource(String dbSource) {
        return dynamicDataSources.get(dbSource);
    }

    // Đóng dynamic data source
    public void closeAllDynamicDataSources() {
        dynamicDataSources.values().forEach(ds -> {
            if (ds instanceof HikariDataSource) {
                ((HikariDataSource) ds).close();
            }
        });
        dynamicDataSources.clear();
    }
}
