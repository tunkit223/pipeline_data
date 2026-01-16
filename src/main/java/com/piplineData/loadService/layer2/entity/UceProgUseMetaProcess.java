package com.piplineData.loadService.layer2.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Entity: Chương trình/kỳ tính sử dụng khung tiến trình
 * Schema: UCE_PROGRAM
 * Table: uce_prog_use_meta_process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Type(JsonBinaryType.class)
    @Column(name = "use_var", columnDefinition = "jsonb")
    private JsonNode useVar;

    @Column(name = "dag_id", length = 250)
    private String dagId;

    @Column(name = "connection_id")
    private Long connectionId;

    @Column(name = "use_note", columnDefinition = "TEXT")
    private String useNote;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
