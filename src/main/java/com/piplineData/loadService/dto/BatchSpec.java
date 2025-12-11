package com.piplineData.loadService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchSpec {
    @JsonProperty("data_obj_code")
    String dataObjCode;

    @JsonProperty("script_usage_id")
    String scriptUsageId;

    @JsonProperty("exec_para_list")
    Map<String,String> execParaList;

    @JsonProperty("script")
    List<ScriptConfig> script;


}
