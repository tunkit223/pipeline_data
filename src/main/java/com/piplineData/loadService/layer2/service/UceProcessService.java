package com.piplineData.loadService.layer2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final TemplateRenderService templateRenderService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Helper: Tìm schema chứa Meta Process
     */
    private String findMetaProcessSchema(String metaProcCode) {
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
        
        throw new RuntimeException("Meta Process not found: " + metaProcCode);
    }
    
    /**
     * Query Meta Tasks từ dynamic schema
     */
    private List<UceMetaTask> queryMetaTasksFromSchema(String schema, String metaProcCode) {
        String sql = String.format("""
            SELECT meta_task_code, meta_task_name, meta_proc_code, task_order, 
                   meta_task_type, pre_meta_task_codelist, post_meta_task_codelist,
                   selector, processor, insertor, meta_task_note, is_active,
                   is_starting, is_ending
            FROM %s.uce_meta_task
            WHERE meta_proc_code = ? AND is_active = true
            ORDER BY task_order
            """, schema);
        
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
        
        // 0. Tìm schema chứa Meta Process
        String schema = findMetaProcessSchema(metaProcCode);
        log.info("Found Meta Process {} in schema: {}", metaProcCode, schema);

        // 1. Generate Process Code
        String procCode = generateProcessCode(metaProcCode, calcProgId, calcPeriodId, schema);

        // 2. Check for duplicate Process Code
        String checkSQL = String.format(
            "SELECT COUNT(*) FROM %s.uce_process WHERE proc_code = ?", schema);
        Integer count = jdbcTemplate.queryForObject(checkSQL, Integer.class, procCode);
        if (count != null && count > 0) {
            throw new RuntimeException(String.format(
                "Process '%s' already exists in schema '%s'. Cannot create duplicate.",
                procCode, schema));
        }

        // 3. Mark previous processes as not lasted (in dynamic schema)
        String updatePreviousSQL = String.format(
            "UPDATE %s.uce_process SET is_lasted = false WHERE meta_proc_code = ?",
            schema
        );
        jdbcTemplate.update(updatePreviousSQL, metaProcCode);

        // 4. Create Process in dynamic schema using JdbcTemplate
        // Convert businessParams to JSON string
        String runtimeParamsJson = null;
        try {
            if (businessParams != null && !businessParams.isEmpty()) {
                runtimeParamsJson = objectMapper.writeValueAsString(businessParams);
            }
        } catch (Exception e) {
            log.warn("Could not serialize businessParams: {}", e.getMessage());
        }
        
        String insertProcessSQL = String.format("""
            INSERT INTO %s.uce_process 
            (proc_code, process_instance_code, meta_proc_code, calc_prog_id, calc_period_id, runtime_params, status, is_lasted)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            RETURNING proc_id
            """, schema);
        
        Long procId = jdbcTemplate.queryForObject(insertProcessSQL, Long.class,
            procCode,
            procCode,  // Use proc_code as process_instance_code
            metaProcCode,
            calcProgId,
            calcPeriodId,
            runtimeParamsJson,
            "READY",
            true
        );
        
        log.info("Created Process: {} with ID {} in schema {}", procCode, procId, schema);

        // 4. Query Meta Tasks từ dynamic schema
        List<UceMetaTask> metaTasks = queryMetaTasksFromSchema(schema, metaProcCode);
        log.info("Found {} Meta Tasks in schema {}", metaTasks.size(), schema);
        List<String> taskCodes = new ArrayList<>();

        // 5. Create Tasks in dynamic schema using JdbcTemplate
        String insertTaskSQL = String.format("""
            INSERT INTO %s.uce_task 
            (task_code, proc_code, meta_task_code, meta_proc_code, calc_prog_id, calc_period_id, 
             selector_biz, processor_biz, insertor_biz, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, schema);
        
        for (UceMetaTask metaTask : metaTasks) {
            String taskCode = generateTaskCode(procCode, metaTask.getMetaTaskCode());
            
            // KHÔNG render template khi tạo task - sẽ render khi execute với runtime params
            // Lưu template gốc từ meta task để có thể render với params khác nhau mỗi lần execute
            String selectorBiz = metaTask.getSelector();
            String processorBiz = metaTask.getProcessor();
            String insertorBiz = metaTask.getInsertor();
            
            jdbcTemplate.update(insertTaskSQL,
                taskCode,
                procCode,
                metaTask.getMetaTaskCode(),
                metaProcCode,
                calcProgId,
                calcPeriodId,
                selectorBiz,
                processorBiz,
                insertorBiz,
                "READY"
            );
            
            taskCodes.add(taskCode);
            log.info("Created Task: {} in schema {}", taskCode, schema);
        }

        return ProcessCreationResult.builder()
            .procCode(procCode)
            .metaProcCode(metaProcCode)
            .procId(procId)
            .status("READY")
            .taskCodes(taskCodes)
            .totalTasks(taskCodes.size())
            .message("Process created successfully with " + taskCodes.size() + " tasks in schema " + schema)
            .build();
    }

    /**
     * Generate Process Code
     * Format: {META_PROC_CODE}_{PROG_ID}_{PERIOD_ID}_{SEQUENCE}
     */
    private String generateProcessCode(String metaProcCode, Long calcProgId, Long calcPeriodId, String schema) {
        String countSQL = String.format(
            "SELECT COUNT(*) FROM %s.uce_process WHERE meta_proc_code = ?",
            schema
        );
        Integer count = jdbcTemplate.queryForObject(countSQL, Integer.class, metaProcCode);
        Long sequence = (count != null ? count : 0) + 1L;

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
}
