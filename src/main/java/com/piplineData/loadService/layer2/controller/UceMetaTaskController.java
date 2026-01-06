package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.MetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import com.piplineData.loadService.layer2.service.UceMetaTaskService;
import com.piplineData.loadService.layer2.service.UceMetaProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private final UceMetaProcessService metaProcessService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Create new Meta Task
     * POST /api/v2/meta-task
     */
    @PostMapping
    public ResponseEntity<UceMetaTask> createMetaTask(@RequestBody MetaTaskRequest request) {
        log.info("Creating Meta Task: {} for process: {}", request.getMetaTaskCode(), request.getMetaProcCode());
        
        // Find schema from Meta Process
        String schema = findMetaProcessSchema(request.getMetaProcCode());
        
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + request.getMetaProcCode());
        }
        
        log.info("Found Meta Process {} in schema: {}", request.getMetaProcCode(), schema);
        
        // Create task in dynamic schema
        UceMetaTask created = metaTaskService.createMetaTaskInSchema(request, schema);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Helper: Find schema containing Meta Process
     */
    private String findMetaProcessSchema(String metaProcCode) {
        try {
            // Query all schemas with uce_meta_process table
            String findSchemasSQL = 
                "SELECT table_schema " +
                "FROM information_schema.tables " +
                "WHERE table_name = 'uce_meta_process'";
            
            List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
            
            // Search in all schemas equally (no priority)
            for (String schema : schemas) {
                String checkSQL = String.format(
                    "SELECT metadata_schema FROM %s.uce_meta_process WHERE meta_proc_code = ?", 
                    schema
                );
                try {
                    String foundSchema = jdbcTemplate.queryForObject(checkSQL, String.class, metaProcCode);
                    return foundSchema;
                } catch (Exception e) {
                    // Not found in this schema, try next
                    continue;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error finding Meta Process schema for {}: {}", metaProcCode, e.getMessage(), e);
            throw new RuntimeException("Failed to find Meta Process: " + metaProcCode, e);
        }
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
            @RequestBody MetaTaskRequest request) {
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
