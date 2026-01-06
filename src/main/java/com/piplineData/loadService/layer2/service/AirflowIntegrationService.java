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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();
    private final DagFileGeneratorService dagFileGeneratorService;

    /**
     * Helper: Tìm schema chứa Meta Process
     */
    private String findMetaProcessSchema(String metaProcCode) {
        String findSchemasSQL = """
            SELECT table_schema 
            FROM information_schema.tables 
            WHERE table_name = 'uce_meta_process'
            """;
        
        List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
        
        for (String schema : schemas) {
            String checkSQL = String.format(
                "SELECT metadata_schema FROM %s.uce_meta_process WHERE meta_proc_code = ?", 
                schema
            );
            try {
                return jdbcTemplate.queryForObject(checkSQL, String.class, metaProcCode);
            } catch (Exception e) {
                continue;
            }
        }
        
        throw new RuntimeException("Meta Process not found: " + metaProcCode);
    }
    
    /**
     * Helper: Extract Meta Process Code from Process Code
     * Format: STUDENT_ANALYTICS_2026_P0_PER0_001 -> STUDENT_ANALYTICS_2026
     */
    private String extractMetaProcCodeFromProcCode(String procCode) {
        // Remove pattern: _P{progId}_PER{periodId}_{sequence}
        return procCode.replaceAll("_P\\d+_PER\\d+_\\d+$", "");
    }
    
    /**
     * Query Meta Tasks từ dynamic schema
     */
    private List<UceMetaTask> queryMetaTasksFromSchema(String schema, String metaProcCode) {
        String sql = String.format("""
            SELECT meta_task_code, meta_task_name, meta_proc_code, task_order, 
                   meta_task_type, pre_meta_task_codelist, post_meta_task_codelist,
                   selector, processor, insertor, meta_task_note, is_active,
                   is_starting, is_ending
            FROM %s.uce_meta_task
            WHERE meta_proc_code = ? AND is_active = true
            ORDER BY task_order
            """, schema);
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceMetaTask task = new UceMetaTask();
            task.setMetaTaskCode(rs.getString("meta_task_code"));
            task.setMetaTaskName(rs.getString("meta_task_name"));
            task.setMetaProcCode(rs.getString("meta_proc_code"));
            task.setTaskOrder(rs.getInt("task_order"));
            task.setMetaTaskType(rs.getString("meta_task_type"));
            task.setPreMetaTaskCodelist(rs.getString("pre_meta_task_codelist"));
            task.setPostMetaTaskCodelist(rs.getString("post_meta_task_codelist"));
            task.setSelector(rs.getString("selector"));
            task.setProcessor(rs.getString("processor"));
            task.setInsertor(rs.getString("insertor"));
            task.setMetaTaskNote(rs.getString("meta_task_note"));
            task.setIsActive(rs.getBoolean("is_active"));
            task.setIsStarting(rs.getBoolean("is_starting"));
            task.setIsEnding(rs.getBoolean("is_ending"));
            return task;
        }, metaProcCode);
    }

    /**
     * Helper: Tìm schema chứa Process
     */
    private String findProcessSchema(String procCode) {
        String findSchemasSQL = """
            SELECT table_schema 
            FROM information_schema.tables 
            WHERE table_name = 'uce_process'
            """;
        
        List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
        
        for (String schema : schemas) {
            String checkSQL = String.format(
                "SELECT COUNT(*) FROM %s.uce_process WHERE proc_code = ?", 
                schema
            );
            try {
                Integer count = jdbcTemplate.queryForObject(checkSQL, Integer.class, procCode);
                if (count != null && count > 0) {
                    return schema;
                }
            } catch (Exception e) {
                continue;
            }
        }
        
        throw new RuntimeException("Process not found: " + procCode);
    }
    
    /**
     * Query Process từ dynamic schema
     */
    private UceProcess queryProcessFromSchema(String schema, String procCode) {
        String sql = String.format("""
            SELECT proc_id, proc_code, meta_proc_code, calc_prog_id, calc_period_id, 
                   status, is_lasted, proc_note
            FROM %s.uce_process
            WHERE proc_code = ?
            """, schema);
        
        List<UceProcess> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceProcess process = new UceProcess();
            process.setProcId(rs.getLong("proc_id"));
            process.setProcCode(rs.getString("proc_code"));
            process.setMetaProcCode(rs.getString("meta_proc_code"));
            process.setCalcProgId(rs.getLong("calc_prog_id"));
            process.setCalcPeriodId(rs.getLong("calc_period_id"));
            process.setStatus(rs.getString("status"));
            process.setIsLasted(rs.getBoolean("is_lasted"));
            process.setProcNote(rs.getString("proc_note"));
            return process;
        }, procCode);
        
        if (results.isEmpty()) {
            throw new RuntimeException("Process not found: " + procCode);
        }
        
        return results.get(0);
    }
    
    /**
     * Query Tasks từ dynamic schema
     */
    private List<UceTask> queryTasksFromSchema(String schema, String procCode) {
        String sql = String.format("""
            SELECT task_id, task_code, proc_code, meta_task_code, meta_proc_code,
                   calc_prog_id, calc_period_id, selector_biz, processor_biz, 
                   insertor_biz, status, task_note
            FROM %s.uce_task
            WHERE proc_code = ?
            ORDER BY task_id
            """, schema);
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceTask task = new UceTask();
            task.setTaskId(rs.getLong("task_id"));
            task.setTaskCode(rs.getString("task_code"));
            task.setProcCode(rs.getString("proc_code"));
            task.setMetaTaskCode(rs.getString("meta_task_code"));
            task.setSelectorBiz(rs.getString("selector_biz"));
            task.setProcessorBiz(rs.getString("processor_biz"));
            task.setInsertorBiz(rs.getString("insertor_biz"));
            task.setStatus(rs.getString("status"));
            task.setTaskNote(rs.getString("task_note"));
            return task;
        }, procCode);
    }
    
    /**
     * Query single Meta Task từ dynamic schema
     */
    private UceMetaTask queryMetaTaskFromSchema(String schema, String metaTaskCode) {
        String sql = String.format("""
            SELECT meta_task_code, meta_task_name, meta_proc_code, task_order, 
                   meta_task_type, pre_meta_task_codelist, post_meta_task_codelist,
                   selector, processor, insertor, meta_task_note, is_active,
                   is_starting, is_ending
            FROM %s.uce_meta_task
            WHERE meta_task_code = ?
            """, schema);
        
        List<UceMetaTask> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceMetaTask task = new UceMetaTask();
            task.setMetaTaskCode(rs.getString("meta_task_code"));
            task.setMetaTaskName(rs.getString("meta_task_name"));
            task.setMetaProcCode(rs.getString("meta_proc_code"));
            task.setTaskOrder(rs.getInt("task_order"));
            task.setMetaTaskType(rs.getString("meta_task_type"));
            task.setPreMetaTaskCodelist(rs.getString("pre_meta_task_codelist"));
            task.setPostMetaTaskCodelist(rs.getString("post_meta_task_codelist"));
            task.setSelector(rs.getString("selector"));
            task.setProcessor(rs.getString("processor"));
            task.setInsertor(rs.getString("insertor"));
            task.setMetaTaskNote(rs.getString("meta_task_note"));
            task.setIsActive(rs.getBoolean("is_active"));
            task.setIsStarting(rs.getBoolean("is_starting"));
            task.setIsEnding(rs.getBoolean("is_ending"));
            return task;
        }, metaTaskCode);
        
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Sync Airflow Variable cho một Meta Process
     * Tạo Variable JSON format như trong DAG mẫu
     */
    public Map<String, Object> syncAirflowVariable(String metaProcCode, Long progId, Long periodId, String connectionName) {
        log.info("Syncing Airflow Variable for Meta Process: {}", metaProcCode);

        try {
            // 0. Tìm schema chứa Meta Process
            String schema = findMetaProcessSchema(metaProcCode);
            log.info("Found Meta Process {} in schema: {}", metaProcCode, schema);
            
            // 1. Get Airflow Connection
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            // 2. Get Program-MetaProcess mapping
            UceProgUseMetaProcess progUseMetaProcess = progUseMetaProcessRepository
                .findByProgIdAndPeriodIdAndMetaProcCode(progId, periodId, metaProcCode)
                .orElseThrow(() -> new RuntimeException("Program-MetaProcess mapping not found"));

            // 3. Query Meta Tasks từ dynamic schema
            List<UceMetaTask> metaTasks = queryMetaTasksFromSchema(schema, metaProcCode);
            log.info("Found {} Meta Tasks in schema {}", metaTasks.size(), schema);

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
            // 1. Extract meta_proc_code from proc_code
            // Format: META_PROC_CODE_P{progId}_PER{periodId}_{sequence}
            String metaProcCode = extractMetaProcCodeFromProcCode(procCode);
            log.info("Extracted Meta Process Code: {}", metaProcCode);
            
            // 2. Find schema containing the Meta Process (which is also where Process is stored)
            String schema = findMetaProcessSchema(metaProcCode);
            log.info("Found Meta Process {} in schema: {}", metaProcCode, schema);
            
            // 3. Get Process Instance from same schema
            UceProcess process = queryProcessFromSchema(schema, procCode);

            // 4. Get Airflow Connection
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            // 5. Get all Tasks of this Process from dynamic schema
            List<UceTask> tasks = queryTasksFromSchema(schema, procCode);

            // 4. Build Task Definitions
            List<AirflowVariableDto.TaskDefinition> taskDefs = new ArrayList<>();
            
            for (UceTask task : tasks) {
                // Extract meta task code from task code
                // Format: PROC_XXX_META_TASK_YYY -> META_TASK_YYY
                String metaTaskCode = task.getMetaTaskCode();
                
                // Get meta task from same schema to find dependencies
                UceMetaTask metaTask = queryMetaTaskFromSchema(schema, metaTaskCode);

                List<String> dependsOn = new ArrayList<>();
                if (metaTask != null && metaTask.getPreMetaTaskCodelist() != null && !metaTask.getPreMetaTaskCodelist().trim().isEmpty()) {
                    String[] deps = metaTask.getPreMetaTaskCodelist().split(",");
                    for (String dep : deps) {
                        // Convert meta task code to actual task code
                        // dep is already meta_task_code (e.g., TASK_STUDENTS_DIM)
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

    /**
     * Phase 2: Sync Process to Dynamic DAG
     * Generate DAG file + Create Airflow Variable for specific Process
     * 
     * @param procCode Process code
     * @param connectionName Airflow connection name
     * @param dagDirectory Airflow DAGs directory path (user-provided)
     * @return Sync result with DAG ID, variable name, file path
     */
    public Map<String, Object> syncProcessToDag(String procCode, String connectionName, String dagDirectory) {
        log.info("🚀 Phase 2: Syncing Process {} to Dynamic DAG", procCode);

        try {
            // 1. Find schema and query Process
            String schema = findProcessSchema(procCode);
            log.info("Found Process {} in schema: {}", procCode, schema);
            
            UceProcess process = queryProcessFromSchema(schema, procCode);
            
            // 2. Get Airflow Connection
            AirflowConnection connection = airflowConnectionRepository.findByConnectionName(connectionName)
                .orElseThrow(() -> new RuntimeException("Airflow Connection not found: " + connectionName));

            // 3. Query Meta Process to get schedule_interval
            String metaProcCode = process.getMetaProcCode();
            String metaSchema = findMetaProcessSchema(metaProcCode);
            
            String scheduleInterval = null;
            try {
                String scheduleSQL = String.format(
                    "SELECT schedule_interval FROM %s.uce_meta_process WHERE meta_proc_code = ?", 
                    metaSchema
                );
                scheduleInterval = jdbcTemplate.queryForObject(scheduleSQL, String.class, metaProcCode);
            } catch (Exception e) {
                log.warn("Could not get schedule_interval for {}: {}", metaProcCode, e.getMessage());
            }
            
            // 4. Query Tasks from dynamic schema
            List<UceTask> tasks = queryTasksFromSchema(schema, procCode);
            log.info("Found {} Tasks for Process {}", tasks.size(), procCode);

            // 5. Query Meta Tasks to get dependencies
            List<UceMetaTask> metaTasks = queryMetaTasksFromSchema(metaSchema, metaProcCode);
            
            // 6. Build Task Definitions with dependencies
            List<AirflowVariableDto.TaskDefinition> taskDefs = new ArrayList<>();
            
            for (UceTask task : tasks) {
                String metaTaskCode = extractMetaTaskCode(task.getTaskCode(), procCode);
                
                // Find corresponding Meta Task for dependencies
                UceMetaTask metaTask = queryMetaTaskFromSchema(metaSchema, metaTaskCode);
                
                List<String> dependsOn = new ArrayList<>();
                if (metaTask != null && metaTask.getPreMetaTaskCodelist() != null 
                    && !metaTask.getPreMetaTaskCodelist().trim().isEmpty()) {
                    
                    String[] deps = metaTask.getPreMetaTaskCodelist().split(",");
                    for (String dep : deps) {
                        // Convert meta task code to actual task code
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

            // 7. Build Airflow Variable DTO
            AirflowVariableDto variableDto = AirflowVariableDto.builder()
                .tasks(taskDefs)
                .httpConnId(connection.getConnectionName())
                .processCode(procCode)
                .companyId(0L)  // Will be provided at runtime via runtimeParams
                .brandId(0L)    // Will be provided at runtime via runtimeParams
                .calculatedProgId(process.getCalcProgId())
                .calculatedPeriodId(process.getCalcPeriodId())
                .metaProcessCode(metaProcCode)
                .build();

            // 8. Generate unique DAG ID and Variable name
            String dagId = String.format("uce_%s_%s_%s", 
                metaProcCode.toLowerCase(),
                process.getCalcProgId() != null ? process.getCalcProgId() : "0",
                process.getCalcPeriodId() != null ? process.getCalcPeriodId() : "0");
            
            String variableName = String.format("uce_var_%s", procCode.toLowerCase());

            // 9. Generate DAG file
            java.nio.file.Path dagFilePath = dagFileGeneratorService.generateDagFile(
                dagDirectory,
                procCode,
                metaProcCode,
                process.getCalcProgId(),
                process.getCalcPeriodId(),
                scheduleInterval,
                null // tasks will be loaded from variable
            );

            // 10. Create/Update Airflow Variable
            String variableJson = objectMapper.writeValueAsString(variableDto);
            boolean variableSuccess = createOrUpdateAirflowVariable(connection, variableName, variableJson);

            // 11. Build result
            Map<String, Object> result = new HashMap<>();
            result.put("success", variableSuccess);
            result.put("dagId", dagId);
            result.put("dagFilePath", dagFilePath.toString());
            result.put("variableName", variableName);
            result.put("procCode", procCode);
            result.put("metaProcCode", metaProcCode);
            result.put("totalTasks", taskDefs.size());
            result.put("scheduleInterval", scheduleInterval != null ? scheduleInterval : "None (manual trigger)");
            result.put("airflowUrl", connection.getAirflowBaseUrl() + "/dags/" + dagId);

            log.info("✅ Process {} synced to DAG successfully!", procCode);
            log.info("   DAG ID: {}", dagId);
            log.info("   Variable: {}", variableName);
            log.info("   File: {}", dagFilePath);
            log.info("   Schedule: {}", scheduleInterval != null ? scheduleInterval : "None");
            
            return result;

        } catch (Exception e) {
            log.error("Failed to sync Process to DAG: {}", e.getMessage(), e);
            throw new RuntimeException("Process DAG sync failed: " + e.getMessage(), e);
        }
    }
}

