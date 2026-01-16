package com.piplineData.loadService.layer2.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UceProgLinkRequest {
    
    private Long progId;
    
    private Long periodId;
    
    private String metaProcCode;
    
    private JsonNode useVar;  // JSONB - variables to use
    
    private String connectionName;  // For creating process and DAG
    
    private String dagDirectory;  // For syncing to Airflow
    
    private Boolean isActive;  // Default: true
}
