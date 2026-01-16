package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity: Cấu hình kết nối đến Airflow
 * Schema: pipeline_config
 * Table: airflow_connection
 */
@Data
@Entity
@Table(name = "airflow_connection", schema = "pipeline_config")
public class AirflowConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "connection_name", nullable = false, unique = true, length = 250)
    private String connectionName;

    @Column(name = "airflow_base_url", nullable = false, length = 500)
    private String airflowBaseUrl; // http://localhost:8081

    @Column(name = "airflow_username", length = 100)
    private String airflowUsername;

    @Column(name = "airflow_password", length = 100)
    private String airflowPassword;

    @Column(name = "api_endpoint_variable", length = 500)
    private String apiEndpointVariable; // /api/v1/variables

    @Column(name = "api_endpoint_dag_run", length = 500)
    private String apiEndpointDagRun; // /api/v1/dags/{dag_id}/dagRuns

    @Column(name = "connection_note", columnDefinition = "TEXT")
    private String connectionNote;

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
}
