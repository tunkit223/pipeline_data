package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.MetaProcessRequest;
import com.piplineData.loadService.layer2.dto.MetaProcessWithTasks;
import com.piplineData.loadService.layer2.dto.MetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.repository.UceMetaProcessRepository;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import com.piplineData.loadService.service.MetadataSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service để quản lý Meta Process và Meta Task
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceMetaProcessService {

    private final UceMetaProcessRepository metaProcessRepository;
    private final UceMetaTaskRepository metaTaskRepository;
    private final MetadataSchemaService metadataSchemaService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Tạo Meta Process mới
     */
    @Transactional
    public UceMetaProcess createMetaProcess(MetaProcessRequest request) {
        log.info("Creating Meta Process: {} in schema: {}", request.getMetaProcCode(), request.getMetadataSchema());

        // 1. REQUIRE metadata schema (không cho phép fallback)
        if (request.getMetadataSchema() == null || request.getMetadataSchema().isBlank()) {
            throw new IllegalArgumentException("metadataSchema is required. Please specify target schema (e.g., 'hr_calc', 'finance_calc')");
        }
        
        String schema = request.getMetadataSchema();
        metadataSchemaService.ensureMetadataSchema(schema);

        // 2. Check for duplicate Meta Process Code
        String checkSQL = String.format(
            "SELECT COUNT(*) FROM %s.uce_meta_process WHERE meta_proc_code = ?", schema);
        Integer count = jdbcTemplate.queryForObject(checkSQL, Integer.class, request.getMetaProcCode());
        if (count != null && count > 0) {
            throw new RuntimeException(String.format(
                "Meta Process '%s' already exists in schema '%s'. Cannot create duplicate.",
                request.getMetaProcCode(), schema));
        }

        // 3. Insert vào dynamic schema using JdbcTemplate
        String insertSQL = String.format("""
            INSERT INTO %s.uce_meta_process 
            (meta_proc_code, meta_proc_name, company_id, brand_id, department_id, meta_proc_note, is_active, metadata_schema, schedule_interval)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, schema);

        jdbcTemplate.update(insertSQL,
            request.getMetaProcCode(),
            request.getMetaProcName(),
            request.getCompanyId(),
            request.getBrandId(),
            request.getDepartmentId(),
            request.getMetaProcNote(),
            request.getIsActive() != null ? request.getIsActive() : true,
            schema,
            request.getScheduleInterval()
        );

        // 4. Return entity
        UceMetaProcess metaProcess = new UceMetaProcess();
        metaProcess.setMetaProcCode(request.getMetaProcCode());
        metaProcess.setMetaProcName(request.getMetaProcName());
        metaProcess.setCompanyId(request.getCompanyId());
        metaProcess.setBrandId(request.getBrandId());
        metaProcess.setDepartmentId(request.getDepartmentId());
        metaProcess.setMetaProcNote(request.getMetaProcNote());
        metaProcess.setIsActive(request.getIsActive());
        metaProcess.setMetadataSchema(schema);
        metaProcess.setScheduleInterval(request.getScheduleInterval());

        log.info("Created Meta Process {} in schema {} with schedule: {}", 
            metaProcess.getMetaProcCode(), schema, request.getScheduleInterval());
        return metaProcess;
    }

    /**
     * Tạo Meta Task mới (DEPRECATED - use UceMetaTaskService.createMetaTaskInSchema instead)
     * @deprecated Use UceMetaTaskService.createMetaTaskInSchema() for dynamic schema support
     */
    @Deprecated
    @Transactional
    public UceMetaTask createMetaTask(MetaTaskRequest request) {
        log.info("Creating Meta Task: {}", request.getMetaTaskCode());

        // Validate Meta Process exists
        metaProcessRepository.findByMetaProcCode(request.getMetaProcCode())
            .orElseThrow(() -> new RuntimeException("Meta Process not found: " + request.getMetaProcCode()));

        // Validate unique name
        if (metaTaskRepository.existsByMetaTaskName(request.getMetaTaskName())) {
            throw new RuntimeException("Meta Task name already exists: " + request.getMetaTaskName());
        }

        UceMetaTask metaTask = new UceMetaTask();
        metaTask.setMetaTaskCode(request.getMetaTaskCode());
        metaTask.setMetaTaskName(request.getMetaTaskName());
        metaTask.setMetaTaskType(request.getMetaTaskType());
        metaTask.setMetaProcCode(request.getMetaProcCode());
        metaTask.setPreMetaTaskCodelist(request.getPreMetaTaskCodelist());
        metaTask.setPostMetaTaskCodelist(request.getPostMetaTaskCodelist());
        metaTask.setSelector(request.getSelector());
        metaTask.setProcessor(request.getProcessor());
        metaTask.setInsertor(request.getInsertor());
        metaTask.setMetaTaskNote(request.getMetaTaskNote());
        metaTask.setIsActive(request.getIsActive());

        // Auto-determine is_starting và is_ending
        metaTask.setIsStarting(request.getPreMetaTaskCodelist() == null || request.getPreMetaTaskCodelist().trim().isEmpty());
        metaTask.setIsEnding(request.getPostMetaTaskCodelist() == null || request.getPostMetaTaskCodelist().trim().isEmpty());

        return metaTaskRepository.save(metaTask);
    }

    /**
     * Get Meta Process với tất cả Tasks
     */
    public MetaProcessWithTasks getMetaProcessWithTasks(String metaProcCode) {
        log.info("Getting Meta Process with tasks: {}", metaProcCode);
        
        // Find schema containing this Meta Process
        String schema = findSchemaForMetaProcess(metaProcCode);
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode);
        }
        
        // Get Meta Process
        UceMetaProcess metaProcess = getMetaProcess(metaProcCode, schema);
        
        // Get Meta Tasks from dynamic schema
        List<UceMetaTask> tasks = getMetaTasksFromSchema(schema, metaProcCode, true);

        return MetaProcessWithTasks.builder()
            .metaProcess(metaProcess)
            .tasks(tasks)
            .totalTasks(tasks.size())
            .startingTasks((int) tasks.stream().filter(UceMetaTask::getIsStarting).count())
            .endingTasks((int) tasks.stream().filter(UceMetaTask::getIsEnding).count())
            .build();
    }
    
    /**
     * Helper: Query Meta Tasks từ dynamic schema
     */
    private List<UceMetaTask> getMetaTasksFromSchema(String schema, String metaProcCode, boolean activeOnly) {
        String sql = String.format("""
            SELECT meta_task_code, meta_task_name, meta_proc_code, task_order, meta_task_type,
                   pre_meta_task_codelist, post_meta_task_codelist, selector, processor, insertor,
                   meta_task_note, is_active, is_starting, is_ending
            FROM %s.uce_meta_task
            WHERE meta_proc_code = ?
            %s
            ORDER BY task_order
            """, schema, activeOnly ? "AND is_active = true" : "");
        
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
     * Get Meta Process by code from dynamic schema
     */
    public UceMetaProcess getMetaProcess(String metaProcCode, String schema) {
        String querySQL = String.format("""
            SELECT meta_proc_code, meta_proc_name, company_id, brand_id, department_id, 
                   meta_proc_note, is_active, metadata_schema
            FROM %s.uce_meta_process
            WHERE meta_proc_code = ?
            """, schema);

        try {
            return jdbcTemplate.queryForObject(querySQL, (rs, rowNum) -> {
                UceMetaProcess mp = new UceMetaProcess();
                mp.setMetaProcCode(rs.getString("meta_proc_code"));
                mp.setMetaProcName(rs.getString("meta_proc_name"));
                mp.setCompanyId((Long) rs.getObject("company_id"));
                mp.setBrandId((Long) rs.getObject("brand_id"));
                mp.setDepartmentId((Long) rs.getObject("department_id"));
                mp.setMetaProcNote(rs.getString("meta_proc_note"));
                mp.setIsActive(rs.getBoolean("is_active"));
                mp.setMetadataSchema(rs.getString("metadata_schema"));
                return mp;
            }, metaProcCode);
        } catch (Exception e) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode + " in schema: " + schema);
        }
    }

    /**
     * Validate DAG không có cycle
     */
    public boolean validateNoCycle(String metaProcCode) {
        log.info("Validating DAG for Meta Process: {}", metaProcCode);
        
        // Find schema
        String schema = findSchemaForMetaProcess(metaProcCode);
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode);
        }
        
        // Get tasks from dynamic schema
        List<UceMetaTask> tasks = getMetaTasksFromSchema(schema, metaProcCode, true);
        
        // Simple cycle detection using DFS
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (UceMetaTask task : tasks) {
            if (hasCycle(task.getMetaTaskCode(), tasks, visited, recursionStack)) {
                log.error("Cycle detected in Meta Process: {}", metaProcCode);
                return false;
            }
        }

        return true;
    }

    private boolean hasCycle(String taskCode, List<UceMetaTask> allTasks, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(taskCode)) {
            return true; // Cycle detected
        }

        if (visited.contains(taskCode)) {
            return false;
        }

        visited.add(taskCode);
        recursionStack.add(taskCode);

        // Find task
        UceMetaTask task = allTasks.stream()
            .filter(t -> t.getMetaTaskCode().equals(taskCode))
            .findFirst()
            .orElse(null);

        if (task != null && task.getPostMetaTaskCodelist() != null) {
            String[] dependencies = task.getPostMetaTaskCodelist().split(",");
            for (String dep : dependencies) {
                if (hasCycle(dep.trim(), allTasks, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(taskCode);
        return false;
    }

    /**
     * List all Meta Processes from ALL schemas
     */
    public List<UceMetaProcess> getAllMetaProcesses() {
        // Tìm tất cả schemas có bảng uce_meta_process
        String findSchemasSQL = """
            SELECT table_schema 
            FROM information_schema.tables 
            WHERE table_name = 'uce_meta_process'
            """;
        
        List<String> schemas = jdbcTemplate.queryForList(findSchemasSQL, String.class);
        List<UceMetaProcess> allProcesses = new java.util.ArrayList<>();
        
        // Query từ tất cả schemas
        for (String schema : schemas) {
            String selectSQL = String.format("""
                SELECT meta_proc_code, meta_proc_name, company_id, brand_id, 
                       department_id, meta_proc_note, is_active, metadata_schema
                FROM %s.uce_meta_process
                """, schema);
            
            List<UceMetaProcess> processes = jdbcTemplate.query(selectSQL, (rs, rowNum) -> {
                UceMetaProcess mp = new UceMetaProcess();
                mp.setMetaProcCode(rs.getString("meta_proc_code"));
                mp.setMetaProcName(rs.getString("meta_proc_name"));
                mp.setCompanyId(rs.getLong("company_id"));
                mp.setBrandId(rs.getLong("brand_id"));
                mp.setDepartmentId(rs.getLong("department_id"));
                mp.setMetaProcNote(rs.getString("meta_proc_note"));
                mp.setIsActive(rs.getBoolean("is_active"));
                mp.setMetadataSchema(rs.getString("metadata_schema"));
                return mp;
            });
            
            allProcesses.addAll(processes);
        }
        
        return allProcesses;
    }

    /**
     * Update Meta Process
     */
    @Transactional
    public UceMetaProcess updateMetaProcess(String metaProcCode, MetaProcessRequest request) {
        log.info("Updating Meta Process: {}", metaProcCode);
        
        // Find schema containing this Meta Process
        String schema = findSchemaForMetaProcess(metaProcCode);
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode);
        }
        
        // Update using JdbcTemplate
        String updateSQL = String.format("""
            UPDATE %s.uce_meta_process
            SET meta_proc_name = ?,
                company_id = ?,
                brand_id = ?,
                department_id = ?,
                meta_proc_note = ?,
                is_active = ?
            WHERE meta_proc_code = ?
            """, schema);
        
        jdbcTemplate.update(updateSQL,
            request.getMetaProcName(),
            request.getCompanyId(),
            request.getBrandId(),
            request.getDepartmentId(),
            request.getMetaProcNote(),
            request.getIsActive() != null ? request.getIsActive() : true,
            metaProcCode
        );
        
        // Return updated entity
        return getMetaProcess(metaProcCode, schema);
    }

    /**
     * Get Meta Tasks of a Process from dynamic schema
     */
    public List<UceMetaTask> getMetaTasks(String metaProcCode) {
        // Tìm schema chứa Meta Process
        String schema = findSchemaForMetaProcess(metaProcCode);
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode);
        }
        
        // Query Meta Tasks từ schema đó (get all tasks, not only active)
        return getMetaTasksFromSchema(schema, metaProcCode, false);
    }

    /**
     * Delete Meta Process from dynamic schema
     */
    @Transactional
    public void deleteMetaProcess(String metaProcCode) {
        // Tìm schema chứa Meta Process
        String schema = findSchemaForMetaProcess(metaProcCode);
        if (schema == null) {
            throw new RuntimeException("Meta Process not found: " + metaProcCode);
        }
        
        // Check if any tasks exist
        String checkTasksSQL = String.format("""
            SELECT COUNT(*) FROM %s.uce_meta_task 
            WHERE meta_proc_code = ?
            """, schema);
        
        Integer taskCount = jdbcTemplate.queryForObject(checkTasksSQL, Integer.class, metaProcCode);
        if (taskCount != null && taskCount > 0) {
            throw new RuntimeException("Cannot delete Meta Process with existing tasks");
        }
        
        // Delete Meta Process
        String deleteSQL = String.format("""
            DELETE FROM %s.uce_meta_process 
            WHERE meta_proc_code = ?
            """, schema);
        
        jdbcTemplate.update(deleteSQL, metaProcCode);
    }
    
    /**
     * Helper: Tìm schema chứa Meta Process
     */
    private String findSchemaForMetaProcess(String metaProcCode) {
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
        
        return null;
    }
}
