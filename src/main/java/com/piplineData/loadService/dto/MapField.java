package com.piplineData.loadService.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO để định nghĩa mapping giữa cột nguồn và cột đích
 * Hỗ trợ SQL transformation
 */
@Data
public class MapField {
    
    // Legacy fields
    @JsonProperty("to")
    private String to;      // tên cột đích
    
    @JsonProperty("from")
    private String from;    // tên cột nguồn
    
    @JsonProperty("build")
    private String build;   // "key" hoặc "value"
    
    // New simplified fields
    @JsonProperty("sourceField")
    private String sourceField;  // Tên cột nguồn (new API)
    
    @JsonProperty("destField")
    private String destField;    // Tên cột đích (new API)
    
    /**
     * SQL transformation expression
     * VD: "UPPER({{name}})", "SUBSTRING({{code}}, 1, 10)", "{{amount}} * 1.1"
     */
    @JsonProperty("sqlTransform")
    private String sqlTransform;
    
    /**
     * Kiểu dữ liệu đích để type conversion
     * VD: "VARCHAR", "INTEGER", "TIMESTAMP", "DECIMAL"
     */
    @JsonProperty("dataType")
    private String dataType;
    
    /**
     * Default value nếu source value là null
     */
    @JsonProperty("defaultValue")
    private String defaultValue;
    
    /**
     * Check if this field is a primary key field
     * Used for legacy BatchSpec support where build="key"
     */
    public boolean isKeyField() {
        return "key".equalsIgnoreCase(build);
    }
    
    // Getters with fallback for compatibility
    public String getFrom() {
        return from != null ? from : sourceField;
    }
    
    public String getTo() {
        return to != null ? to : destField;
    }
}
