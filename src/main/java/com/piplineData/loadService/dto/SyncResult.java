package com.piplineData.loadService.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncResult {
    String dataObjectId;
    Integer recordsFetched;
    Integer recordsInserted;
    Integer recordsUpdated;
    Integer recordsDeleted;
    Map<String, Object> entriesInCommon;
    Map<String, Object> entriesDiffering;
    Map<String, Object> entriesOnlyInSource;
    Map<String, Object> entriesOnlyInDest;
    String status;
    String errorMessage;
}
