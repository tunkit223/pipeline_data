package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Tiến trình được thực thi
 * Note: Execution logs stored in uit_calc schema (centralized logging)
 * Table: uce_proc_exec
 */
@Data
@Entity
@Table(name = "uce_proc_exec", schema = "uit_calc")
public class UceProcExec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proc_exec_id")
    private Long procExecId;

    @Column(name = "proc_exec_code", nullable = false, unique = true, length = 250)
    private String procExecCode; // dag_run_id trong Airflow

    @Column(name = "proc_code", nullable = false, length = 250)
    private String procCode;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "calc_prog_id")
    private Long calcProgId;

    @Column(name = "calc_period_id")
    private Long calcPeriodId;

    @Column(name = "proc_exec_note", columnDefinition = "TEXT")
    private String procExecNote;

    @Column(name = "status", length = 100)
    private String status = "RUNNING"; // RUNNING / SUCCESS / FAILED
}
