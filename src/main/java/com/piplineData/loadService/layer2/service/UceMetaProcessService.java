package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.MetaProcessRequest;
import com.piplineData.loadService.layer2.dto.MetaProcessWithTasks;
import com.piplineData.loadService.layer2.dto.MetaTaskRequest;
import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.repository.UceMetaProcessRepository;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Tạo Meta Process mới
     */
    @Transactional
    public UceMetaProcess createMetaProcess(MetaProcessRequest request) {
        log.info("Creating Meta Process: {}", request.getMetaProcCode());

        // Validate
        if (metaProcessRepository.existsByMetaProcName(request.getMetaProcName())) {
            throw new RuntimeException("Meta Process name already exists: " + request.getMetaProcName());
        }

        UceMetaProcess metaProcess = new UceMetaProcess();
        metaProcess.setMetaProcCode(request.getMetaProcCode());
        metaProcess.setMetaProcName(request.getMetaProcName());
        metaProcess.setCompanyId(request.getCompanyId());
        metaProcess.setBrandId(request.getBrandId());
        metaProcess.setDepartmentId(request.getDepartmentId());
        metaProcess.setMetaProcNote(request.getMetaProcNote());
        metaProcess.setIsActive(request.getIsActive());

        return metaProcessRepository.save(metaProcess);
    }

    /**
     * Tạo Meta Task mới
     */
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
        UceMetaProcess metaProcess = metaProcessRepository.findByMetaProcCode(metaProcCode)
            .orElseThrow(() -> new RuntimeException("Meta Process not found: " + metaProcCode));

        List<UceMetaTask> tasks = metaTaskRepository.findByMetaProcCodeAndIsActive(metaProcCode, true);

        return MetaProcessWithTasks.builder()
            .metaProcess(metaProcess)
            .tasks(tasks)
            .totalTasks(tasks.size())
            .startingTasks((int) tasks.stream().filter(UceMetaTask::getIsStarting).count())
            .endingTasks((int) tasks.stream().filter(UceMetaTask::getIsEnding).count())
            .build();
    }

    /**
     * Validate DAG không có cycle
     */
    public boolean validateNoCycle(String metaProcCode) {
        List<UceMetaTask> tasks = metaTaskRepository.findByMetaProcCodeAndIsActive(metaProcCode, true);
        
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
     * List all Meta Processes
     */
    public List<UceMetaProcess> getAllMetaProcesses() {
        return metaProcessRepository.findAll();
    }

    /**
     * Update Meta Process
     */
    @Transactional
    public UceMetaProcess updateMetaProcess(String metaProcCode, MetaProcessRequest request) {
        UceMetaProcess metaProcess = metaProcessRepository.findByMetaProcCode(metaProcCode)
            .orElseThrow(() -> new RuntimeException("Meta Process not found: " + metaProcCode));

        metaProcess.setMetaProcName(request.getMetaProcName());
        metaProcess.setCompanyId(request.getCompanyId());
        metaProcess.setBrandId(request.getBrandId());
        metaProcess.setDepartmentId(request.getDepartmentId());
        metaProcess.setMetaProcNote(request.getMetaProcNote());
        metaProcess.setIsActive(request.getIsActive());

        return metaProcessRepository.save(metaProcess);
    }

    /**
     * Get Meta Tasks of a Process (ordered by taskOrder)
     */
    public List<UceMetaTask> getMetaTasks(String metaProcCode) {
        return metaTaskRepository.findByMetaProcCodeOrderByTaskOrder(metaProcCode);
    }

    /**
     * Delete Meta Process
     */
    @Transactional
    public void deleteMetaProcess(String metaProcCode) {
        // Check if any tasks exist
        List<UceMetaTask> tasks = metaTaskRepository.findByMetaProcCode(metaProcCode);
        if (!tasks.isEmpty()) {
            throw new RuntimeException("Cannot delete Meta Process with existing tasks");
        }

        metaProcessRepository.deleteById(metaProcCode);
    }
}
