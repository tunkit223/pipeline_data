package com.piplineData.loadService.layer2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO để sync Airflow Variable
 * Format theo DAG mẫu uit_reward_mp_sale.py
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirflowVariableDto {
    
    private List<TaskDefinition> tasks;
    private String httpConnId;
    private String processCode;
    private Long companyId;
    private Long brandId;
    private Long calculatedProgId;
    private Long calculatedPeriodId;
    private String metaProcessCode;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDefinition {
        private String taskCode;
        private String endpoint;
        private List<String> dependsOn; // List of task codes this task depends on
    }
}
