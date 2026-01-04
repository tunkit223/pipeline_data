package com.piplineData.loadService.layer2.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Entity: Chu kỳ tính
 * Schema: UCE_PROGRAM
 * Table: uce_prog_period
 */
@Data
@Entity
@Table(name = "uce_prog_period", schema = "uce_program")
public class UceProgPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "period_name", nullable = false, unique = true, length = 250)
    private String periodName;

    @Column(name = "period_note", columnDefinition = "TEXT")
    private String periodNote;

    @Column(name = "prog_id", nullable = false)
    private Long progId;

    @Column(name = "status", length = 100)
    private String status = "DECLARED"; // DECLARED / APPROVED / RUNNING / FINISHED / PENDING / TERMINATED

    @Type(JsonBinaryType.class)
    @Column(name = "period_spec", nullable = false, columnDefinition = "jsonb")
    private String periodSpec;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Type(JsonBinaryType.class)
    @Column(name = "exec_mode", nullable = false, columnDefinition = "jsonb")
    private String execMode;
}
