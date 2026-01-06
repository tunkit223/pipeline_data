package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.entity.AirflowConnection;
import com.piplineData.loadService.layer2.repository.AirflowConnectionRepository;
import com.piplineData.loadService.layer2.service.AirflowIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller để tích hợp với Airflow
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/airflow")
@RequiredArgsConstructor
public class AirflowIntegrationController {

    private final AirflowIntegrationService airflowIntegrationService;
    private final AirflowConnectionRepository airflowConnectionRepository;

    /**
     * Create Airflow Connection
     * POST /api/v2/airflow/connections
     */
    @PostMapping("/connections")
    public ResponseEntity<AirflowConnection> createConnection(@RequestBody AirflowConnection connection) {
        log.info("Creating Airflow Connection: {}", connection.getConnectionName());
        AirflowConnection saved = airflowConnectionRepository.save(connection);
        return ResponseEntity.ok(saved);
    }

    /**
     * Get all Airflow Connections
     * GET /api/v2/airflow/connections
     */
    @GetMapping("/connections")
    public ResponseEntity<List<AirflowConnection>> getAllConnections() {
        log.info("Getting all Airflow Connections");
        List<AirflowConnection> connections = airflowConnectionRepository.findAll();
        return ResponseEntity.ok(connections);
    }

    /**
     * Sync Airflow Variable cho Meta Process
     * POST /api/v2/airflow/sync-variable
     * 
     * Request body:
     * {
     *   "metaProcCode": "MP_SALE",
     *   "progId": 1,
     *   "periodId": 1,
     *   "connectionName": "default_airflow"
     * }
     */
    @PostMapping("/sync-variable")
    public ResponseEntity<Map<String, Object>> syncVariable(@RequestBody Map<String, Object> request) {
        String metaProcCode = (String) request.get("metaProcCode");
        Long progId = Long.valueOf(request.get("progId").toString());
        Long periodId = Long.valueOf(request.get("periodId").toString());
        String connectionName = (String) request.get("connectionName");

        log.info("Syncing Airflow Variable for Meta Process: {}", metaProcCode);

        Map<String, Object> result = airflowIntegrationService.syncAirflowVariable(
            metaProcCode, progId, periodId, connectionName);

        return ResponseEntity.ok(result);
    }

    /**
     * Get Airflow Variable
     * GET /api/v2/airflow/variable/{variableName}?connectionName=default
     */
    @GetMapping("/variable/{variableName}")
    public ResponseEntity<String> getVariable(
            @PathVariable String variableName,
            @RequestParam String connectionName) {
        log.info("Getting Airflow Variable: {}", variableName);
        String value = airflowIntegrationService.getAirflowVariable(connectionName, variableName);
        return ResponseEntity.ok(value);
    }

    /**
     * DEPRECATED - Phase 1 Legacy API
     * Sync DAG Config (Variable only, uses shared DAG template)
     * 
     * ⚠️ This API only syncs Airflow Variable to existing DAG template (e.g., uit_reward_mp_sale.py)
     * ⚠️ Use POST /api/v2/airflow/sync-process-to-dag for Phase 2 (Dynamic DAG generation)
     * 
     * POST /api/v2/airflow/sync-dag-config
     * 
     * Request body:
     * {
     *   "procCode": "PROC_META_STUDENT_ANALYTICS_20260104_001",
     *   "connectionName": "spring_boot_layer2_api"
     * }
     * 
     * @deprecated Use syncProcessToDag() instead for Phase 2 dynamic DAG generation
     */
    @Deprecated
    @PostMapping("/sync-dag-config")
    public ResponseEntity<Map<String, Object>> syncDagConfig(@RequestBody Map<String, String> request) {
        String procCode = request.get("procCode");
        String connectionName = request.get("connectionName");

        log.warn("⚠️ Using deprecated API /sync-dag-config. Consider migrating to /sync-process-to-dag for Phase 2.");
        log.info("Syncing DAG Config for Process: {}", procCode);

        Map<String, Object> result = airflowIntegrationService.syncDagConfigByProcess(procCode, connectionName);
        return ResponseEntity.ok(result);
    }

    /**
     * Trigger Airflow DAG
     * POST /api/v2/airflow/trigger-dag
     * 
     * Request body:
     * {
     *   "connectionName": "default_airflow",
     *   "dagId": "uit_reward_mp_sale",
     *   "conf": {
     *     "process_exec_code": "PROC_SALE_E20260104_001"
     *   }
     * }
     */
    @PostMapping("/trigger-dag")
    public ResponseEntity<Map<String, Object>> triggerDag(@RequestBody Map<String, Object> request) {
        String connectionName = (String) request.get("connectionName");
        String dagId = (String) request.get("dagId");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> conf = (Map<String, Object>) request.get("conf");

        log.info("Triggering DAG: {}", dagId);

        Map<String, Object> result = airflowIntegrationService.triggerDag(connectionName, dagId, conf);
        return ResponseEntity.ok(result);
    }

    /**
     * Phase 2: Sync Process to Dynamic DAG
     * Generate DAG file + Create Airflow Variable for specific Process
     * POST /api/v2/airflow/sync-process-to-dag
     * 
     * Request body:
     * {
     *   "procCode": "PROC_STUDENT_ANALYTICS_2026_1_202601",
     *   "connectionName": "spring_boot_layer2_api",
     *   "dagDirectory": "e:/nam3/DA1/pipline_data/airflow/dags"
     * }
     * 
     * Response:
     * {
     *   "success": true,
     *   "dagId": "uce_student_analytics_2026_1_202601",
     *   "dagFilePath": "e:/nam3/DA1/pipline_data/airflow/dags/uce_student_analytics_2026_1_202601.py",
     *   "variableName": "uce_var_proc_student_analytics_2026_1_202601",
     *   "procCode": "PROC_STUDENT_ANALYTICS_2026_1_202601",
     *   "metaProcCode": "STUDENT_ANALYTICS_2026",
     *   "totalTasks": 4,
     *   "scheduleInterval": "@daily",
     *   "airflowUrl": "http://localhost:8080/dags/uce_student_analytics_2026_1_202601"
     * }
     */
    @PostMapping("/sync-process-to-dag")
    public ResponseEntity<Map<String, Object>> syncProcessToDag(@RequestBody Map<String, String> request) {
        String procCode = request.get("procCode");
        String connectionName = request.get("connectionName");
        String dagDirectory = request.get("dagDirectory");

        if (dagDirectory == null || dagDirectory.isBlank()) {
            throw new IllegalArgumentException("dagDirectory is required. Please specify Airflow DAGs directory path.");
        }

        log.info("🚀 Phase 2: Syncing Process {} to Dynamic DAG at {}", procCode, dagDirectory);

        Map<String, Object> result = airflowIntegrationService.syncProcessToDag(procCode, connectionName, dagDirectory);
        return ResponseEntity.ok(result);
    }
}
