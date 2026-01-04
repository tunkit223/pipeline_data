package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.MetaProcessRequest;
import com.piplineData.loadService.layer2.dto.MetaProcessWithTasks;
import com.piplineData.loadService.layer2.dto.MetaTaskRequest;
import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.service.UceMetaProcessService;
import com.piplineData.loadService.layer2.service.UceProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller để quản lý Meta Process và Meta Task
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/meta-process")
@RequiredArgsConstructor
public class UceMetaProcessController {

    private final UceMetaProcessService metaProcessService;
    private final UceProcessService processService;

    /**
     * Create Meta Process
     * POST /api/v2/meta-process
     */
    @PostMapping
    public ResponseEntity<UceMetaProcess> createMetaProcess(@RequestBody MetaProcessRequest request) {
        log.info("Creating Meta Process: {}", request.getMetaProcCode());
        UceMetaProcess metaProcess = metaProcessService.createMetaProcess(request);
        return ResponseEntity.ok(metaProcess);
    }

    /**
     * Get all Meta Processes
     * GET /api/v2/meta-process
     */
    @GetMapping
    public ResponseEntity<List<UceMetaProcess>> getAllMetaProcesses() {
        log.info("Getting all Meta Processes");
        List<UceMetaProcess> metaProcesses = metaProcessService.getAllMetaProcesses();
        return ResponseEntity.ok(metaProcesses);
    }

    /**
     * Get Meta Process with Tasks
     * GET /api/v2/meta-process/{code}
     */
    @GetMapping("/{code}")
    public ResponseEntity<MetaProcessWithTasks> getMetaProcessWithTasks(@PathVariable String code) {
        log.info("Getting Meta Process: {}", code);
        MetaProcessWithTasks result = metaProcessService.getMetaProcessWithTasks(code);
        return ResponseEntity.ok(result);
    }

    /**
     * Update Meta Process
     * PUT /api/v2/meta-process/{code}
     */
    @PutMapping("/{code}")
    public ResponseEntity<UceMetaProcess> updateMetaProcess(
            @PathVariable String code,
            @RequestBody MetaProcessRequest request) {
        log.info("Updating Meta Process: {}", code);
        UceMetaProcess metaProcess = metaProcessService.updateMetaProcess(code, request);
        return ResponseEntity.ok(metaProcess);
    }

    /**
     * Delete Meta Process
     * DELETE /api/v2/meta-process/{code}
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteMetaProcess(@PathVariable String code) {
        log.info("Deleting Meta Process: {}", code);
        metaProcessService.deleteMetaProcess(code);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get Meta Tasks of Process
     * GET /api/v2/meta-process/{code}/tasks
     */
    @GetMapping("/{code}/tasks")
    public ResponseEntity<List<UceMetaTask>> getMetaTasksOfProcess(@PathVariable String code) {
        log.info("Getting Meta Tasks for Meta Process: {}", code);
        List<UceMetaTask> tasks = metaProcessService.getMetaTasks(code);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Create Meta Task
     * POST /api/v2/meta-process/{code}/tasks
     */
    @PostMapping("/{code}/tasks")
    public ResponseEntity<UceMetaTask> createMetaTask(
            @PathVariable String code,
            @RequestBody MetaTaskRequest request) {
        log.info("Creating Meta Task for Meta Process: {}", code);
        request.setMetaProcCode(code);
        UceMetaTask metaTask = metaProcessService.createMetaTask(request);
        return ResponseEntity.ok(metaTask);
    }

    /**
     * Validate Meta Process DAG (no cycle)
     * GET /api/v2/meta-process/{code}/validate
     */
    @GetMapping("/{code}/validate")
    public ResponseEntity<Boolean> validateMetaProcess(@PathVariable String code) {
        log.info("Validating Meta Process: {}", code);
        boolean isValid = metaProcessService.validateNoCycle(code);
        return ResponseEntity.ok(isValid);}

    /**
     * Create Process Instance from Meta Process
     * POST /api/v2/meta-process/{code}/create-process
     */
    @PostMapping("/{code}/create-process")
    public ResponseEntity<ProcessCreationResult> createProcessFromMeta(
            @PathVariable String code,
            @RequestBody Map<String, Object> request) {
        log.info("Creating Process Instance from Meta Process: {}", code);

        Long calcProgId = request.get("calcProgId") != null 
            ? Long.valueOf(request.get("calcProgId").toString()) 
            : null;
        Long calcPeriodId = request.get("calcPeriodId") != null 
            ? Long.valueOf(request.get("calcPeriodId").toString()) 
            : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> procParams = (Map<String, Object>) request.getOrDefault("procParams", new HashMap<>());

        ProcessCreationResult result = processService.createProcessFromMeta(
            code, calcProgId, calcPeriodId, procParams);

        return ResponseEntity.ok(result);
    }

}
