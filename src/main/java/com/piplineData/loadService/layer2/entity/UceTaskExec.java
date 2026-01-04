package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity: Tác vụ được thực thi
 * Schema: UIT_CALC
 * Table: uce_task_exec
 */
@Data
@Entity
@Table(name = "uce_task_exec", schema = "uit_calc")
public class UceTaskExec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_exec_id")
    private Long taskExecId;

    @Column(name = "task_exec_code", nullable = false, unique = true, length = 250)
    private String taskExecCode;

    @Column(name = "proc_exec_code", nullable = false, length = 250)
    private String procExecCode;

    @Column(name = "task_code", nullable = false, length = 250)
    private String taskCode;

    @Column(name = "proc_code", nullable = false, length = 250)
    private String procCode;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "calc_prog_id")
    private Long calcProgId;

    @Column(name = "calc_period_id")
    private Long calcPeriodId;

    @Column(name = "selector_biz_ctrl", columnDefinition = "TEXT")
    private String selectorBizCtrl;

    @Column(name = "processor_biz_ctrl", columnDefinition = "TEXT")
    private String processorBizCtrl;

    @Column(name = "insertor_biz_ctrl", columnDefinition = "TEXT")
    private String insertorBizCtrl;

    @Column(name = "task_exec_note", columnDefinition = "TEXT")
    private String taskExecNote;

    @Column(name = "status", length = 100)
    private String status = "RUNNING"; // RUNNING / SUCCESS / FAILED

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }
}
