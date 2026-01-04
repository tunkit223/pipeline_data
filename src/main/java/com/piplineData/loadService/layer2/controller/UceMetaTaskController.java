package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.UceMetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.service.UceMetaTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Meta Task Management
 * Base Path: /api/v2/meta-task
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/meta-task")
@RequiredArgsConstructor
public class UceMetaTaskController {

    private final UceMetaTaskService metaTaskService;

    /**
     * Create new Meta Task
     * POST /api/v2/meta-task
     */
    @PostMapping
    public ResponseEntity<UceMetaTask> createMetaTask(@RequestBody UceMetaTaskRequest request) {
        log.info("Creating Meta Task: {}", request.getMetaTaskCode());
        UceMetaTask created = metaTaskService.createMetaTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get Meta Task by code
     * GET /api/v2/meta-task/{metaTaskCode}
     */
    @GetMapping("/{metaTaskCode}")
    public ResponseEntity<UceMetaTask> getMetaTask(@PathVariable String metaTaskCode) {
        log.info("Getting Meta Task: {}", metaTaskCode);
        UceMetaTask metaTask = metaTaskService.getByCode(metaTaskCode);
        return ResponseEntity.ok(metaTask);
    }

    /**
     * Get all Meta Tasks of a Meta Process
     * GET /api/v2/meta-task/process/{metaProcCode}
     */
    @GetMapping("/process/{metaProcCode}")
    public ResponseEntity<List<UceMetaTask>> getMetaTasksByProcess(@PathVariable String metaProcCode) {
        log.info("Getting Meta Tasks for process: {}", metaProcCode);
        List<UceMetaTask> tasks = metaTaskService.getByMetaProcess(metaProcCode);
        return ResponseEntity.ok(tasks);
    }

    /**
     * Update Meta Task
     * PUT /api/v2/meta-task/{metaTaskCode}
     */
    @PutMapping("/{metaTaskCode}")
    public ResponseEntity<UceMetaTask> updateMetaTask(
            @PathVariable String metaTaskCode,
            @RequestBody UceMetaTaskRequest request) {
        log.info("Updating Meta Task: {}", metaTaskCode);
        UceMetaTask updated = metaTaskService.updateMetaTask(metaTaskCode, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete Meta Task
     * DELETE /api/v2/meta-task/{metaTaskCode}
     */
    @DeleteMapping("/{metaTaskCode}")
    public ResponseEntity<Void> deleteMetaTask(@PathVariable String metaTaskCode) {
        log.info("Deleting Meta Task: {}", metaTaskCode);
        metaTaskService.deleteMetaTask(metaTaskCode);
        return ResponseEntity.noContent().build();
    }
}
