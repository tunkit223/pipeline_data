package com.piplineData.loadService.layer2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.layer2.dto.AirflowVariableDto;
import com.piplineData.loadService.layer2.entity.AirflowConnection;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.entity.UceProcess;
import com.piplineData.loadService.layer2.entity.UceProgUseMetaProcess;
import com.piplineData.loadService.layer2.entity.UceTask;
import com.piplineData.loadService.layer2.repository.AirflowConnectionRepository;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import com.piplineData.loadService.layer2.repository.UceProcessRepository;
import com.piplineData.loadService.layer2.repository.UceProgUseMetaProcessRepository;
import com.piplineData.loadService.layer2.repository.UceTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service để tích hợp với Airflow
 * - Sync Variable
 * - Trigger DAG
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AirflowIntegrationService {

    private final AirflowConnectionRepository airflowConnectionRepository;
    private final UceMetaTaskRepository metaTaskRepository;
    private final UceProgUseMetaProcessRepository progUseMetaProcessRepository;
    private final UceProcessRepository processRepository;
    private final UceTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sync Airflow Variable cho một Meta Process
     * Tạo Variable JSON format như trong DAG mẫu
     */
    public Map<String, Object> syncAirflowVariable(String metaProcCode, Long progId, Long periodId, String connectionName) {
        log.info("Syncing Airflow Variable for Meta Process: {}", metaProcCode);

        try {
            // 1. Get Airflow Connection
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            // 2. Get Program-MetaProcess mapping
            UceProgUseMetaProcess progUseMetaProcess = progUseMetaProcessRepository
                .findByProgIdAndPeriodIdAndMetaProcCode(progId, periodId, metaProcCode)
                .orElseThrow(() -> new RuntimeException("Program-MetaProcess mapping not found"));

            // 3. Get all Meta Tasks
            List<UceMetaTask> metaTasks = metaTaskRepository.findByMetaProcCodeAndIsActive(metaProcCode, true);

            // 4. Build Task Definitions
            List<AirflowVariableDto.TaskDefinition> taskDefs = new ArrayList<>();
            
            for (UceMetaTask metaTask : metaTasks) {
                List<String> dependsOn = new ArrayList<>();
                
                // Parse dependencies
                if (metaTask.getPreMetaTaskCodelist() != null && !metaTask.getPreMetaTaskCodelist().trim().isEmpty()) {
                    String[] deps = metaTask.getPreMetaTaskCodelist().split(",");
                    for (String dep : deps) {
                        dependsOn.add(dep.trim());
                    }
                }

                AirflowVariableDto.TaskDefinition taskDef = AirflowVariableDto.TaskDefinition.builder()
                    .taskCode(metaTask.getMetaTaskCode())
                    .endpoint("/api/v2/execution/execute-task")
                    .dependsOn(dependsOn)
                    .build();

                taskDefs.add(taskDef);
            }

            // 5. Build Airflow Variable DTO
            AirflowVariableDto variableDto = AirflowVariableDto.builder()
                .tasks(taskDefs)
                .httpConnId(progUseMetaProcess.getConnectionId())
                .processCode("PROC_" + metaProcCode)
                .companyId(0L) // Will be provided at runtime
                .brandId(0L)
                .calculatedProgId(progId)
                .calculatedPeriodId(periodId)
                .metaProcessCode(metaProcCode)
                .build();

            // 6. Call Airflow API to create/update Variable
            String variableName = progUseMetaProcess.getUseVar();
            String variableJson = objectMapper.writeValueAsString(variableDto);

            boolean success = createOrUpdateAirflowVariable(connection, variableName, variableJson);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("variableName", variableName);
            result.put("totalTasks", taskDefs.size());
            result.put("dagId", progUseMetaProcess.getDagId());

            log.info("Airflow Variable synced successfully: {}", variableName);
            return result;

        } catch (Exception e) {
            log.error("Failed to sync Airflow Variable: {}", e.getMessage(), e);
            throw new RuntimeException("Airflow sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create or Update Airflow Variable via REST API
     */
    private boolean createOrUpdateAirflowVariable(AirflowConnection connection, String variableName, String variableValue) {
        try {
            String url = connection.getAirflowBaseUrl() + 
                        (connection.getApiEndpointVariable() != null ? connection.getApiEndpointVariable() : "/api/v1/variables");

            // Build request
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("key", variableName);
            requestBody.put("value", variableValue);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Add Basic Auth if provided
            if (connection.getAirflowUsername() != null && connection.getAirflowPassword() != null) {
                String auth = connection.getAirflowUsername() + ":" + connection.getAirflowPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            // Try PATCH first (update), then POST (create)
            try {
                restTemplate.exchange(url + "/" + variableName, HttpMethod.PATCH, request, String.class);
                log.info("Updated Airflow Variable: {}", variableName);
            } catch (Exception e) {
                // Variable không tồn tại, tạo mới
                restTemplate.postForEntity(url, request, String.class);
                log.info("Created Airflow Variable: {}", variableName);
            }

            return true;

        } catch (Exception e) {
            log.error("Failed to create/update Airflow Variable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get Airflow Variable
     */
    public String getAirflowVariable(String connectionName, String variableName) {
        try {
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            String url = connection.getAirflowBaseUrl() + 
                        (connection.getApiEndpointVariable() != null ? connection.getApiEndpointVariable() : "/api/v1/variables") +
                        "/" + variableName;

            HttpHeaders headers = new HttpHeaders();
            if (connection.getAirflowUsername() != null && connection.getAirflowPassword() != null) {
                String auth = connection.getAirflowUsername() + ":" + connection.getAirflowPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<String> request = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            if (response.getBody() != null) {
                return (String) response.getBody().get("value");
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to get Airflow Variable: {}", e.getMessage());
            throw new RuntimeException("Failed to get Airflow Variable: " + e.getMessage(), e);
        }
    }

    /**
     * Trigger Airflow DAG
     */
    public Map<String, Object> triggerDag(String connectionName, String dagId, Map<String, Object> conf) {
        try {
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            String url = connection.getAirflowBaseUrl() + 
                        (connection.getApiEndpointDagRun() != null 
                            ? connection.getApiEndpointDagRun().replace("{dag_id}", dagId)
                            : "/api/v1/dags/" + dagId + "/dagRuns");

            Map<String, Object> requestBody = new HashMap<>();
            if (conf != null) {
                requestBody.put("conf", conf);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (connection.getAirflowUsername() != null && connection.getAirflowPassword() != null) {
                String auth = connection.getAirflowUsername() + ":" + connection.getAirflowPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            log.info("DAG triggered successfully: {}", dagId);
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to trigger DAG: {}", e.getMessage());
            throw new RuntimeException("Failed to trigger DAG: " + e.getMessage(), e);
        }
    }

    /**
     * Sync DAG Config based on Process Instance
     * Used when Process Instance already created, need to sync config to Airflow Variable
     */
    public Map<String, Object> syncDagConfigByProcess(String procCode, String connectionName) {
        log.info("Syncing DAG Config for Process: {}", procCode);

        try {
            // 1. Get Process Instance
            UceProcess process = processRepository.findByProcCode(procCode)
                .orElseThrow(() -> new RuntimeException("Process not found: " + procCode));

            // 2. Get Airflow Connection
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            // 3. Get all Tasks of this Process
            List<UceTask> tasks = taskRepository.findByProcCode(procCode);

            // 4. Build Task Definitions
            List<AirflowVariableDto.TaskDefinition> taskDefs = new ArrayList<>();
            
            for (UceTask task : tasks) {
                // Extract meta task code from task code
                // Format: PROC_XXX_META_TASK_YYY -> META_TASK_YYY
                String metaTaskCode = extractMetaTaskCode(task.getTaskCode(), procCode);
                
                // Get meta task to find dependencies
                UceMetaTask metaTask = metaTaskRepository.findByMetaTaskCode(metaTaskCode)
                    .orElse(null);

                List<String> dependsOn = new ArrayList<>();
                if (metaTask != null && metaTask.getPreMetaTaskCodelist() != null && !metaTask.getPreMetaTaskCodelist().trim().isEmpty()) {
                    String[] deps = metaTask.getPreMetaTaskCodelist().split(",");
                    for (String dep : deps) {
                        // Convert meta task code dependencies to actual task codes
                        String depTaskCode = procCode + "_" + dep.trim();
                        dependsOn.add(depTaskCode);
                    }
                }

                AirflowVariableDto.TaskDefinition taskDef = AirflowVariableDto.TaskDefinition.builder()
                    .taskCode(task.getTaskCode())
                    .endpoint("/api/v2/execution/execute-task")
                    .dependsOn(dependsOn)
                    .build();

                taskDefs.add(taskDef);
            }

            // 5. Build Airflow Variable DTO
            AirflowVariableDto variableDto = AirflowVariableDto.builder()
                .tasks(taskDefs)
                .httpConnId(connection.getConnectionName())
                .processCode(procCode)
                .companyId(0L)
                .brandId(0L)
                .calculatedProgId(process.getCalcProgId())
                .calculatedPeriodId(process.getCalcPeriodId())
                .metaProcessCode(process.getMetaProcCode())
                .build();

            // 6. Determine variable name (use default pattern)
            String variableName = "uit_var_" + process.getMetaProcCode().toLowerCase().replace("meta_", "");

            // 7. Call Airflow API to create/update Variable
            String variableJson = objectMapper.writeValueAsString(variableDto);
            boolean success = createOrUpdateAirflowVariable(connection, variableName, variableJson);

            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("variableName", variableName);
            result.put("procCode", procCode);
            result.put("totalTasks", taskDefs.size());
            result.put("airflowUrl", connection.getAirflowBaseUrl());

            log.info("DAG Config synced successfully for Process: {}", procCode);
            return result;

        } catch (Exception e) {
            log.error("Failed to sync DAG config: {}", e.getMessage(), e);
            throw new RuntimeException("DAG config sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract Meta Task Code from Task Code
     * Format: PROC_XXX_META_TASK_YYY -> META_TASK_YYY
     */
    private String extractMetaTaskCode(String taskCode, String procCode) {
        if (taskCode.startsWith(procCode + "_")) {
            return taskCode.substring(procCode.length() + 1);
        }
        return taskCode;
    }
}
