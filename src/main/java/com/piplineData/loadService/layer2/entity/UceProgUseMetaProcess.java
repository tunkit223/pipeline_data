package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Chương trình/kỳ tính sử dụng khung tiến trình
 * Schema: UCE_PROGRAM
 * Table: uce_prog_use_meta_process
 */
@Data
@Entity
@Table(name = "uce_prog_use_meta_process", schema = "uce_program")
public class UceProgUseMetaProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prog_use_mp_id")
    private Long progUseMpId;

    @Column(name = "prog_id", nullable = false)
    private Long progId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "use_var", nullable = false, length = 250)
    private String useVar; // Biến trong Airflow để phát sinh DAG

    @Column(name = "dag_id", nullable = false, length = 250)
    private String dagId; // DAG ID trong Airflow

    @Column(name = "connection_id", nullable = false, length = 250)
    private String connectionId; // API URL để chạy task

    @Column(name = "use_note", columnDefinition = "TEXT")
    private String useNote;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
