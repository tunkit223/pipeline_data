package com.piplineData.loadService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO để nhận request tạo/update Data Object từ API
 */
@Data
public class DataObjectRequest {

    @JsonProperty("dataObjCode")
    private String dataObjCode;

    @JsonProperty("dataObjName")
    private String dataObjName;

    @JsonProperty("sourceDbConfigCode")
    private String sourceDbConfigCode;

    @JsonProperty("sourceSchema")
    private String sourceSchema;

    @JsonProperty("destSchema")
    private String destSchema;

    @JsonProperty("destTablename")
    private String destTablename;

    @JsonProperty("dataObjNote")
    private String dataObjNote;

    @JsonProperty("syncMode")
    private String syncMode;

    /**
     * BatchSpec nhận dưới dạng Object, sẽ được convert sang JSON string
     */
    @JsonProperty("batchSpec")
    private Object batchSpec;

    /**
     * StreamSpec nhận dưới dạng Object, sẽ được convert sang JSON string
     */
    @JsonProperty("streamSpec")
    private Object streamSpec;
}
