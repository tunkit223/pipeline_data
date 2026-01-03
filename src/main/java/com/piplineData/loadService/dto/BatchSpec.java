package com.piplineData.loadService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * BatchSpec DTO - Simplified version for API
 * Cấu trúc đơn giản hơn để dễ sử dụng từ API
 */
@Data
public class BatchSpec {

    @JsonProperty("sourceTable")
    private String sourceTable;

    @JsonProperty("destTable")
    private String destTable;

    @JsonProperty("primaryKeys")
    private List<String> primaryKeys;

    @JsonProperty("fields")
    private List<MapField> fields;

    @JsonProperty("whereCondition")
    private String whereCondition;

    @JsonProperty("orderBy")
    private String orderBy;

    // Legacy support - old structure
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