package com.piplineData.loadService.layer2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO khi execute một Task
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionResult {
    
    private String taskExecCode;
    private String taskCode;
    private String procExecCode;
    private String status; // RUNNING / SUCCESS / FAILED
    private Integer rowsAffected;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long executionTimeMs;
    private String message;
    private String errorMessage;
}
