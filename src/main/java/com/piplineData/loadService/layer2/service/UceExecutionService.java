package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.dto.ProcessExecutionRequest;
import com.piplineData.loadService.layer2.dto.TaskExecutionRequest;
import com.piplineData.loadService.layer2.dto.TaskExecutionResult;
import com.piplineData.loadService.layer2.entity.*;
import com.piplineData.loadService.layer2.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UceProcessRepository processRepository;
    private final UceTaskRepository taskRepository;
    private final UceProcExecRepository procExecRepository;
    private final UceTaskExecRepository taskExecRepository;
    private final UceProcessService processService;
    private final TemplateRenderService templateRenderService;
    private final SqlExecutorService sqlExecutorService;

    /**
     * Khởi tạo Process Execution
     * Gọi từ Airflow khi DAG bắt đầu
     */
    @Transactional
    public ProcessCreationResult initProcessExecution(ProcessExecutionRequest request) {
        log.info("Initializing Process Execution: {}", request.getProcExecCode());

        // 1. Tạo hoặc lấy Process
        UceProcess process;
        if (request.getProcessCode() != null) {
            process = processRepository.findByProcCode(request.getProcessCode())
                .orElseThrow(() -> new RuntimeException("Process not found: " + request.getProcessCode()));
        } else {
            // Tạo mới Process từ Meta Process
            Map<String, Object> businessParams = request.getRuntimeParams() != null 
                ? request.getRuntimeParams() 
                : new HashMap<>();
            
            ProcessCreationResult result = processService.createProcessFromMeta(
                request.getMetaProcCode(),
                request.getCalcProgId(),
                request.getCalcPeriodId(),
                businessParams
            );
            
            process = processService.getProcess(result.getProcCode());
        }

        // 2. Create Process Execution log
        UceProcExec procExec = new UceProcExec();
        procExec.setProcExecCode(request.getProcExecCode());
        procExec.setProcCode(process.getProcCode());
        procExec.setMetaProcCode(process.getMetaProcCode());
        procExec.setCalcProgId(process.getCalcProgId());
        procExec.setCalcPeriodId(process.getCalcPeriodId());
        procExec.setStatus("RUNNING");

        procExecRepository.save(procExec);
        log.info("Created Process Execution: {}", request.getProcExecCode());

        // 3. Update Process status
        processService.updateProcessStatus(process.getProcCode(), "RUNNING");

        // 4. Get all tasks
        List<UceTask> tasks = taskRepository.findByProcCode(process.getProcCode());
        List<String> taskCodes = tasks.stream().map(UceTask::getTaskCode).toList();

        return ProcessCreationResult.builder()
            .procCode(process.getProcCode())
            .metaProcCode(process.getMetaProcCode())
            .procId(process.getProcId())
            .status("RUNNING")
            .taskCodes(taskCodes)
            .totalTasks(taskCodes.size())
            .message("Process execution initialized successfully")
            .build();
    }

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
            // 1. Get Task
            UceTask task = taskRepository.findByTaskCode(request.getTaskCode())
                .orElseThrow(() -> new RuntimeException("Task not found: " + request.getTaskCode()));

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

            // 6. Update Task status
            task.setStatus("SUCCESS");
            taskRepository.save(task);

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
     * Complete Process Execution
     * Gọi khi DAG kết thúc
     */
    @Transactional
    public void completeProcessExecution(String procExecCode, String status) {
        UceProcExec procExec = getProcessExecution(procExecCode);
        procExec.setStatus(status);
        procExecRepository.save(procExec);

        // Update Process status
        processService.updateProcessStatus(procExec.getProcCode(), status);
        
        log.info("Process Execution {} completed with status: {}", procExecCode, status);
    }
}
