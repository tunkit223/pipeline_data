package com.piplineData.loadService.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.dto.BatchSpec;
import com.piplineData.loadService.exception.DataSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSpecParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse BatchSpec từ JSON string
     */
    public BatchSpec parseBatchSpec(String batchSpecJson) {
        try {
            return objectMapper.readValue(batchSpecJson, BatchSpec.class);
        } catch (Exception e) {
            log.error("Failed to parse BatchSpec: {}", e.getMessage());
            throw new DataSyncException("Invalid BatchSpec JSON format", e);
        }
    }

    /**
     * Render SQL với parameters
     * Thay thế ${param_name} bằng giá trị thực
     */
    public String renderSql(String sqlTemplate, Map<String, Object> parameters) {
        String renderedSql = sqlTemplate;

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            renderedSql = renderedSql.replace(placeholder, value);
        }

        log.debug("Rendered SQL: {}", renderedSql);
        return renderedSql;
    }

    /**
     * Validate BatchSpec
     */
    public void validateBatchSpec(BatchSpec batchSpec) {
        if (batchSpec == null) {
            throw new DataSyncException("BatchSpec cannot be null");
        }

        if (batchSpec.getScript() == null || batchSpec.getScript().isEmpty()) {
            throw new DataSyncException("BatchSpec must contain at least one script configuration");
        }

        BatchSpec.ScriptConfig scriptConfig = batchSpec.getScript().get(0);

        if (scriptConfig.getSource() == null || scriptConfig.getSource().getBatchscript() == null) {
            throw new DataSyncException("Source batch script is required");
        }

        if (scriptConfig.getMapfields() == null || scriptConfig.getMapfields().isEmpty()) {
            throw new DataSyncException("Map fields configuration is required");
        }

        if (scriptConfig.getDestination() == null) {
            throw new DataSyncException("Destination configuration is required");
        }

        log.info("BatchSpec validation passed for: {}", batchSpec.getDataObjCode());
    }
}
