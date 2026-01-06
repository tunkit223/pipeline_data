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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceExecutionService {

    private final UceProcExecRepository procExecRepository;
    private final UceTaskExecRepository taskExecRepository;
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

        try {
            // 1. Extract meta process code and find schema
            String metaProcCode = request.getMetaProcCode();
            if (metaProcCode == null || metaProcCode.isEmpty()) {
                // Try to extract from task code or process code
                throw new RuntimeException("metaProcCode is required in request");
            }
            
            String schema = findMetaProcessSchema(metaProcCode);
            log.info("Found schema for execution: {}", schema);
            
            // 2. Get Task from dynamic schema
            UceTask task = queryTaskFromSchema(schema, request.getTaskCode());

            // 2. Create Task Execution log
            UceTaskExec taskExec = new UceTaskExec();
            taskExec.setTaskExecCode(taskExecCode);
            taskExec.setProcExecCode(request.getProcExecCode());
            taskExec.setTaskCode(request.getTaskCode());
            taskExec.setProcCode(task.getProcCode());
            taskExec.setMetaProcCode(task.getMetaProcCode());
            taskExec.setCalcProgId(task.getCalcProgId());
            taskExec.setCalcPeriodId(task.getCalcPeriodId());
            taskExec.setStatus("RUNNING");
            taskExec.setStartedAt(startTime);

            taskExecRepository.save(taskExec);

            // 3. Render SQL với runtime params (rule điều khiển)
            Map<String, Object> runtimeParams = request.getRuntimeParams() != null 
                ? request.getRuntimeParams() 
                : new HashMap<>();

            // Add request data to params
            runtimeParams.putIfAbsent("company_id", request.getCompanyId());
            runtimeParams.putIfAbsent("brand_id", request.getBrandId());
            runtimeParams.putIfAbsent("calc_prog_id", request.getCalcProgId());
            runtimeParams.putIfAbsent("calc_period_id", request.getCalcPeriodId());

            String selectorFinal = null;
            String processorFinal = null;
            String insertorFinal = null;

            if (task.getSelectorBiz() != null) {
                selectorFinal = templateRenderService.render(task.getSelectorBiz(), runtimeParams);
                taskExec.setSelectorBizCtrl(selectorFinal);
            }

            if (task.getProcessorBiz() != null) {
                processorFinal = templateRenderService.render(task.getProcessorBiz(), runtimeParams);
                taskExec.setProcessorBizCtrl(processorFinal);
            }

            if (task.getInsertorBiz() != null) {
                insertorFinal = templateRenderService.render(task.getInsertorBiz(), runtimeParams);
                taskExec.setInsertorBizCtrl(insertorFinal);
            }

            // 4. Execute SQL
            int rowsAffected = 0;

            // Execute Selector (if exists) - usually for creating result tables
            if (selectorFinal != null && !selectorFinal.trim().isEmpty()) {
                log.info("Executing Selector SQL");
                
                // Check if selector is CREATE TABLE AS SELECT or just SELECT
                if (selectorFinal.trim().toUpperCase().startsWith("CREATE TABLE") || 
                    selectorFinal.trim().toUpperCase().startsWith("DROP TABLE")) {
                    // Execute as DDL/DML statement
                    rowsAffected = sqlExecutorService.executeUpdate(selectorFinal);
                    log.info("Selector (DDL/DML) affected {} rows", rowsAffected);
                } else {
                    // Execute as SELECT query
                    List<Map<String, Object>> selectorResults = sqlExecutorService.executeSelect(selectorFinal);
                    log.info("Selector returned {} rows", selectorResults.size());
                    rowsAffected = selectorResults.size();
                }
            }

            // Execute Processor (if exists)
            if (processorFinal != null && !processorFinal.trim().isEmpty()) {
                log.info("Executing Processor SQL");
                int processorRows = sqlExecutorService.executeUpdate(processorFinal);
                log.info("Processor affected {} rows", processorRows);
                rowsAffected += processorRows;
            }

            // Execute Insertor (if exists)
            if (insertorFinal != null && !insertorFinal.trim().isEmpty()) {
                log.info("Executing Insertor SQL");
                int insertorRows = sqlExecutorService.executeUpdate(insertorFinal);
                log.info("Insertor affected {} rows", insertorRows);
                rowsAffected += insertorRows;
            }

            // 5. Update Task Execution status
            LocalDateTime finishTime = LocalDateTime.now();
            taskExec.setStatus("SUCCESS");
            taskExec.setFinishedAt(finishTime);
            taskExecRepository.save(taskExec);

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

            // Update Task Execution status to FAILED
            UceTaskExec taskExec = taskExecRepository.findByTaskExecCode(taskExecCode).orElse(null);
            if (taskExec != null) {
                taskExec.setStatus("FAILED");
                taskExec.setFinishedAt(LocalDateTime.now());
                taskExec.setTaskExecNote("Error: " + e.getMessage());
                taskExecRepository.save(taskExec);
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
     * Get Process Execution status
     */
    public UceProcExec getProcessExecution(String procExecCode) {
        return procExecRepository.findByProcExecCode(procExecCode)
            .orElseThrow(() -> new RuntimeException("Process Execution not found: " + procExecCode));
    }

    /**
     * Get Task Execution status
     */
    public UceTaskExec getTaskExecution(String taskExecCode) {
        return taskExecRepository.findByTaskExecCode(taskExecCode)
            .orElseThrow(() -> new RuntimeException("Task Execution not found: " + taskExecCode));
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
            SELECT task_id, task_code, proc_code, meta_task_code, meta_proc_code,
                   calc_prog_id, calc_period_id, selector_biz, processor_biz, 
                   insertor_biz, status, task_note
            FROM %s.uce_task
            WHERE task_code = ?
            """, schema);
        
        List<UceTask> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            UceTask task = new UceTask();
            task.setTaskId(rs.getLong("task_id"));
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
