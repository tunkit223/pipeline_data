package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import com.piplineData.loadService.layer2.entity.UceProcess;
import com.piplineData.loadService.layer2.entity.UceTask;
import com.piplineData.loadService.layer2.repository.UceMetaTaskRepository;
import com.piplineData.loadService.layer2.repository.UceProcessRepository;
import com.piplineData.loadService.layer2.repository.UceTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service để tạo Process Instance từ Meta Process
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceProcessService {

    private final UceProcessRepository processRepository;
    private final UceTaskRepository taskRepository;
    private final UceMetaTaskRepository metaTaskRepository;
    private final TemplateRenderService templateRenderService;

    /**
     * Tạo Process từ Meta Process
     * Render các SQL template theo business rules (chưa có runtime params)
     */
    @Transactional
    public ProcessCreationResult createProcessFromMeta(
            String metaProcCode,
            Long calcProgId,
            Long calcPeriodId,
            Map<String, Object> businessParams) {

        log.info("Creating Process from Meta Process: {}", metaProcCode);

        // 1. Generate Process Code
        String procCode = generateProcessCode(metaProcCode, calcProgId, calcPeriodId);

        // 2. Create Process
        UceProcess process = new UceProcess();
        process.setProcCode(procCode);
        process.setMetaProcCode(metaProcCode);
        process.setCalcProgId(calcProgId);
        process.setCalcPeriodId(calcPeriodId);
        process.setStatus("READY");
        process.setIsLasted(true);

        // Mark previous processes as not lasted
        List<UceProcess> previousProcesses = processRepository.findByMetaProcCode(metaProcCode);
        previousProcesses.forEach(p -> p.setIsLasted(false));
        processRepository.saveAll(previousProcesses);

        process = processRepository.save(process);
        log.info("Created Process: {}", procCode);

        // 3. Create Tasks từ Meta Tasks
        List<UceMetaTask> metaTasks = metaTaskRepository.findByMetaProcCodeAndIsActive(metaProcCode, true);
        List<String> taskCodes = new ArrayList<>();

        for (UceMetaTask metaTask : metaTasks) {
            UceTask task = createTaskFromMetaTask(process, metaTask, businessParams);
            taskRepository.save(task);
            taskCodes.add(task.getTaskCode());
            log.info("Created Task: {}", task.getTaskCode());
        }

        return ProcessCreationResult.builder()
            .procCode(procCode)
            .metaProcCode(metaProcCode)
            .procId(process.getProcId())
            .status(process.getStatus())
            .taskCodes(taskCodes)
            .totalTasks(taskCodes.size())
            .message("Process created successfully with " + taskCodes.size() + " tasks")
            .build();
    }

    /**
     * Tạo Task từ Meta Task
     * Render SQL với business params (prog_spec, period_spec, etc.)
     */
    private UceTask createTaskFromMetaTask(
            UceProcess process,
            UceMetaTask metaTask,
            Map<String, Object> businessParams) {

        UceTask task = new UceTask();
        task.setTaskCode(generateTaskCode(process.getProcCode(), metaTask.getMetaTaskCode()));
        task.setProcCode(process.getProcCode());
        task.setMetaProcCode(process.getMetaProcCode());
        task.setCalcProgId(process.getCalcProgId());
        task.setCalcPeriodId(process.getCalcPeriodId());
        task.setStatus("READY");

        // Render SQL templates với business params (rule nghiệp vụ)
        // Kết quả là SQL vẫn còn {{params}} cho runtime (rule điều khiển)
        if (metaTask.getSelector() != null) {
            task.setSelectorBiz(templateRenderService.render(metaTask.getSelector(), businessParams));
        }

        if (metaTask.getProcessor() != null) {
            task.setProcessorBiz(templateRenderService.render(metaTask.getProcessor(), businessParams));
        }

        if (metaTask.getInsertor() != null) {
            task.setInsertorBiz(templateRenderService.render(metaTask.getInsertor(), businessParams));
        }

        return task;
    }

    /**
     * Generate Process Code
     * Format: {META_PROC_CODE}_{PROG_ID}_{PERIOD_ID}_{SEQUENCE}
     */
    private String generateProcessCode(String metaProcCode, Long calcProgId, Long calcPeriodId) {
        Long sequence = processRepository.findAll().stream()
            .filter(p -> p.getMetaProcCode().equals(metaProcCode))
            .count() + 1;

        return String.format("%s_P%d_PER%d_%03d", metaProcCode, 
            calcProgId != null ? calcProgId : 0,
            calcPeriodId != null ? calcPeriodId : 0,
            sequence);
    }

    /**
     * Generate Task Code
     * Format: {PROC_CODE}_{META_TASK_CODE}
     */
    private String generateTaskCode(String procCode, String metaTaskCode) {
        return procCode + "_" + metaTaskCode;
    }

    /**
     * Get Process by code
     */
    public UceProcess getProcess(String procCode) {
        return processRepository.findByProcCode(procCode)
            .orElseThrow(() -> new RuntimeException("Process not found: " + procCode));
    }

    /**
     * Get all Processes by Meta Process
     */
    public List<UceProcess> getProcessesByMetaProc(String metaProcCode) {
        return processRepository.findByMetaProcCode(metaProcCode);
    }

    /**
     * Update Process status
     */
    @Transactional
    public void updateProcessStatus(String procCode, String status) {
        UceProcess process = getProcess(procCode);
        process.setStatus(status);
        processRepository.save(process);
        log.info("Updated Process {} status to {}", procCode, status);
    }
}
