package com.piplineData.loadService.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScriptConfig {
    SourceConfig source;
    String version;
    List<MapFieldConfig> mapFields;
    DestinationConfig destination;
}
