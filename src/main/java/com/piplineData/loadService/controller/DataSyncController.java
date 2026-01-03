package com.piplineData.loadService.controller;


import com.piplineData.loadService.dto.SyncResult;
import com.piplineData.loadService.entity.SyncLog;
import com.piplineData.loadService.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

        String dataObjCode = (String) request.get("data_obj_code");

        log.info("Received sync request for data_obj_code: {}", dataObjCode);
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
     * Get sync history
     * GET /api/v1/data-sync/history/{dataObjCode}
     */
    @GetMapping("/history/{dataObjCode}")
    public ResponseEntity<List<SyncLog>> getSyncHistory(@PathVariable String dataObjCode) {

        log.info("Fetching sync history for: {}", dataObjCode);

        List<SyncLog> history = dataSyncService.getSyncHistory(dataObjCode);

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
     */
    private Map<String, Object> extractRuntimeParams(Map<String, Object> request) {
        Map<String, Object> params = Map.of(
                "fromdate", request.getOrDefault("fromdate", ""),
                "todate", request.getOrDefault("todate", ""),
                "company_id", request.getOrDefault("company_id", ""),
                "p_starttime", request.getOrDefault("p_starttime", ""),
                "p_endtime", request.getOrDefault("p_endtime", ""),
                "p_company_id", request.getOrDefault("p_company_id", ""),
                "p_companyId", request.getOrDefault("p_companyId", "")
        );

        return params;
    }
}
