package com.piplineData.loadService.layer2.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UceProgLinkResponse {
    
    private Long progUseMpId;  // Link record ID
    
    private Long progId;
    
    private Long periodId;
    
    private String metaProcCode;
    
    private Long processId;  // Created process ID
    
    private String processCode;  // Created process code
    
    private String dagId;  // Created DAG ID
    
    private String connectionName;
    
    private String message;
}
