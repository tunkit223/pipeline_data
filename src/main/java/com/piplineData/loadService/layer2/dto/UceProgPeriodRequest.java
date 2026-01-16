package com.piplineData.loadService.layer2.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UceProgPeriodRequest {
    
    private String periodName;
    
    private Long progId;
    
    private JsonNode periodSpec;  // JSONB - period specification
    
    private JsonNode execMode;  // JSONB - execution mode
    
    private LocalDateTime startDate;
    
    private LocalDateTime endDate;
    
    private String status;  // e.g., "ACTIVE", "CLOSED"
}
