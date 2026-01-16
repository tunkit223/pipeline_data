package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Khung tiến trình tính toán
 * Note: Không còn hard-code schema, sẽ được set động
 * Table: uce_meta_process
 */
@Data
@Entity
@Table(name = "uce_meta_process")
public class UceMetaProcess {

    @Id
    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "meta_proc_name", nullable = false, unique = true, length = 500)
    private String metaProcName;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "meta_proc_note", columnDefinition = "TEXT")
    private String metaProcNote;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Schema chứa metadata của tiến trình này
     * Ví dụ: "hr_calc", "finance_calc", "test_dynamic_schema"
     * Each tenant can have their own schema for metadata isolation
     */
    @Column(name = "metadata_schema", nullable = false, length = 100)
    private String metadataSchema;

    /**
     * Airflow schedule interval for DAG
     * Examples: "@daily", "@hourly", "0 0 * * *", null (manual trigger only)
     */
    @Column(name = "schedule_interval", length = 100)
    private String scheduleInterval;
}
