package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Tác vụ (Task Instance)
 * Note: Schema được set động, không hard-code
 * Table: uce_task
 */
@Data
@Entity
@Table(name = "uce_task")
public class UceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_code", nullable = false, unique = true, length = 250)
    private String taskCode;

    @Column(name = "proc_code", nullable = false, length = 250)
    private String procCode;

    @Column(name = "meta_task_code", nullable = false, length = 250)
    private String metaTaskCode;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "calc_prog_id")
    private Long calcProgId;

    @Column(name = "calc_period_id")
    private Long calcPeriodId;

    @Column(name = "selector_biz", columnDefinition = "TEXT")
    private String selectorBiz;

    @Column(name = "processor_biz", columnDefinition = "TEXT")
    private String processorBiz;

    @Column(name = "insertor_biz", columnDefinition = "TEXT")
    private String insertorBiz;

    @Column(name = "task_note", columnDefinition = "TEXT")
    private String taskNote;

    @Column(name = "status", length = 100)
    private String status = "READY"; // READY / RUNNING / SUCCESS / FAILED
}
