package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.TaskExecutionRequest;
import com.piplineData.loadService.layer2.dto.TaskExecutionResult;
import com.piplineData.loadService.layer2.entity.*;
import com.piplineData.loadService.layer2.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service để thực thi Process và Task
 * Được gọi từ Airflow
Vả * Note: Sử dụng JdbcTemplate để lưu execution logs vào schema động
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceExecutionService {

    private final TemplateRenderService templateRenderService;
    private final SqlExecutorService sqlExecutorService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Execute một Task
     * Gọi từ Airflow cho mỗi task trong DAG
     */
    @Transactional
    public TaskExecutionResult executeTask(TaskExecutionRequest request) {
        log.info("Executing Task: {}", request.getTaskCode());
        
        LocalDateTime startTime = LocalDateTime.now();
        String taskExecCode = generateTaskExecCode(request.getTaskCode(), request.getProcExecCode());
        Long taskExecId = null; // For tracking in catch block

        try {
            // 1. Extract meta process code and find schema
            String metaProcCode = request.getMetaProcCode();
            if (metaProcCode == null || metaProcCode.isEmpty()) {
                // Try to extract from task code or process code
                throw new RuntimeException("metaProcCode is required in request");
            }
            
            String schema = findMetaProcessSchema(metaProcCode);
            log.info("Found schema for execution: {}", schema);
            
            // 1.5. Ensure Process Execution exists (lazy create)
            ensureProcessExecutionExists(request, schema);
            
            // 2. Get Task from dynamic schema
            UceTask task = queryTaskFromSchema(schema, request.getTaskCode());

            // 2. Get process_id and proc_exec_id
            Long processId = jdbcTemplate.queryForObject(
                String.format("SELECT id FROM %s.uce_process WHERE proc_code = ?", schema),
                Long.class,
                task.getProcCode()
            );
            
            Long procExecId = jdbcTemplate.queryForObject(
                String.format("SELECT id FROM %s.uce_proc_exec WHERE process_id = ? ORDER BY started_at DESC LIMIT 1", schema),
                Long.class,
                processId
            );
            
            // 3. Create Task Execution log in dynamic schema
            String insertTaskExecSQL = String.format(
                "INSERT INTO %s.uce_task_exec (proc_exec_id, task_id, status, started_at) VALUES (?, ?, ?, ?)",
                schema
            );
            
            jdbcTemplate.update(insertTaskExecSQL, procExecId, task.getTaskId(), "RUNNING", startTime);
            
            // Get the inserted task_exec_id for later updates
            taskExecId = jdbcTemplate.queryForObject(
                String.format("SELECT id FROM %s.uce_task_exec WHERE proc_exec_id = ? AND task_id = ? ORDER BY started_at DESC LIMIT 1", schema),
                Long.class,
                procExecId,
                task.getTaskId()
            );
            
            log.info("✅ Logged Task Execution id: {} in schema: {}", taskExecId, schema);

            // 3. Get runtime_params from uce_process (contains useVar with calc_schema)
            String getRuntimeParamsSQL = String.format(
                "SELECT runtime_params FROM %s.uce_process WHERE proc_code = ?",
                schema
            );
            String runtimeParamsJson = jdbcTemplate.queryForObject(getRuntimeParamsSQL, String.class, task.getProcCode());
            
            // 3.1 Parse runtime_params JSON and merge with request params
            Map<String, Object> runtimeParams = request.getRuntimeParams() != null 
                ? request.getRuntimeParams() 
                : new HashMap<>();

            // Parse and flatten runtime_params from database
            if (runtimeParamsJson != null && !runtimeParamsJson.isEmpty()) {
                Map<String, Object> dbParams = templateRenderService.flattenParams(
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(runtimeParamsJson, Map.class)
                );
                // Merge db params (higher priority for calc_schema, source_schema, etc.)
                dbParams.forEach(runtimeParams::putIfAbsent);
            }

            // Add request data to params
            runtimeParams.putIfAbsent("company_id", request.getCompanyId());
            runtimeParams.putIfAbsent("brand_id", request.getBrandId());
            runtimeParams.putIfAbsent("calc_prog_id", request.getCalcProgId());
            runtimeParams.putIfAbsent("calc_period_id", request.getCalcPeriodId());
            
            log.info("Runtime params for template rendering: {}", runtimeParams);

            // 4. Render SQL với runtime params
            String selectorFinal = null;
            String processorFinal = null;
            String insertorFinal = null;

            if (task.getSelectorBiz() != null) {
                selectorFinal = templateRenderService.render(task.getSelectorBiz(), runtimeParams);
            }

            if (task.getProcessorBiz() != null) {
                processorFinal = templateRenderService.render(task.getProcessorBiz(), runtimeParams);
            }

            if (task.getInsertorBiz() != null) {
                insertorFinal = templateRenderService.render(task.getInsertorBiz(), runtimeParams);
            }

            // 4. Execute SQL
            int rowsAffected = 0;
            List<Map<String, Object>> selectorResults = null;

            // Execute Selector (if exists) - SELECT data for transformation
            if (selectorFinal != null && !selectorFinal.trim().isEmpty()) {
                log.info("Executing Selector SQL");
                
                // Check if selector is CREATE TABLE AS SELECT or just SELECT
                if (selectorFinal.trim().toUpperCase().startsWith("CREATE TABLE") || 
                    selectorFinal.trim().toUpperCase().startsWith("DROP TABLE")) {
                    // Execute as DDL/DML statement
                    rowsAffected = sqlExecutorService.executeUpdate(selectorFinal);
                    log.info("Selector (DDL/DML) affected {} rows", rowsAffected);
                } else {
                    // Execute as SELECT query to get data for processing
                    selectorResults = sqlExecutorService.executeSelect(selectorFinal);
                    log.info("Selector returned {} rows", selectorResults.size());
                }
            }

            // Execute Processor (if exists) - Transform data
            if (processorFinal != null && !processorFinal.trim().isEmpty()) {
                log.info("Executing Processor SQL");
                int processorRows = sqlExecutorService.executeUpdate(processorFinal);
                log.info("Processor affected {} rows", processorRows);
                rowsAffected += processorRows;
            }

            // Execute Insertor (if exists) - Load data into target
            if (insertorFinal != null && !insertorFinal.trim().isEmpty()) {
                log.info("Executing Insertor SQL");
                
                // If we have selector results, use PreparedStatement batch insert
                if (selectorResults != null && !selectorResults.isEmpty()) {
                    log.info("Batch inserting {} rows from selector results", selectorResults.size());
                    int insertCount = sqlExecutorService.executeBatchInsert(insertorFinal, selectorResults);
                    log.info("Insertor affected {} rows", insertCount);
                    rowsAffected += insertCount;
                } else {
                    // Direct INSERT/UPDATE statement
                    int insertorRows = sqlExecutorService.executeUpdate(insertorFinal);
                    log.info("Insertor affected {} rows", insertorRows);
                    rowsAffected += insertorRows;
                }
            }

            // 5. Update Task Execution status to SUCCESS in dynamic schema
            LocalDateTime finishTime = LocalDateTime.now();
            String updateTaskExecSQL = String.format(
                "UPDATE %s.uce_task_exec SET status = ?, completed_at = ?, rows_affected = ? WHERE id = ?",
                schema
            );
            jdbcTemplate.update(updateTaskExecSQL, "SUCCESS", finishTime, rowsAffected, taskExecId);

            // 6. Update Task status in dynamic schema using JdbcTemplate
            String updateTaskSQL = String.format(
                "UPDATE %s.uce_task SET status = ? WHERE task_code = ?",
                schema
            );
            jdbcTemplate.update(updateTaskSQL, "SUCCESS", request.getTaskCode());

            long executionTimeMs = java.time.Duration.between(startTime, finishTime).toMillis();

            return TaskExecutionResult.builder()
                .taskExecCode(taskExecCode)
                .taskCode(request.getTaskCode())
                .procExecCode(request.getProcExecCode())
                .status("SUCCESS")
                .rowsAffected(rowsAffected)
                .startedAt(startTime)
                .finishedAt(finishTime)
                .executionTimeMs(executionTimeMs)
                .message("Task executed successfully")
                .build();

        } catch (Exception e) {
            log.error("Task execution failed: {}", e.getMessage(), e);

            // Extract schema again để update FAILED status
            try {
                String metaProcCode = request.getMetaProcCode();
                String schemaForUpdate = findMetaProcessSchema(metaProcCode);
                
                // Find task_exec_id by task_code and proc_exec
                Long taskId = jdbcTemplate.queryForObject(
                    String.format("SELECT task_id FROM %s.uce_task WHERE task_code = ?", schemaForUpdate),
                    Long.class,
                    request.getTaskCode()
                );
                
                // Update Task Execution status to FAILED
                String updateTaskExecSQL = String.format(
                    "UPDATE %s.uce_task_exec SET status = ?, completed_at = ?, error_message = ? WHERE task_id = ? AND status = 'RUNNING'",
                    schemaForUpdate
                );
                jdbcTemplate.update(updateTaskExecSQL, 
                    "FAILED", 
                    LocalDateTime.now(), 
                    "Error: " + e.getMessage(),
                    taskId
                );
            } catch (Exception updateEx) {
                log.error("Failed to update task execution status: {}", updateEx.getMessage());
            }

            return TaskExecutionResult.builder()
                .taskExecCode(taskExecCode)
                .taskCode(request.getTaskCode())
                .procExecCode(request.getProcExecCode())
                .status("FAILED")
                .startedAt(startTime)
                .finishedAt(LocalDateTime.now())
                .errorMessage(e.getMessage())
                .message("Task execution failed")
                .build();
        }
    }

    /**
     * Generate Task Execution Code
     * Format: {TASK_CODE}_E{TIMESTAMP}
     */
    private String generateTaskExecCode(String taskCode, String procExecCode) {
        // Extract timestamp from procExecCode nếu có
        String timestamp = java.time.LocalDateTime.now().toString().replace(":", "").replace("-", "").substring(0, 14);
        return taskCode + "_E" + timestamp;
    }

    /**
     * Get Process Execution status (scan tất cả schema động)
     */
    public UceProcExec getProcessExecution(String procExecCode) {
        // Find all schemas có bảng uce_proc_exec
        String findSchemasSQL = """
            SELECT table_schema 
            FROM information_schema.tables 
            WHERE table_name = 'uce_proc_exec'
            """;
        
        List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
        
        for (String schema : schemas) {
            String querySQL = String.format("""
                SELECT proc_exec_id, proc_exec_code, proc_code, meta_proc_code, 
                       calc_prog_id, calc_period_id, proc_exec_note, status, 
                       started_at, finished_at
                FROM %s.uce_proc_exec
                WHERE proc_exec_code = ?
                """, schema);
            
            try {
                return jdbcTemplate.queryForObject(querySQL, (rs, rowNum) -> {
                    UceProcExec procExec = new UceProcExec();
                    procExec.setProcExecId(rs.getLong("proc_exec_id"));
                    procExec.setProcExecCode(rs.getString("proc_exec_code"));
                    procExec.setProcCode(rs.getString("proc_code"));
                    procExec.setMetaProcCode(rs.getString("meta_proc_code"));
                    procExec.setCalcProgId(rs.getLong("calc_prog_id"));
                    procExec.setCalcPeriodId(rs.getLong("calc_period_id"));
                    procExec.setProcExecNote(rs.getString("proc_exec_note"));
                    procExec.setStatus(rs.getString("status"));
                    procExec.setStartedAt(rs.getTimestamp("started_at") != null ? 
                        rs.getTimestamp("started_at").toLocalDateTime() : null);
                    procExec.setFinishedAt(rs.getTimestamp("finished_at") != null ? 
                        rs.getTimestamp("finished_at").toLocalDateTime() : null);
                    return procExec;
                }, procExecCode);
            } catch (Exception e) {
                continue;
            }
        }
        
        throw new RuntimeException("Process Execution not found: " + procExecCode);
    }

    /**
     * Get Task Execution status (scan tất cả schema động)
     */
    public UceTaskExec getTaskExecution(String taskExecCode) {
        // Find all schemas có bảng uce_task_exec
        String findSchemasSQL = """
            SELECT table_schema 
            FROM information_schema.tables 
            WHERE table_name = 'uce_task_exec'
            """;
        
        List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
        
        for (String schema : schemas) {
            String querySQL = String.format("""
                SELECT task_exec_id, task_exec_code, proc_exec_code, task_code, 
                       proc_code, meta_proc_code, calc_prog_id, calc_period_id,
                       selector_biz_ctrl, processor_biz_ctrl, insertor_biz_ctrl,
                       status, started_at, finished_at, task_exec_note
                FROM %s.uce_task_exec
                WHERE task_exec_code = ?
                """, schema);
            
            try {
                return jdbcTemplate.queryForObject(querySQL, (rs, rowNum) -> {
                    UceTaskExec taskExec = new UceTaskExec();
                    taskExec.setTaskExecId(rs.getLong("task_exec_id"));
                    taskExec.setTaskExecCode(rs.getString("task_exec_code"));
                    taskExec.setProcExecCode(rs.getString("proc_exec_code"));
                    taskExec.setTaskCode(rs.getString("task_code"));
                    taskExec.setProcCode(rs.getString("proc_code"));
                    taskExec.setMetaProcCode(rs.getString("meta_proc_code"));
                    taskExec.setCalcProgId(rs.getLong("calc_prog_id"));
                    taskExec.setCalcPeriodId(rs.getLong("calc_period_id"));
                    taskExec.setSelectorBizCtrl(rs.getString("selector_biz_ctrl"));
                    taskExec.setProcessorBizCtrl(rs.getString("processor_biz_ctrl"));
                    taskExec.setInsertorBizCtrl(rs.getString("insertor_biz_ctrl"));
                    taskExec.setStatus(rs.getString("status"));
                    taskExec.setStartedAt(rs.getTimestamp("started_at") != null ? 
                        rs.getTimestamp("started_at").toLocalDateTime() : null);
                    taskExec.setFinishedAt(rs.getTimestamp("finished_at") != null ? 
                        rs.getTimestamp("finished_at").toLocalDateTime() : null);
                    taskExec.setTaskExecNote(rs.getString("task_exec_note"));
                    return taskExec;
                }, taskExecCode);
            } catch (Exception e) {
                continue;
            }
        }
        
        throw new RuntimeException("Task Execution not found: " + taskExecCode);
    }
    
    /**
     * Ensure Process Execution exists (tự động tạo nếu chưa có)
     * Lưu lại lịch sử process nào đã chạy vào schema động
     */
    @Transactional
    private void ensureProcessExecutionExists(TaskExecutionRequest request, String schema) {
        String procExecCode = request.getProcExecCode();
        
        // Check if already exists by checking if we can find proc_exec for this process
        // Note: Bảng dùng process_id (FK), không có proc_exec_code
        String processCode = request.getProcessCode();
        
        // Get process_id from proc_code
        String getProcessIdSQL = String.format(
            "SELECT id FROM %s.uce_process WHERE proc_code = ?",
            schema
        );
        
        try {
            Long processId = jdbcTemplate.queryForObject(getProcessIdSQL, Long.class, processCode);
            
            // Check if proc_exec already exists for this process
            String checkSQL = String.format(
                "SELECT COUNT(*) FROM %s.uce_proc_exec WHERE process_id = ? AND status = 'RUNNING'",
                schema
            );
            Integer count = jdbcTemplate.queryForObject(checkSQL, Integer.class, processId);
            if (count != null && count > 0) {
                return; // Already exists
            }
            
            // Insert new proc_exec
            String insertSQL = String.format(
                "INSERT INTO %s.uce_proc_exec (process_id, execution_params, status, started_at) VALUES (?, ?, ?, ?)",
                schema
            );
            
            String executionParams = String.format("proc_exec_code=%s, calc_prog_id=%d, calc_period_id=%d",
                procExecCode,
                request.getCalcProgId() != null ? request.getCalcProgId() : 0,
                request.getCalcPeriodId() != null ? request.getCalcPeriodId() : 0
            );
            
            jdbcTemplate.update(insertSQL, processId, executionParams, "RUNNING", LocalDateTime.now());
            log.info("✅ Logged Process Execution for process_id: {} in schema: {}", processId, schema);
            
        } catch (Exception e) {
            log.warn("Could not create proc_exec: {}", e.getMessage());
        }
    }
    
    /**
     * Helper: Find schema containing Meta Process
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
     * Query single Task from dynamic schema
     */
    private UceTask queryTaskFromSchema(String schema, String taskCode) {
        String sql = String.format("""
            SELECT id, task_code, proc_code, meta_task_code, meta_proc_code,
                   calc_prog_id, calc_period_id, selector_biz, processor_biz, 
                   insertor_biz, status, task_note
            FROM %s.uce_task
            WHERE task_code = ?
            """, schema);
        
        List<UceTask> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceTask task = new UceTask();
            task.setTaskId(rs.getLong("id"));  // Column name is 'id' not 'task_id'
            task.setTaskCode(rs.getString("task_code"));
            task.setProcCode(rs.getString("proc_code"));
            task.setMetaTaskCode(rs.getString("meta_task_code"));
            task.setMetaProcCode(rs.getString("meta_proc_code"));
            task.setCalcProgId(rs.getLong("calc_prog_id"));
            task.setCalcPeriodId(rs.getLong("calc_period_id"));
            task.setSelectorBiz(rs.getString("selector_biz"));
            task.setProcessorBiz(rs.getString("processor_biz"));
            task.setInsertorBiz(rs.getString("insertor_biz"));
            task.setStatus(rs.getString("status"));
            task.setTaskNote(rs.getString("task_note"));
            return task;
        }, taskCode);
        
        if (results.isEmpty()) {
            throw new RuntimeException("Task not found: " + taskCode);
        }
        
        return results.get(0);
    }
}
