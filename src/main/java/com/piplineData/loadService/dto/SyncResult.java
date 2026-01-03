package com.piplineData.loadService.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SyncResult {
    private String dataObjCode;
    private Integer recordsFetched;
    private Integer recordsInserted;
    private Integer recordsUpdated;
    private Integer recordsDeleted;
    private Map<String, Object> entriesInCommon;
    private Map<String, Object> entriesDiffering;
    private Map<String, Object> entriesOnlyInSource;
    private Map<String, Object> entriesOnlyInDest;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
