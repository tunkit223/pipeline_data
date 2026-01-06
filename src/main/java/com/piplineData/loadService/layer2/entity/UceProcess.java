package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Tiến trình (Process Instance)
 * Note: Schema được set động, không hard-code
 * Table: uce_process
 */
@Data
@Entity
@Table(name = "uce_process")
public class UceProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proc_id")
    private Long procId;

    @Column(name = "proc_code", nullable = false, unique = true, length = 250)
    private String procCode;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "calc_prog_id")
    private Long calcProgId;

    @Column(name = "calc_period_id")
    private Long calcPeriodId;

    @Column(name = "proc_note", columnDefinition = "TEXT")
    private String procNote;

    @Column(name = "status", length = 100)
    private String status = "READY"; // READY / RUNNING / SUCCESS / FAILED

    @Column(name = "is_lasted", nullable = false)
    private Boolean isLasted = true;
}
