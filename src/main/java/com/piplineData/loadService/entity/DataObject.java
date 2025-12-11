package com.piplineData.loadService.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;


@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class DataObject {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String dataObjId;

    String dataObjCode;
    String dataObjName;

    @Type(JsonBinaryType.class)
    String streamSpec;

    @Type(JsonBinaryType.class)
    String batchSpec;

    String sourceSchema;
    String destSchema;
    String destTableName;
    String dataObjNote;
    Boolean isActive;
}
