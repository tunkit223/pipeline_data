package com.piplineData.loadService.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entity để lưu thông tin kết nối đến database nguồn (kho dữ liệu doanh nghiệp)
 * Schema: pipeline_config
 */
@Data
@Entity
@Table(name = "db_source_config", schema = "pipeline_config")
public class DatabaseSourceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "config_code", nullable = false, unique = true, length = 100)
    private String configCode;

    @Column(name = "config_name", nullable = false, length = 500)
    private String configName;

    @Column(name = "db_type", nullable = false, length = 50)
    private String dbType; // POSTGRESQL, MYSQL, ORACLE, MSSQL

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 255)
    private String databaseName;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password; // Plain text tạm thời

    @Column(name = "jdbc_url", length = 1000)
    private String jdbcUrl;

    @Column(name = "driver_class_name", length = 500)
    private String driverClassName;

    @Column(name = "connection_properties", columnDefinition = "TEXT")
    private String connectionProperties; // JSON string cho các properties bổ sung

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Tự động build JDBC URL nếu chưa có
     */
    public String getJdbcUrl() {
        if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
            return jdbcUrl;
        }
        return buildJdbcUrl();
    }

    /**
     * Build JDBC URL dựa trên db_type
     */
    private String buildJdbcUrl() {
        switch (dbType.toUpperCase()) {
            case "POSTGRESQL":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "MYSQL":
                return String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName);
            case "ORACLE":
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, databaseName);
            case "MSSQL":
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            default:
                return String.format("jdbc:%s://%s:%d/%s", dbType.toLowerCase(), host, port, databaseName);
        }
    }

    /**
     * Get driver class name dựa trên db_type
     */
    public String getDriverClassName() {
        if (driverClassName != null && !driverClassName.isEmpty()) {
            return driverClassName;
        }
        return getDefaultDriverClassName();
    }

    /**
     * Default driver cho mỗi loại database
     */
    private String getDefaultDriverClassName() {
        switch (dbType.toUpperCase()) {
            case "POSTGRESQL":
                return "org.postgresql.Driver";
            case "MYSQL":
                return "com.mysql.cj.jdbc.Driver";
            case "ORACLE":
                return "oracle.jdbc.OracleDriver";
            case "MSSQL":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                return "org.postgresql.Driver";
        }
    }
}
