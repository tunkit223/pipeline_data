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
     * Sync DAG Config based on Process Instance
     * POST /api/v2/airflow/sync-dag-config
     * 
     * Request body:
     * {
     *   "procCode": "PROC_META_STUDENT_ANALYTICS_20260104_001",
     *   "connectionName": "spring_boot_layer2_api"
     * }
     */
    @PostMapping("/sync-dag-config")
    public ResponseEntity<Map<String, Object>> syncDagConfig(@RequestBody Map<String, String> request) {
        String procCode = request.get("procCode");
        String connectionName = request.get("connectionName");

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
}
