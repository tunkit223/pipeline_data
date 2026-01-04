package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Khung tiến trình tính toán
 * Schema: UIT_CALC
 * Table: uce_meta_process
 */
@Data
@Entity
@Table(name = "uce_meta_process", schema = "uit_calc")
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
}
