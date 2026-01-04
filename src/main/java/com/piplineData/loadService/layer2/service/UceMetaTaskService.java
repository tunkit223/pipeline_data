package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.UceMetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Create new Meta Task
     */
    @Transactional
    public UceMetaTask createMetaTask(UceMetaTaskRequest request) {
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
        
        // Convert SQL template and params to JSON string
        try {
            if (request.getSqlTemplate() != null) {
                metaTask.setSelector(request.getSqlTemplate());
            }
            if (request.getParamsTemplate() != null) {
                String paramsJson = objectMapper.writeValueAsString(request.getParamsTemplate());
                metaTask.setMetaTaskNote(paramsJson);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert params to JSON", e);
        }

        return metaTaskRepository.save(metaTask);
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
    public UceMetaTask updateMetaTask(String metaTaskCode, UceMetaTaskRequest request) {
        log.info("Updating Meta Task: {}", metaTaskCode);

        UceMetaTask existing = getByCode(metaTaskCode);

        existing.setMetaTaskName(request.getMetaTaskName());
        existing.setTaskOrder(request.getTaskOrder());
        existing.setMetaTaskType(request.getTaskType());

        try {
            if (request.getSqlTemplate() != null) {
                existing.setSelector(request.getSqlTemplate());
            }
            if (request.getParamsTemplate() != null) {
                String paramsJson = objectMapper.writeValueAsString(request.getParamsTemplate());
                existing.setMetaTaskNote(paramsJson);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert params to JSON", e);
        }

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
