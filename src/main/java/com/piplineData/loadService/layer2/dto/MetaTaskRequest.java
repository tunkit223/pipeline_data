package com.piplineData.loadService.layer2.dto;

import lombok.Data;

/**
 * Request DTO để tạo/cập nhật Meta Task
 */
@Data
public class MetaTaskRequest {
    
    private String metaTaskCode;
    private String metaTaskName;
    private String metaTaskType;
    private String metaProcCode;
    private String preMetaTaskCodelist; // Comma-separated: "TASK1,TASK2"
    private String postMetaTaskCodelist;
    private Boolean isStarting;
    private Boolean isEnding;
    private String selector; // SQL template with {{params}}
    private String processor; // Function name or code
    private String insertor; // SQL template with {{params}}
    private String metaTaskNote;
    private Boolean isActive = true;
}
