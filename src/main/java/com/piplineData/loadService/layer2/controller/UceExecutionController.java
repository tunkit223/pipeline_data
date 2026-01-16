package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.dto.ProcessExecutionRequest;
import com.piplineData.loadService.layer2.dto.TaskExecutionRequest;
import com.piplineData.loadService.layer2.dto.TaskExecutionResult;
import com.piplineData.loadService.layer2.entity.UceProcExec;
import com.piplineData.loadService.layer2.entity.UceTaskExec;
import com.piplineData.loadService.layer2.service.UceExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller cho Airflow gọi để thực thi Process và Task
 * Đây là endpoint chính mà Airflow DAG sẽ gọi
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/execution")
@RequiredArgsConstructor
public class UceExecutionController {

    private final UceExecutionService executionService;

    /**
     * Execute Task
     * Được gọi từ Airflow cho mỗi task trong DAG
     * POST /api/v2/execution/execute-task
     */
    @PostMapping("/execute-task")
    public ResponseEntity<TaskExecutionResult> executeTask(@RequestBody TaskExecutionRequest request) {
        log.info("Executing Task: {}", request.getTaskCode());
        log.debug("Request: {}", request);

        TaskExecutionResult result = executionService.executeTask(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Get Process Execution status
     * GET /api/v2/execution/process/{procExecCode}
     */
    @GetMapping("/process/{procExecCode}")
    public ResponseEntity<UceProcExec> getProcessExecution(@PathVariable String procExecCode) {
        log.info("Getting Process Execution: {}", procExecCode);
        UceProcExec procExec = executionService.getProcessExecution(procExecCode);
        return ResponseEntity.ok(procExec);
    }

    /**
     * Get Task Execution status
     * GET /api/v2/execution/task/{taskExecCode}
     */
    @GetMapping("/task/{taskExecCode}")
    public ResponseEntity<UceTaskExec> getTaskExecution(@PathVariable String taskExecCode) {
        log.info("Getting Task Execution: {}", taskExecCode);
        UceTaskExec taskExec = executionService.getTaskExecution(taskExecCode);
        return ResponseEntity.ok(taskExec);
    }

    /**
     * Health check endpoint cho Airflow
     * GET /api/v2/execution/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "UCE Execution Service",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}
