package com.piplineData.loadService.layer2.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

/**
 * Entity: Chương trình tính
 * Schema: UCE_PROGRAM
 * Table: uce_prog
 */
@Data
@Entity
@Table(name = "uce_prog", schema = "uce_program")
public class UceProg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prog_id")
    private Long progId;

    @Column(name = "prog_name", nullable = false, unique = true, length = 250)
    private String progName;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "prog_note", columnDefinition = "TEXT")
    private String progNote;

    @Column(name = "status", length = 100)
    private String status = "DECLARED"; // DECLARED / APPROVED / RUNNING / FINISHED / PENDING / TERMINATED

    @Column(name = "prog_type", length = 100)
    private String progType = "NOTARGET"; // TARGET / NOTARGET

    @Type(JsonBinaryType.class)
    @Column(name = "prog_spec", nullable = false, columnDefinition = "jsonb")
    private String progSpec;
}
