package com.piplineData.loadService.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;

/**
 * Entity để khai báo Data Object - đối tượng dữ liệu cần đồng bộ
 * Schema: pipeline_config
 */
@Data
@Entity
@Table(name = "data_object", schema = "pipeline_config")
public class DataObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_obj_id")
    private Long dataObjId;

    @Column(name = "data_obj_code", nullable = false, unique = true, length = 250)
    private String dataObjCode;

    @Column(name = "data_obj_name", nullable = false, length = 500)
    private String dataObjName;

    /**
     * Reference đến Database Source Config
     */
    @Column(name = "source_db_config_code", length = 100)
    private String sourceDbConfigCode;

    @Type(JsonBinaryType.class)
    @Column(name = "streamspec", columnDefinition = "jsonb")
    private String streamSpec;

    @Type(JsonBinaryType.class)
    @Column(name = "batchspec", columnDefinition = "jsonb")
    private String batchSpec;

    @Column(name = "source_schema", length = 200)
    private String sourceSchema;

    @Column(name = "dest_schema", length = 200)
    private String destSchema;

    @Column(name = "dest_tablename", length = 200)
    private String destTablename;

    @Column(name = "data_obj_note", columnDefinition = "TEXT")
    private String dataObjNote;

    @Column(name = "sync_mode", length = 50)
    private String syncMode; // BATCH, EVENT, STREAMING

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
