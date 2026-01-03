package com.piplineData.loadService.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

@Data
@Entity
@Table(name = "dl_data_obj", schema = "sts")
public class DataObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_obj_id")
    private Integer dataObjId;

    @Column(name = "data_obj_code", nullable = false, unique = true, length = 250)
    private String dataObjCode;

    @Column(name = "data_obj_name", nullable = false, length = 500)
    private String dataObjName;

    @Type(JsonBinaryType.class)
    @Column(name = "streamspec", columnDefinition = "jsonb")
    private String streamSpec;

    @Type(JsonBinaryType.class)
    @Column(name = "batchspec", columnDefinition = "jsonb")
    private String batchSpec;

    @Column(name = "source_schema", length = 200)
    private String sourceSchema;

    @Column(name = "dest_schema", length = 200)
    private String destSchema;

    @Column(name = "dest_tablename", length = 200)
    private String destTablename;

    @Column(name = "data_obj_note", columnDefinition = "TEXT")
    private String dataObjNote;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
