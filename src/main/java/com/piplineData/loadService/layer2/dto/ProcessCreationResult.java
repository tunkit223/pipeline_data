package com.piplineData.loadService.layer2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO khi tạo Process từ Meta Process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessCreationResult {
    
    private String procCode;
    private String metaProcCode;
    private Long procId;
    private String status;
    private List<String> taskCodes;
    private Integer totalTasks;
    private String message;
}
