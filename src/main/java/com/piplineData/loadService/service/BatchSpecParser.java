package com.piplineData.loadService.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.dto.BatchSpec;
import com.piplineData.loadService.dto.MapField;
import com.piplineData.loadService.exception.DataSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSpecParser {

    private final ObjectMapper objectMapper;

    // Pattern để validate SQL (cơ bản, tránh SQL injection)
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            ".*(DROP|TRUNCATE|ALTER|GRANT|REVOKE|EXEC|EXECUTE).*",
            Pattern.CASE_INSENSITIVE
    );

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
     * Thay thế ${param_name} hoặc {{param_name}} bằng giá trị thực
     */
    public String renderSql(String sqlTemplate, Map<String, Object> parameters) {
        String renderedSql = sqlTemplate;

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() != null ? entry.getValue().toString() : "";

            // Support cả ${} và {{}}
            String placeholder1 = "${" + key + "}";
            String placeholder2 = "{{" + key + "}}";

            renderedSql = renderedSql.replace(placeholder1, value);
            renderedSql = renderedSql.replace(placeholder2, value);
        }

        // Validate SQL trước khi return
        validateSql(renderedSql);

        log.debug("Rendered SQL: {}", renderedSql);
        return renderedSql;
    }

    /**
     * Build SELECT statement với SQL transform
     */
    public String buildSelectWithTransform(
            List<MapField> mapFields,
            String baseSelectSql) {

        // Nếu không có transform, return base SQL
        boolean hasTransform = mapFields.stream()
                .anyMatch(mf -> mf.getSqlTransform() != null && !mf.getSqlTransform().isEmpty());

        if (!hasTransform) {
            return baseSelectSql;
        }

        // Build SELECT clause với transforms
        StringBuilder selectBuilder = new StringBuilder("SELECT ");

        for (int i = 0; i < mapFields.size(); i++) {
            MapField field = mapFields.get(i);

            if (field.getSqlTransform() != null && !field.getSqlTransform().isEmpty()) {
                // Sử dụng SQL transform
                selectBuilder.append(field.getSqlTransform())
                        .append(" AS ")
                        .append(field.getTo());
            } else {
                // Mapping trực tiếp
                selectBuilder.append(field.getFrom())
                        .append(" AS ")
                        .append(field.getTo());
            }

            if (i < mapFields.size() - 1) {
                selectBuilder.append(", ");
            }
        }

        // Thêm các cột không được map (sẽ vào extra_data)
        selectBuilder.append(", * ");

        // Extract FROM clause từ base SQL
        int fromIndex = baseSelectSql.toUpperCase().indexOf("FROM");
        if (fromIndex > 0) {
            String fromClause = baseSelectSql.substring(fromIndex);
            selectBuilder.append(" ").append(fromClause);
        }

        return selectBuilder.toString();
    }

    /**
     * Validate SQL để tránh SQL injection
     */
    public void validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DataSyncException("SQL cannot be empty");
        }

        // Kiểm tra dangerous keywords
        if (DANGEROUS_SQL_PATTERN.matcher(sql).matches()) {
            throw new DataSyncException(
                    "SQL contains dangerous keywords (DROP, TRUNCATE, ALTER, etc.)"
            );
        }

        // Kiểm tra SQL phải bắt đầu bằng SELECT (cho source query)
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT") && !trimmedSql.startsWith("WITH")) {
            log.warn("SQL does not start with SELECT or WITH: {}", sql);
        }
    }

    /**
     * Validate BatchSpec
     * Hỗ trợ 3 cấu trúc: custom SQL, simplified, và legacy
     */
    public void validateBatchSpec(BatchSpec batchSpec) {
        if (batchSpec == null) {
            throw new DataSyncException("BatchSpec cannot be null");
        }

        // Check for custom SQL structure
        if (batchSpec.getCustomSourceSql() != null && !batchSpec.getCustomSourceSql().trim().isEmpty()) {
            validateCustomSqlBatchSpec(batchSpec);
            return;
        }

        // Check for simplified structure
        if (batchSpec.getSourceTable() != null && batchSpec.getDestTable() != null) {
            validateSimplifiedBatchSpec(batchSpec);
            return;
        }

        // Check for legacy structure
        if (batchSpec.getScript() == null || batchSpec.getScript().isEmpty()) {
            throw new DataSyncException("BatchSpec must contain either custom SQL (customSourceSql), simplified structure (sourceTable, destTable, fields), or legacy structure (script)");
        }

        validateLegacyBatchSpec(batchSpec);
    }

    /**
     * Validate simplified BatchSpec structure
     */
    private void validateSimplifiedBatchSpec(BatchSpec batchSpec) {
        if (batchSpec.getSourceTable() == null || batchSpec.getSourceTable().trim().isEmpty()) {
            throw new DataSyncException("sourceTable is required");
        }

        if (batchSpec.getDestTable() == null || batchSpec.getDestTable().trim().isEmpty()) {
            throw new DataSyncException("destTable is required");
        }

        if (batchSpec.getPrimaryKeys() == null || batchSpec.getPrimaryKeys().isEmpty()) {
            throw new DataSyncException("primaryKeys is required and must contain at least one key");
        }

        if (batchSpec.getFields() == null || batchSpec.getFields().isEmpty()) {
            throw new DataSyncException("fields is required and must contain at least one field mapping");
        }

        // Validate fields
        for (MapField field : batchSpec.getFields()) {
            if (field.getSourceField() == null || field.getSourceField().trim().isEmpty()) {
                throw new DataSyncException("sourceField is required in field mapping");
            }
            if (field.getDestField() == null || field.getDestField().trim().isEmpty()) {
                throw new DataSyncException("destField is required in field mapping");
            }
        }

        log.info("Simplified BatchSpec validation passed");
    }

    /**
     * Validate custom SQL BatchSpec structure
     */
    private void validateCustomSqlBatchSpec(BatchSpec batchSpec) {
        if (batchSpec.getCustomSourceSql() == null || batchSpec.getCustomSourceSql().trim().isEmpty()) {
            throw new DataSyncException("customSourceSql is required for custom SQL mode");
        }

        if (batchSpec.getDestTable() == null || batchSpec.getDestTable().trim().isEmpty()) {
            throw new DataSyncException("destTable is required");
        }

        if (batchSpec.getPrimaryKeys() == null || batchSpec.getPrimaryKeys().isEmpty()) {
            throw new DataSyncException("primaryKeys is required and must contain at least one key");
        }

        if (batchSpec.getFields() == null || batchSpec.getFields().isEmpty()) {
            throw new DataSyncException("fields is required and must contain at least one field mapping");
        }

        // Validate custom SQL không chứa dangerous commands
        validateSql(batchSpec.getCustomSourceSql());

        if (batchSpec.getCustomDestSql() != null && !batchSpec.getCustomDestSql().trim().isEmpty()) {
            validateSql(batchSpec.getCustomDestSql());
        }

        // Validate fields
        for (MapField field : batchSpec.getFields()) {
            if (field.getSourceField() == null || field.getSourceField().trim().isEmpty()) {
                throw new DataSyncException("sourceField is required in field mapping");
            }
            if (field.getDestField() == null || field.getDestField().trim().isEmpty()) {
                throw new DataSyncException("destField is required in field mapping");
            }
        }

        log.info("Custom SQL BatchSpec validation passed");
    }

    /**
     * Validate legacy BatchSpec structure
     */
    private void validateLegacyBatchSpec(BatchSpec batchSpec) {
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

        // Validate SQL trong source và destination
        validateSql(scriptConfig.getSource().getBatchscript());

        if (scriptConfig.getDestination().getBatchscript() != null) {
            validateSql(scriptConfig.getDestination().getBatchscript());
        }

        log.info("Legacy BatchSpec validation passed for: {}", batchSpec.getDataObjCode());
    }
}
