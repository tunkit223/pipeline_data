package com.piplineData.loadService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BatchSpec {

    @JsonProperty("data_obj_code")
    private String dataObjCode;

    @JsonProperty("scriptusageid")
    private String scriptUsageId;

    @JsonProperty("exec_para_list")
    private Map<String, String> execParaList;

    @JsonProperty("script")
    private List<ScriptConfig> script;

    @Data
    public static class ScriptConfig {
        private SourceConfig source;
        private String version;
        private List<MapFieldConfig> mapfields;
        private DestinationConfig destination;
    }

    @Data
    public static class SourceConfig {
        private String dbsource;
        private String batchscript;
    }

    @Data
    public static class MapFieldConfig {
        private String objectname;
        private List<MapField> map;
    }

    @Data
    public static class DestinationConfig {
        private String dbsource;
        private String objectname;
        private String batchscript;
    }
}