package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.MetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for Meta Task Management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceMetaTaskService {

    private final UceMetaTaskRepository metaTaskRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Create new Meta Task (DEPRECATED - use createMetaTaskInSchema instead)
     * @deprecated Use createMetaTaskInSchema() for dynamic schema support
     */
    @Deprecated
    @Transactional
    public UceMetaTask createMetaTask(MetaTaskRequest request) {
        log.info("Creating Meta Task: {}", request.getMetaTaskCode());

        // Check if task code already exists
        if (metaTaskRepository.existsById(request.getMetaTaskCode())) {
            throw new RuntimeException("Meta Task code already exists: " + request.getMetaTaskCode());
        }

        UceMetaTask metaTask = new UceMetaTask();
        metaTask.setMetaTaskCode(request.getMetaTaskCode());
        metaTask.setMetaTaskName(request.getMetaTaskName());
        metaTask.setMetaProcCode(request.getMetaProcCode());
        metaTask.setTaskOrder(request.getTaskOrder());
        metaTask.setMetaTaskType(request.getTaskType());
        metaTask.setSelector(request.getSqlTemplate());
        metaTask.setMetaTaskNote(request.getMetaTaskNote());
        metaTask.setPreMetaTaskCodelist(request.getPreMetaTaskCodelist());
        metaTask.setPostMetaTaskCodelist(request.getPostMetaTaskCodelist());
        
        // Auto-determine is_starting và is_ending
        metaTask.setIsStarting(request.getPreMetaTaskCodelist() == null || request.getPreMetaTaskCodelist().trim().isEmpty());
        metaTask.setIsEnding(request.getPostMetaTaskCodelist() == null || request.getPostMetaTaskCodelist().trim().isEmpty());
        metaTask.setIsActive(true);

        return metaTaskRepository.save(metaTask);
    }

    /**
     * Create Meta Task in dynamic schema
     */
    @Transactional
    public UceMetaTask createMetaTaskInSchema(MetaTaskRequest request, String schema) {
        log.info("Creating Meta Task: {} in schema: {}", request.getMetaTaskCode(), schema);

        // Check for duplicate Meta Task Code
        String checkSQL = String.format(
            "SELECT COUNT(*) FROM %s.uce_meta_task WHERE meta_task_code = ?", schema);
        Integer count = jdbcTemplate.queryForObject(checkSQL, Integer.class, request.getMetaTaskCode());
        if (count != null && count > 0) {
            throw new RuntimeException(String.format(
                "Meta Task '%s' already exists in schema '%s'. Cannot create duplicate.",
                request.getMetaTaskCode(), schema));
        }

        String insertSQL = String.format("""
            INSERT INTO %s.uce_meta_task 
            (meta_task_code, meta_task_name, meta_proc_code, task_order, meta_task_type,
             pre_meta_task_codelist, post_meta_task_codelist, selector, processor, insertor, 
             meta_task_note, is_active, is_starting, is_ending)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, schema);

        // Auto-determine is_starting và is_ending
        boolean isStarting = request.getPreMetaTaskCodelist() == null || request.getPreMetaTaskCodelist().trim().isEmpty();
        boolean isEnding = request.getPostMetaTaskCodelist() == null || request.getPostMetaTaskCodelist().trim().isEmpty();

        jdbcTemplate.update(insertSQL,
            request.getMetaTaskCode(),
            request.getMetaTaskName(),
            request.getMetaProcCode(),
            request.getTaskOrder(),
            request.getTaskType(),
            request.getPreMetaTaskCodelist(),
            request.getPostMetaTaskCodelist(),
            request.getSqlTemplate(),
            null, // processor
            null, // insertor
            request.getMetaTaskNote(),
            true, // is_active
            isStarting,
            isEnding
        );

        // Return entity
        UceMetaTask metaTask = new UceMetaTask();
        metaTask.setMetaTaskCode(request.getMetaTaskCode());
        metaTask.setMetaTaskName(request.getMetaTaskName());
        metaTask.setMetaProcCode(request.getMetaProcCode());
        metaTask.setTaskOrder(request.getTaskOrder());
        metaTask.setMetaTaskType(request.getTaskType());
        metaTask.setPreMetaTaskCodelist(request.getPreMetaTaskCodelist());
        metaTask.setPostMetaTaskCodelist(request.getPostMetaTaskCodelist());
        metaTask.setSelector(request.getSqlTemplate());
        metaTask.setMetaTaskNote(request.getMetaTaskNote());
        metaTask.setIsActive(true);
        metaTask.setIsStarting(isStarting);
        metaTask.setIsEnding(isEnding);

        log.info("Created Meta Task {} in schema {}", metaTask.getMetaTaskCode(), schema);
        return metaTask;
    }

    /**
     * Get Meta Task by code
     */
    public UceMetaTask getByCode(String metaTaskCode) {
        return metaTaskRepository.findById(metaTaskCode)
                .orElseThrow(() -> new RuntimeException("Meta Task not found: " + metaTaskCode));
    }

    /**
     * Get all Meta Tasks of a Meta Process
     */
    public List<UceMetaTask> getByMetaProcess(String metaProcCode) {
        return metaTaskRepository.findByMetaProcCodeOrderByTaskOrder(metaProcCode);
    }

    /**
     * Update Meta Task
     */
    @Transactional
    public UceMetaTask updateMetaTask(String metaTaskCode, MetaTaskRequest request) {
        log.info("Updating Meta Task: {}", metaTaskCode);

        UceMetaTask existing = getByCode(metaTaskCode);

        existing.setMetaTaskName(request.getMetaTaskName());
        existing.setTaskOrder(request.getTaskOrder());
        existing.setMetaTaskType(request.getTaskType());
        existing.setSelector(request.getSqlTemplate());
        existing.setMetaTaskNote(request.getMetaTaskNote());
        existing.setPreMetaTaskCodelist(request.getPreMetaTaskCodelist());
        existing.setPostMetaTaskCodelist(request.getPostMetaTaskCodelist());
        
        // Auto-determine is_starting và is_ending
        existing.setIsStarting(request.getPreMetaTaskCodelist() == null || request.getPreMetaTaskCodelist().trim().isEmpty());
        existing.setIsEnding(request.getPostMetaTaskCodelist() == null || request.getPostMetaTaskCodelist().trim().isEmpty());

        return metaTaskRepository.save(existing);
    }

    /**
     * Delete Meta Task
     */
    @Transactional
    public void deleteMetaTask(String metaTaskCode) {
        log.info("Deleting Meta Task: {}", metaTaskCode);
        if (!metaTaskRepository.existsById(metaTaskCode)) {
            throw new RuntimeException("Meta Task not found: " + metaTaskCode);
        }
        metaTaskRepository.deleteById(metaTaskCode);
    }
}
