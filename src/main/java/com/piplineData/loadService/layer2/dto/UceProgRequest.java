package com.piplineData.loadService.layer2.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UceProgRequest {
    
    private String progName;
    
    private Long companyId;
    
    private Long brandId;
    
    private Long departmentId;
    
    private String progNote;
    
    private String status;  // Default: "DECLARED"
    
    private String progType;  // Default: "NOTARGET"
    
    private JsonNode progSpec;  // JSONB - program specification
}
