package com.piplineData.loadService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.dto.BatchSpec;
import com.piplineData.loadService.dto.MapField;
import com.piplineData.loadService.dto.SyncResult;
import com.piplineData.loadService.entity.DataObject;
import com.piplineData.loadService.entity.DatabaseSourceConfig;
import com.piplineData.loadService.entity.SyncLog;
import com.piplineData.loadService.exception.DataSyncException;
import com.piplineData.loadService.repository.DataObjectRepository;
import com.piplineData.loadService.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service xử lý sync với simplified BatchSpec structure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimplifiedDataSyncService {

    private final DataObjectRepository dataObjectRepository;
    private final SyncLogRepository syncLogRepository;
    private final BatchSpecParser batchSpecParser;
    private final DatabaseSourceConfigService databaseSourceConfigService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SyncResult syncData(String dataObjCode, Map<String, Object> runtimeParams) {
        
        log.info("Starting simplified sync for: {}", dataObjCode);
        
        // Create sync log
        SyncLog syncLog = new SyncLog();
        syncLog.setDataObjCode(dataObjCode);
        syncLog.setSyncType("BATCH");
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(LocalDateTime.now());
        syncLog.setRecordsFetched(0);
        syncLog.setRecordsInserted(0);
        syncLog.setRecordsUpdated(0);
        syncLog.setRecordsDeleted(0);
        syncLog.setRetryCount(0);
        syncLog = syncLogRepository.save(syncLog);

        try {
            // 1. Load DataObject
            DataObject dataObject = dataObjectRepository.findByDataObjCodeAndIsActiveTrue(dataObjCode)
                    .orElseThrow(() -> new DataSyncException("DataObject not found: " + dataObjCode));

            // 2. Parse BatchSpec
            BatchSpec batchSpec = batchSpecParser.parseBatchSpec(dataObject.getBatchSpec());
            batchSpecParser.validateBatchSpec(batchSpec);
            
            // Convert runtimeParams to String map for SQL replacement
            Map<String, String> runtimeParamsStr = convertToStringMap(runtimeParams);

            // 3. Check if simplified structure
            if (batchSpec.getSourceTable() == null && batchSpec.getCustomSourceSql() == null) {
                throw new DataSyncException("BatchSpec must have either sourceTable or customSourceSql");
            }

            // 4. Build source SQL (with parameter replacement)
            String sourceSql = buildSourceSql(batchSpec, runtimeParamsStr);
            log.info("Source SQL: {}", sourceSql);

            // 5. Fetch source data
            DatabaseSourceConfig sourceConfig = databaseSourceConfigService.findByCode(
                    dataObject.getSourceDbConfigCode()
            );
            DataSource sourceDataSource = databaseSourceConfigService.createDataSource(sourceConfig);
            JdbcTemplate sourceJdbc = new JdbcTemplate(sourceDataSource);
            List<Map<String, Object>> sourceData = sourceJdbc.queryForList(sourceSql);
            
            syncLog.setRecordsFetched(sourceData.size());
            log.info("Fetched {} records from source", sourceData.size());
            
            // Debug: Log first record keys to see column names
            if (!sourceData.isEmpty()) {
                log.info("First source record keys: {}", sourceData.get(0).keySet());
            }

            // 6. Get destination DataSource (local database)
            DatabaseSourceConfig destConfig = createLocalDestinationConfig();
            DataSource destDataSource = databaseSourceConfigService.createDataSource(destConfig);
            JdbcTemplate destJdbc = new JdbcTemplate(destDataSource);

            // 7. Auto-create destination table if not exists
            createDestinationTableIfNotExists(destJdbc, dataObject, batchSpec);

            // 8. Fetch existing data from destination (with parameter replacement)
            String destSelectSql = buildDestinationSelectSql(batchSpec, runtimeParamsStr);
            List<Map<String, Object>> destData = destJdbc.queryForList(destSelectSql);
            log.info("Fetched {} existing records from destination", destData.size());

            // 8. Map source data to destination format (with extra_data)
            List<Map<String, Object>> mappedSourceData = new ArrayList<>();
            for (Map<String, Object> sourceRecord : sourceData) {
                mappedSourceData.add(mapSourceToDestRecord(sourceRecord, batchSpec));
            }

            // 9. Compare and sync
            int inserted = 0;
            int updated = 0;
            int deleted = 0;

            // Create maps for comparison
            Map<String, Map<String, Object>> sourceMap = createKeyMap(mappedSourceData, batchSpec.getPrimaryKeys());
            Map<String, Map<String, Object>> destMap = createKeyMap(destData, batchSpec.getPrimaryKeys());

            // INSERT new records
            for (String key : sourceMap.keySet()) {
                if (!destMap.containsKey(key)) {
                    Map<String, Object> record = sourceMap.get(key);
                    insertRecord(destJdbc, batchSpec, record);
                    inserted++;
                }
            }

            // UPDATE existing records
            for (String key : sourceMap.keySet()) {
                if (destMap.containsKey(key)) {
                    Map<String, Object> sourceRecord = sourceMap.get(key);
                    Map<String, Object> destRecord = destMap.get(key);
                    
                    if (!recordsEqual(sourceRecord, destRecord, batchSpec.getFields())) {
                        updateRecord(destJdbc, batchSpec, sourceRecord);
                        updated++;
                    }
                }
            }

            // DELETE removed records
            for (String key : destMap.keySet()) {
                if (!sourceMap.containsKey(key)) {
                    Map<String, Object> record = destMap.get(key);
                    deleteRecord(destJdbc, batchSpec, record);
                    deleted++;
                }
            }

            // Update sync log
            syncLog.setRecordsInserted(inserted);
            syncLog.setRecordsUpdated(updated);
            syncLog.setRecordsDeleted(deleted);
            syncLog.setStatus("SUCCESS");
            syncLog.setFinishedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            log.info("Sync completed: inserted={}, updated={}, deleted={}", inserted, updated, deleted);

            return SyncResult.builder()
                    .dataObjCode(dataObjCode)
                    .status("SUCCESS")
                    .recordsFetched(sourceData.size())
                    .recordsInserted(inserted)
                    .recordsUpdated(updated)
                    .recordsDeleted(deleted)
                    .startedAt(syncLog.getStartedAt())
                    .finishedAt(syncLog.getFinishedAt())
                    .build();

        } catch (Exception e) {
            log.error("Sync failed for {}: {}", dataObjCode, e.getMessage(), e);
            
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setFinishedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            throw new DataSyncException("Sync failed: " + e.getMessage(), e);
        }
    }

    private String buildSourceSql(BatchSpec batchSpec, Map<String, String> runtimeParams) {
        // Check if custom SQL mode
        if (batchSpec.getCustomSourceSql() != null && !batchSpec.getCustomSourceSql().isEmpty()) {
            log.info("Using custom source SQL");
            
            // Merge parameters: execParaList (from definition) + runtimeParams (from execution)
            Map<String, String> allParams = mergeParameters(batchSpec, runtimeParams);
            
            // Replace parameters in custom SQL
            String sql = replaceParameters(batchSpec.getCustomSourceSql(), allParams);
            log.debug("Custom SQL after parameter replacement: {}", sql);
            return sql;
        }
        
        // Simplified mode: SELECT * FROM table
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(batchSpec.getSourceTable());
        
        if (batchSpec.getWhereCondition() != null && !batchSpec.getWhereCondition().isEmpty()) {
            sql.append(" WHERE ").append(batchSpec.getWhereCondition());
        }
        
        if (batchSpec.getOrderBy() != null && !batchSpec.getOrderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(batchSpec.getOrderBy());
        }
        
        return sql.toString();
    }

    private String buildDestinationSelectSql(BatchSpec batchSpec, Map<String, String> runtimeParams) {
        // Check if custom destination SQL
        if (batchSpec.getCustomDestSql() != null && !batchSpec.getCustomDestSql().isEmpty()) {
            log.info("Using custom destination SQL");
            
            // Merge parameters
            Map<String, String> allParams = mergeParameters(batchSpec, runtimeParams);
            
            // Replace parameters
            String sql = replaceParameters(batchSpec.getCustomDestSql(), allParams);
            log.debug("Custom dest SQL after parameter replacement: {}", sql);
            return sql;
        }
        
        // Simplified mode: SELECT fields + extra_data
        StringBuilder sql = new StringBuilder("SELECT ");
        
        for (int i = 0; i < batchSpec.getFields().size(); i++) {
            sql.append(batchSpec.getFields().get(i).getDestField());
            sql.append(", ");
        }
        
        // Thêm extra_data column
        sql.append("extra_data");
        sql.append(" FROM ").append(batchSpec.getDestTable());
        
        return sql.toString();
    }

    /**
     * Extract extra fields (fields không có trong BatchSpec.fields)
     */
    private Map<String, Object> extractExtraFields(Map<String, Object> sourceRecord, BatchSpec batchSpec) {
        Map<String, Object> extraData = new HashMap<>();
        
        // Lấy danh sách các sourceField đã được map (case-insensitive)
        Set<String> mappedSourceFields = new HashSet<>();
        for (MapField field : batchSpec.getFields()) {
            mappedSourceFields.add(field.getSourceField().toLowerCase());
        }
        
        log.debug("Source record keys: {}", sourceRecord.keySet());
        log.debug("Mapped source fields: {}", mappedSourceFields);
        
        // Các field không được map -> extraData
        sourceRecord.forEach((key, value) -> {
            if (!mappedSourceFields.contains(key.toLowerCase())) {
                log.debug("Adding to extraData: {} = {}", key, value);
                extraData.put(key, value);
            }
        });
        
        log.info("Extracted {} extra fields: {}", extraData.size(), extraData.keySet());
        return extraData;
    }

    /**
     * Map source record sang destination format với extra_data
     */
    private Map<String, Object> mapSourceToDestRecord(Map<String, Object> sourceRecord, BatchSpec batchSpec) {
        Map<String, Object> destRecord = new HashMap<>();
        
        // Map các field đã định nghĩa
        for (MapField field : batchSpec.getFields()) {
            Object value = sourceRecord.get(field.getSourceField());
            destRecord.put(field.getDestField(), value);
        }
        
        // Extract extra fields
        Map<String, Object> extraData = extractExtraFields(sourceRecord, batchSpec);
        if (!extraData.isEmpty()) {
            try {
                String extraJson = objectMapper.writeValueAsString(extraData);
                destRecord.put("extra_data", extraJson);
                log.info("Mapped extra_data: {}", extraJson);
            } catch (Exception e) {
                log.warn("Failed to serialize extra data: {}", e.getMessage());
                destRecord.put("extra_data", "{}");
            }
        } else {
            destRecord.put("extra_data", null);
            log.debug("No extra fields found, extra_data set to null");
        }
        
        return destRecord;
    }

    private Map<String, Map<String, Object>> createKeyMap(List<Map<String, Object>> data, List<String> primaryKeys) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        for (Map<String, Object> record : data) {
            String key = primaryKeys.stream()
                    .map(pk -> String.valueOf(record.get(pk)))
                    .reduce((a, b) -> a + "||" + b)
                    .orElse("");
            result.put(key, record);
        }
        
        return result;
    }

    private boolean recordsEqual(Map<String, Object> source, Map<String, Object> dest, List<MapField> fields) {
        // So sánh các mapped fields
        for (MapField field : fields) {
            Object sourceVal = source.get(field.getDestField());
            Object destVal = dest.get(field.getDestField());
            
            // Normalize values before comparison
            sourceVal = normalizeValue(sourceVal);
            destVal = normalizeValue(destVal);
            
            if (!Objects.equals(sourceVal, destVal)) {
                log.debug("Field {} differs: source={}, dest={}", field.getDestField(), sourceVal, destVal);
                return false;
            }
        }
        
        // So sánh extra_data
        Object sourceExtraData = source.get("extra_data");
        Object destExtraData = dest.get("extra_data");
        
        // Normalize extra_data (convert null/empty to null)
        sourceExtraData = normalizeExtraData(sourceExtraData);
        destExtraData = normalizeExtraData(destExtraData);
        
        if (!Objects.equals(sourceExtraData, destExtraData)) {
            log.debug("extra_data differs: source={}, dest={}", sourceExtraData, destExtraData);
            return false;
        }
        
        return true;
    }
    
    /**
     * Normalize extra_data: null, "", "{}" đều coi là null
     */
    private Object normalizeExtraData(Object extraData) {
        if (extraData == null) {
            return null;
        }
        String str = extraData.toString().trim();
        if (str.isEmpty() || str.equals("{}") || str.equals("null")) {
            return null;
        }
        return str;
    }

    /**
     * Normalize giá trị để so sánh chính xác
     * - Convert BigDecimal về cùng scale
     * - Convert Timestamp về LocalDateTime
     * - Trim string
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // BigDecimal - strip trailing zeros để so sánh
        if (value instanceof java.math.BigDecimal) {
            return ((java.math.BigDecimal) value).stripTrailingZeros();
        }
        
        // Timestamp -> LocalDateTime
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        
        // Date -> LocalDate
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        
        // String - trim
        if (value instanceof String) {
            return ((String) value).trim();
        }
        
        // Number types - convert to comparable format
        if (value instanceof Number) {
            // Convert all numbers to BigDecimal for comparison
            return new java.math.BigDecimal(value.toString()).stripTrailingZeros();
        }
        
        return value;
    }

    private void insertRecord(JdbcTemplate jdbc, BatchSpec batchSpec, Map<String, Object> record) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(batchSpec.getDestTable()).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");
        
        List<Object> params = new ArrayList<>();
        
        for (int i = 0; i < batchSpec.getFields().size(); i++) {
            MapField field = batchSpec.getFields().get(i);
            sql.append(field.getDestField());
            values.append("?");
            sql.append(", ");
            values.append(", ");
            
            params.add(record.get(field.getDestField()));
        }
        
        // Add extra_data
        sql.append("extra_data)");
        values.append("?::jsonb)");
        params.add(record.get("extra_data"));
        
        sql.append(values);
        
        jdbc.update(sql.toString(), params.toArray());
    }

    private void updateRecord(JdbcTemplate jdbc, BatchSpec batchSpec, Map<String, Object> record) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(batchSpec.getDestTable()).append(" SET ");
        List<Object> params = new ArrayList<>();
        
        // SET clause for mapped fields
        boolean first = true;
        for (MapField field : batchSpec.getFields()) {
            if (!batchSpec.getPrimaryKeys().contains(field.getDestField())) {
                if (!first) {
                    sql.append(", ");
                }
                sql.append(field.getDestField()).append(" = ?");
                params.add(record.get(field.getDestField()));
                first = false;
            }
        }
        
        // SET extra_data
        if (!first) {
            sql.append(", ");
        }
        sql.append("extra_data = ?::jsonb");
        params.add(record.get("extra_data"));
        
        // WHERE clause
        sql.append(" WHERE ");
        for (int i = 0; i < batchSpec.getPrimaryKeys().size(); i++) {
            String pk = batchSpec.getPrimaryKeys().get(i);
            sql.append(pk).append(" = ?");
            params.add(record.get(pk));
            
            if (i < batchSpec.getPrimaryKeys().size() - 1) {
                sql.append(" AND ");
            }
        }
        
        jdbc.update(sql.toString(), params.toArray());
    }

    private void deleteRecord(JdbcTemplate jdbc, BatchSpec batchSpec, Map<String, Object> record) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(batchSpec.getDestTable()).append(" WHERE ");
        List<Object> params = new ArrayList<>();
        
        for (int i = 0; i < batchSpec.getPrimaryKeys().size(); i++) {
            String pk = batchSpec.getPrimaryKeys().get(i);
            sql.append(pk).append(" = ?");
            params.add(record.get(pk));
            
            if (i < batchSpec.getPrimaryKeys().size() - 1) {
                sql.append(" AND ");
            }
        }
        
        jdbc.update(sql.toString(), params.toArray());
    }

    /**
     * Tạo config cho local destination database
     */
    private DatabaseSourceConfig createLocalDestinationConfig() {
        DatabaseSourceConfig config = new DatabaseSourceConfig();
        config.setDbType("POSTGRESQL");
        config.setHost("localhost");
        config.setPort(5432);
        config.setDatabaseName("pipeline_data");
        config.setUsername("postgres");
        config.setPassword("postgres");
        return config;
    }

    /**
     * Tự động tạo destination table nếu chưa tồn tại
     */
    private void createDestinationTableIfNotExists(JdbcTemplate jdbc, DataObject dataObject, BatchSpec batchSpec) {
        try {
            // Parse table name from destTable (may include schema)
            String[] parts = batchSpec.getDestTable().split("\\.");
            String tableName = parts.length > 1 ? parts[1] : parts[0];
            
            // Use schema from DataObject if available, otherwise parse from destTable
            String schema;
            if (dataObject.getDestSchema() != null && !dataObject.getDestSchema().trim().isEmpty()) {
                schema = dataObject.getDestSchema();
                log.info("Using destSchema from DataObject: {}", schema);
            } else {
                schema = parts.length > 1 ? parts[0] : "pipeline_data";
                log.info("Using schema parsed from destTable: {}", schema);
            }
            
            // Build full table name and update BatchSpec to use consistent schema
            String fullTableName = schema + "." + tableName;
            batchSpec.setDestTable(fullTableName);
            log.info("Normalized destTable to: {}", fullTableName);
            
            // Auto-create schema if not exists
            createSchemaIfNotExists(jdbc, schema);
            
            // Check if table exists
            String checkTableSql = "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = ? AND table_name = ?";
            
            Integer count = jdbc.queryForObject(checkTableSql, Integer.class, schema, tableName);
            
            if (count != null && count > 0) {
                log.info("Table {}.{} already exists, checking for extra_data column", schema, tableName);
                // Table đã tồn tại → kiểm tra và thêm cột extra_data nếu chưa có
                ensureExtraDataColumnExists(jdbc, schema, tableName, fullTableName);
                return;
            }

            // Create table
            StringBuilder createTableSql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(fullTableName).append(" (");
            
            for (int i = 0; i < batchSpec.getFields().size(); i++) {
                MapField field = batchSpec.getFields().get(i);
                String dataType = mapDataType(field.getDataType());
                
                createTableSql.append(field.getDestField()).append(" ").append(dataType);
                
                // Primary key constraint
                if (batchSpec.getPrimaryKeys().contains(field.getDestField())) {
                    createTableSql.append(" NOT NULL");
                }
                
                if (i < batchSpec.getFields().size() - 1) {
                    createTableSql.append(", ");
                }
            }
            
            // Add extra_data column for unmapped fields
            createTableSql.append(", extra_data JSONB");
            
            // Add primary key constraint
            if (!batchSpec.getPrimaryKeys().isEmpty()) {
                createTableSql.append(", PRIMARY KEY (");
                createTableSql.append(String.join(", ", batchSpec.getPrimaryKeys()));
                createTableSql.append(")");
            }
            
            createTableSql.append(")");
            
            log.info("Creating destination table: {}", createTableSql);
            jdbc.execute(createTableSql.toString());
            log.info("Table {} created successfully", fullTableName);
            
        } catch (Exception e) {
            log.error("Failed to create destination table: {}", e.getMessage(), e);
            throw new DataSyncException("Failed to create destination table: " + e.getMessage(), e);
        }
    }

    /**
     * Đảm bảo cột extra_data tồn tại trong table (thêm nếu chưa có)
     */
    private void ensureExtraDataColumnExists(JdbcTemplate jdbc, String schema, String tableName, String fullTableName) {
        try {
            // Check if extra_data column exists
            String checkColumnSql = "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_schema = ? AND table_name = ? AND column_name = 'extra_data'";
            
            Integer count = jdbc.queryForObject(checkColumnSql, Integer.class, schema, tableName);
            
            if (count != null && count > 0) {
                log.info("Column extra_data already exists in {}", fullTableName);
                return;
            }

            // Add extra_data column
            String alterTableSql = String.format("ALTER TABLE %s ADD COLUMN extra_data JSONB", fullTableName);
            jdbc.execute(alterTableSql);
            
            log.info("Added extra_data column to table {}", fullTableName);
            
        } catch (Exception e) {
            log.warn("Failed to add extra_data column to {}: {}", fullTableName, e.getMessage());
            // Don't throw exception - column might already exist
        }
    }

    /**
     * Tự động tạo schema nếu chưa tồn tại
     */
    private void createSchemaIfNotExists(JdbcTemplate jdbc, String schemaName) {
        try {
            // Check if schema exists
            String checkSchemaSql = "SELECT COUNT(*) FROM information_schema.schemata " +
                    "WHERE schema_name = ?";
            
            Integer count = jdbc.queryForObject(checkSchemaSql, Integer.class, schemaName);
            
            if (count != null && count > 0) {
                log.info("Schema {} already exists", schemaName);
                return;
            }

            // Create schema
            String createSchemaSql = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
            jdbc.execute(createSchemaSql);
            
            log.info("Schema created: {}", schemaName);
            
        } catch (Exception e) {
            log.warn("Failed to create schema {}: {}", schemaName, e.getMessage());
            // Don't throw exception - schema might already exist or user might not have permission
        }
    }

    /**
     * Map dataType từ BatchSpec sang PostgreSQL data type
     */
    private String mapDataType(String dataType) {
        if (dataType == null || dataType.isEmpty()) {
            return "VARCHAR(255)";
        }
        
        switch (dataType.toUpperCase()) {
            case "BIGINT":
            case "LONG":
                return "BIGINT";
            case "INTEGER":
            case "INT":
                return "INTEGER";
            case "DECIMAL":
            case "NUMERIC":
                return "DECIMAL(15,2)";
            case "VARCHAR":
            case "STRING":
                return "VARCHAR(255)";
            case "TEXT":
                return "TEXT";
            case "TIMESTAMP":
            case "DATETIME":
                return "TIMESTAMP";
            case "DATE":
                return "DATE";
            case "BOOLEAN":
            case "BOOL":
                return "BOOLEAN";
            default:
                return "VARCHAR(255)";
        }
    }
    
    /**
     * Replace parameters in SQL string
     * Parameters format: ${param_name}
     */
    private String replaceParameters(String sql, Map<String, String> params) {
        if (sql == null || params == null) {
            return sql;
        }
        
        String result = sql;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue();
            
            if (value != null) {
                result = result.replace(placeholder, value);
            }
        }
        
        log.debug("SQL after parameter replacement: {}", result);
        return result;
    }
    
    /**
     * Merge execParaList (from BatchSpec definition) with runtimeParams (from execution request)
     * runtimeParams will override execParaList if same key exists
     */
    private Map<String, String> mergeParameters(BatchSpec batchSpec, Map<String, String> runtimeParams) {
        Map<String, String> merged = new HashMap<>();
        
        // First add execParaList (default values from BatchSpec)
        if (batchSpec.getExecParaList() != null) {
            merged.putAll(batchSpec.getExecParaList());
        }
        
        // Then add runtimeParams (override defaults)
        if (runtimeParams != null) {
            merged.putAll(runtimeParams);
        }
        
        log.debug("Merged parameters: {}", merged);
        return merged;
    }
    
    /**
     * Convert Map<String, Object> to Map<String, String>
     */
    private Map<String, String> convertToStringMap(Map<String, Object> objectMap) {
        if (objectMap == null) {
            return new HashMap<>();
        }
        
        Map<String, String> stringMap = new HashMap<>();
        objectMap.forEach((key, value) -> {
            if (value != null) {
                stringMap.put(key, value.toString());
            }
        });
        
        return stringMap;
    }
}
