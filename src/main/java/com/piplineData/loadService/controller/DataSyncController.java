package com.piplineData.loadService.controller;


import com.piplineData.loadService.dto.SyncResult;
import com.piplineData.loadService.entity.SyncLog;
import com.piplineData.loadService.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/data-sync")
@RequiredArgsConstructor
public class DataSyncController {

    private final DataSyncService dataSyncService;

    /**
     * Endpoint để Airflow gọi để trigger sync
     * POST /api/v1/data-sync/execute
     */
    @PostMapping("/execute")
    public ResponseEntity<SyncResult> executeSyncspringboot(@RequestBody Map<String, Object> request) {

        // Support both naming conventions
        String dataObjCode = (String) request.getOrDefault("dataObjCode", 
                                request.get("data_obj_code"));

        log.info("Received sync request for dataObjCode: {}", dataObjCode);
        log.debug("Request payload: {}", request);

        // Extract runtime parameters
        Map<String, Object> runtimeParams = extractRuntimeParams(request);

        // Execute sync
        SyncResult result = dataSyncService.syncData(dataObjCode, runtimeParams);

        return ResponseEntity.ok(result);
    }

    /**
     * Manual retry endpoint
     * POST /api/v1/data-sync/retry
     */
    @PostMapping("/retry")
    public ResponseEntity<SyncResult> retrySync(@RequestBody Map<String, Object> request) {

        String dataObjCode = (String) request.get("data_obj_code");

        log.info("Manual retry requested for data_obj_code: {}", dataObjCode);

        Map<String, Object> runtimeParams = extractRuntimeParams(request);

        SyncResult result = dataSyncService.retrySyncManually(dataObjCode, runtimeParams);

        return ResponseEntity.ok(result);
    }

    /**
     * Get sync history by path variable
     * GET /api/v1/data-sync/history/{dataObjCode}
     */
    @GetMapping("/history/{dataObjCode}")
    public ResponseEntity<List<SyncLog>> getSyncHistoryByPath(@PathVariable String dataObjCode) {

        log.info("Fetching sync history for: {}", dataObjCode);

        List<SyncLog> history = dataSyncService.getSyncHistory(dataObjCode);

        return ResponseEntity.ok(history);
    }

    /**
     * Get sync history by query parameter
     * GET /api/v1/data-sync/history?dataObjCode=SYNC_ORDERS
     * GET /api/v1/data-sync/history (get all)
     */
    @GetMapping("/history")
    public ResponseEntity<List<SyncLog>> getSyncHistoryByQuery(
            @RequestParam(required = false) String dataObjCode) {

        log.info("Fetching sync history, dataObjCode: {}", dataObjCode);

        List<SyncLog> history;
        if (dataObjCode != null && !dataObjCode.isEmpty()) {
            history = dataSyncService.getSyncHistory(dataObjCode);
        } else {
            history = dataSyncService.getAllSyncHistory();
        }

        return ResponseEntity.ok(history);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "data-sync-layer-1"
        ));
    }

    /**
     * Extract runtime parameters từ request
     * Hỗ trợ 2 formats:
     * 1. Nested object: { "runtimeParams": { "p_key": "value" } }
     * 2. Flat structure: { "p_key": "value", "fromdate": "..." }
     */
    private Map<String, Object> extractRuntimeParams(Map<String, Object> request) {
        Map<String, Object> params = new HashMap<>();
        
        // Check if runtimeParams object exists (nested format)
        if (request.containsKey("runtimeParams")) {
            Object runtimeParamsObj = request.get("runtimeParams");
            if (runtimeParamsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedParams = (Map<String, Object>) runtimeParamsObj;
                params.putAll(nestedParams);
                log.info("Extracted runtime parameters from nested 'runtimeParams' object: {}", params);
                return params;
            }
        }
        
        // Fallback: Extract known parameter names from flat structure (legacy support)
        params.put("fromdate", request.getOrDefault("fromdate", ""));
        params.put("todate", request.getOrDefault("todate", ""));
        params.put("company_id", request.getOrDefault("company_id", ""));
        params.put("p_starttime", request.getOrDefault("p_starttime", ""));
        params.put("p_endtime", request.getOrDefault("p_endtime", ""));
        params.put("p_company_id", request.getOrDefault("p_company_id", ""));
        params.put("p_companyId", request.getOrDefault("p_companyId", ""));

        log.debug("Extracted runtime parameters from flat structure: {}", params);
        return params;
    }
}
