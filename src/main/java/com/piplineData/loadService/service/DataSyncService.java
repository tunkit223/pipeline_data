package com.piplineData.loadService.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.MapDifference;

import com.piplineData.loadService.config.DatabaseConfig;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final DataObjectRepository dataObjectRepository;
    private final SyncLogRepository syncLogRepository;
    private final BatchSpecParser batchSpecParser;
    private final DynamicDatabaseService dynamicDatabaseService;
    private final DataComparator dataComparator;
    private final DatabaseConfig databaseConfig;
    private final RetryTemplate retryTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseSourceConfigService databaseSourceConfigService;
    private final SimplifiedDataSyncService simplifiedDataSyncService;

    /**
     * Main method để sync dữ liệu
     * Auto-detect custom SQL, simplified, or legacy BatchSpec structure
     */
    @Transactional
    public SyncResult syncData(String dataObjCode, Map<String, Object> runtimeParams) {

        try {
            // Load DataObject để check BatchSpec structure
            DataObject dataObject = loadDataObject(dataObjCode);
            BatchSpec batchSpec = batchSpecParser.parseBatchSpec(dataObject.getBatchSpec());

            // Auto-detect structure và route đến service phù hợp
            // Priority: Custom SQL > Simplified > Legacy
            if (batchSpec.getCustomSourceSql() != null && !batchSpec.getCustomSourceSql().trim().isEmpty()) {
                log.info("Detected custom SQL BatchSpec structure, using SimplifiedDataSyncService");
                return simplifiedDataSyncService.syncData(dataObjCode, runtimeParams);
            } else if (batchSpec.getSourceTable() != null && batchSpec.getDestTable() != null) {
                log.info("Detected simplified BatchSpec structure, using SimplifiedDataSyncService");
                return simplifiedDataSyncService.syncData(dataObjCode, runtimeParams);
            } else {
                log.info("Detected legacy BatchSpec structure, using legacy sync flow");
                return syncDataLegacy(dataObjCode, runtimeParams);
            }

        } catch (Exception e) {
            log.error("Failed to determine BatchSpec structure for {}: {}", dataObjCode, e.getMessage());
            throw new DataSyncException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy sync method for old BatchSpec structure
     */
    @Transactional
    public SyncResult syncDataLegacy(String dataObjCode, Map<String, Object> runtimeParams) {

        SyncLog syncLog = createSyncLog(dataObjCode);

        try {
            // 1. Load DataObject configuration
            DataObject dataObject = loadDataObject(dataObjCode);

            // 2. Parse BatchSpec
            BatchSpec batchSpec = batchSpecParser.parseBatchSpec(dataObject.getBatchSpec());
            batchSpecParser.validateBatchSpec(batchSpec);

            // 3. Get script configuration
            BatchSpec.ScriptConfig scriptConfig = batchSpec.getScript().get(0);

            // 4. Prepare parameters
            Map<String, Object> execParams = prepareParameters(batchSpec, runtimeParams);

            // 5. Render SQL queries
            String sourceSql = batchSpecParser.renderSql(
                    scriptConfig.getSource().getBatchscript(), execParams
            );
            String destSql = batchSpecParser.renderSql(
                    scriptConfig.getDestination().getBatchscript(), execParams
            );

            // 6. Extract MapFields
            List<MapField> mapFields = scriptConfig.getMapfields().get(0).getMap();
            List<String> keyFields = dataComparator.extractKeyFields(mapFields);
            List<String> valueFields = dataComparator.extractValueFields(mapFields);

            // 7. Fetch data với retry mechanism
            List<Map<String, Object>> sourceData = retryTemplate.execute(context ->
                    fetchSourceData(scriptConfig.getSource().getDbsource(), sourceSql)
            );
            syncLog.setRecordsFetched(sourceData.size());

            List<Map<String, Object>> destData = retryTemplate.execute(context ->
                    fetchDestinationData(destSql)
            );

            // 8. Compare data using MapDifference
            MapDifference<String, Map<String, Object>> difference =
                    dataComparator.compareData(sourceData, destData, keyFields);

            // 9. Apply changes (Insert/Update/Delete)
            SyncResult result = applyChanges(
                    dataObject,
                    difference,
                    keyFields,
                    valueFields,
                    mapFields
            );

            // 10. Update sync log
            syncLog.setRecordsInserted(result.getRecordsInserted());
            syncLog.setRecordsUpdated(result.getRecordsUpdated());
            syncLog.setRecordsDeleted(result.getRecordsDeleted());
            syncLog.setStatus("SUCCESS");
            syncLog.setFinishedAt(LocalDateTime.now());
            syncLogRepository.save(syncLog);

            log.info("Data sync completed successfully for: {}", dataObjCode);
            return result;

        } catch (Exception e) {
            log.error("Data sync failed for {}: {}", dataObjCode, e.getMessage(), e);

            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setFinishedAt(LocalDateTime.now());
            syncLog.setRetryCount(syncLog.getRetryCount() + 1);
            syncLogRepository.save(syncLog);

            throw new DataSyncException("Data sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load DataObject từ database
     */
    private DataObject loadDataObject(String dataObjCode) {
        return dataObjectRepository.findByDataObjCodeAndIsActiveTrue(dataObjCode)
                .orElseThrow(() -> new DataSyncException(
                        "DataObject not found or inactive: " + dataObjCode
                ));
    }

    /**
     * Tạo SyncLog mới
     */
    private SyncLog createSyncLog(String dataObjCode) {
        SyncLog syncLog = new SyncLog();
        syncLog.setDataObjCode(dataObjCode);
        syncLog.setSyncType("BATCH");
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(LocalDateTime.now());
        syncLog.setRetryCount(0);
        return syncLogRepository.save(syncLog);
    }

    /**
     * Chuẩn bị parameters cho SQL rendering
     */
    private Map<String, Object> prepareParameters(
            BatchSpec batchSpec,
            Map<String, Object> runtimeParams) {

        Map<String, Object> params = new HashMap<>();

        // Map từ exec_para_list trong BatchSpec
        if (batchSpec.getExecParaList() != null) {
            batchSpec.getExecParaList().forEach((key, value) -> {
                if (runtimeParams.containsKey(value)) {
                    params.put(key, runtimeParams.get(value));
                }
            });
        }

        // Add runtime params trực tiếp
        params.putAll(runtimeParams);

        log.debug("Prepared parameters: {}", params);
        return params;
    }

    /**
     * Fetch data từ Source DB
     */
    private List<Map<String, Object>> fetchSourceData(String dbConfigCode, String sql) {
        log.info("Fetching data from source DB: {}", dbConfigCode);

        // Lấy database config từ service
        DatabaseSourceConfig dbConfig = databaseSourceConfigService.findByCode(dbConfigCode);
        DataSource sourceDataSource = databaseSourceConfigService.createDataSource(dbConfig);

        return dynamicDatabaseService.executeQueryOnDestDb(sourceDataSource, sql);
    }

    /**
     * Fetch data từ Destination DB
     */
    private List<Map<String, Object>> fetchDestinationData(String sql) {
        log.info("Fetching data from destination DB");

        DataSource destDataSource = databaseConfig.destinationDataSource();
        return dynamicDatabaseService.executeQueryOnDestDb(destDataSource, sql);
    }

    /**
     * Apply changes (Insert/Update/Delete) vào Destination DB
     */
    @Transactional
    private SyncResult applyChanges(
            DataObject dataObject,
            MapDifference<String, Map<String, Object>> difference,
            List<String> keyFields,
            List<String> valueFields,
            List<MapField> mapFields) {

        int inserted = 0;
        int updated = 0;
        int deleted = 0;

        DataSource destDataSource = databaseConfig.destinationDataSource();
        String destTable = dataObject.getDestSchema() + "." + dataObject.getDestTablename();

        // 1. INSERT: Entries only in source (new records)
        Map<String, Map<String, Object>> toInsert = difference.entriesOnlyOnLeft();
        for (Map.Entry<String, Map<String, Object>> entry : toInsert.entrySet()) {
            insertRecord(destDataSource, destTable, entry.getValue(), keyFields, valueFields, mapFields);
            inserted++;
        }

        // 2. UPDATE: Entries differing (changed records)
        Map<String, MapDifference.ValueDifference<Map<String, Object>>> toDiff =
                difference.entriesDiffering();
        for (Map.Entry<String, MapDifference.ValueDifference<Map<String, Object>>> entry : toDiff.entrySet()) {
            Map<String, Object> newValue = entry.getValue().leftValue(); // source value
            Map<String, Object> keyMap = dataComparator.parseCompositeKey(entry.getKey());
            updateRecord(destDataSource, destTable, newValue, keyMap, valueFields, mapFields);
            updated++;
        }

        // 3. DELETE: Entries only in destination (removed records)
        Map<String, Map<String, Object>> toDelete = difference.entriesOnlyOnRight();
        for (Map.Entry<String, Map<String, Object>> entry : toDelete.entrySet()) {
            Map<String, Object> keyMap = dataComparator.parseCompositeKey(entry.getKey());
            deleteRecord(destDataSource, destTable, keyMap, keyFields);
            deleted++;
        }

        log.info("Changes applied: inserted={}, updated={}, deleted={}", inserted, updated, deleted);

        return SyncResult.builder()
                .dataObjCode(dataObject.getDataObjCode())
                .recordsFetched(toInsert.size() + toDiff.size() + difference.entriesInCommon().size())
                .recordsInserted(inserted)
                .recordsUpdated(updated)
                .recordsDeleted(deleted)
                .entriesInCommon(new HashMap<>(difference.entriesInCommon()))
                .entriesDiffering(convertValueDifferenceMap(toDiff))
                .entriesOnlyInSource(new HashMap<>(toInsert))
                .entriesOnlyInDest(new HashMap<>(toDelete))
                .status("SUCCESS")
                .build();
    }

    /**
     * INSERT record vào Destination DB
     */
    private void insertRecord(
            DataSource destDataSource,
            String destTable,
            Map<String, Object> record,
            List<String> keyFields,
            List<String> valueFields,
            List<MapField> mapFields) {

        try {
            // Map field name to MapField definition
            Map<String, MapField> fieldDefMap = mapFields.stream()
                    .collect(Collectors.toMap(MapField::getTo, mf -> mf));

            // Tách mapped fields và extra fields
            Map<String, Object> mappedData = new HashMap<>();
            Map<String, Object> extraData = new HashMap<>();

            Set<String> mappedTargetFieldNames = mapFields.stream()
                    .map(MapField::getTo)
                    .collect(Collectors.toSet());

            // Xử lý mapping với type conversion
            for (MapField mapField : mapFields) {
                Object sourceValue = record.get(mapField.getFrom());
                
                // Apply default value nếu null
                if (sourceValue == null && mapField.getDefaultValue() != null) {
                    sourceValue = mapField.getDefaultValue();
                }

                // Type conversion
                Object convertedValue = convertValueType(sourceValue, mapField.getDataType());
                mappedData.put(mapField.getTo(), convertedValue);
            }

            // Các field không được map -> extra_data
            record.forEach((key, value) -> {
                if (!mapFields.stream().anyMatch(mf -> mf.getFrom().equals(key))) {
                    extraData.put(key, value);
                }
            });

            // Build INSERT SQL
            List<String> allFields = new ArrayList<>();
            allFields.addAll(keyFields);
            allFields.addAll(valueFields);
            
            if (!extraData.isEmpty()) {
                allFields.add("extra_data"); // cột JSONB
            }

            String columns = String.join(", ", allFields);
            String placeholders = String.join(", ", Collections.nCopies(allFields.size(), "?"));

            String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
                    destTable, columns, placeholders);

            // Prepare parameters
            List<Object> params = new ArrayList<>();
            for (String field : keyFields) {
                params.add(mappedData.get(field));
            }
            for (String field : valueFields) {
                params.add(mappedData.get(field));
            }
            
            if (!extraData.isEmpty()) {
                // Convert extra data to JSON string
                String extraDataJson = objectMapper.writeValueAsString(extraData);
                params.add(extraDataJson);
            }

            dynamicDatabaseService.executeInsert(destDataSource, sql, params.toArray());

            log.debug("Inserted record with keys: {}", keyFields.stream()
                    .map(field -> field + "=" + mappedData.get(field))
                    .collect(Collectors.joining(", ")));

        } catch (Exception e) {
            log.error("Failed to insert record: {}", e.getMessage(), e);
            throw new DataSyncException("Insert failed", e);
        }
    }

    /**
     * UPDATE record trong Destination DB
     */
    private void updateRecord(
            DataSource destDataSource,
            String destTable,
            Map<String, Object> record,
            Map<String, Object> keyMap,
            List<String> valueFields,
            List<MapField> mapFields) {

        try {
            // Tách mapped fields và extra fields
            Map<String, Object> mappedData = new HashMap<>();
            Map<String, Object> extraData = new HashMap<>();

            // Xử lý mapping với type conversion
            for (MapField mapField : mapFields) {
                Object sourceValue = record.get(mapField.getFrom());
                
                // Apply default value nếu null
                if (sourceValue == null && mapField.getDefaultValue() != null) {
                    sourceValue = mapField.getDefaultValue();
                }

                // Type conversion
                Object convertedValue = convertValueType(sourceValue, mapField.getDataType());
                mappedData.put(mapField.getTo(), convertedValue);
            }

            // Các field không được map -> extra_data
            record.forEach((key, value) -> {
                if (!mapFields.stream().anyMatch(mf -> mf.getFrom().equals(key))) {
                    extraData.put(key, value);
                }
            });

            // Build UPDATE SQL
            List<String> setClauses = new ArrayList<>();
            valueFields.forEach(field -> setClauses.add(field + " = ?"));
            
            if (!extraData.isEmpty()) {
                setClauses.add("extra_data = ?");
            }

            List<String> whereClauses = new ArrayList<>();
            keyMap.keySet().forEach(key -> whereClauses.add(key + " = ?"));

            String sql = String.format("UPDATE %s SET %s WHERE %s",
                    destTable,
                    String.join(", ", setClauses),
                    String.join(" AND ", whereClauses));

            // Prepare parameters
            List<Object> params = new ArrayList<>();
            valueFields.forEach(field -> params.add(mappedData.get(field)));
            
            if (!extraData.isEmpty()) {
                String extraDataJson = objectMapper.writeValueAsString(extraData);
                params.add(extraDataJson);
            }
            
            keyMap.values().forEach(params::add);

            dynamicDatabaseService.executeUpdate(destDataSource, sql, params.toArray());

            log.debug("Updated record with keys: {}", keyMap);

        } catch (Exception e) {
            log.error("Failed to update record: {}", e.getMessage(), e);
            throw new DataSyncException("Update failed", e);
        }
    }

    /**
     * DELETE record từ Destination DB
     */
    private void deleteRecord(
            DataSource destDataSource,
            String destTable,
            Map<String, Object> keyMap,
            List<String> keyFields) {

        try {
            List<String> whereClauses = new ArrayList<>();
            keyFields.forEach(field -> whereClauses.add(field + " = ?"));

            String sql = String.format("DELETE FROM %s WHERE %s",
                    destTable,
                    String.join(" AND ", whereClauses));

            Object[] params = keyFields.stream()
                    .map(keyMap::get)
                    .toArray();

            dynamicDatabaseService.executeDelete(destDataSource, sql, params);

            log.debug("Deleted record with keys: {}", keyMap);

        } catch (Exception e) {
            log.error("Failed to delete record: {}", e.getMessage());
            throw new DataSyncException("Delete failed", e);
        }
    }

    /**
     * Convert MapDifference.ValueDifference to simple Map
     */
    private Map<String, Object> convertValueDifferenceMap(
            Map<String, MapDifference.ValueDifference<Map<String, Object>>> diffMap) {

        Map<String, Object> result = new HashMap<>();
        diffMap.forEach((key, valueDiff) -> {
            Map<String, Object> diff = new HashMap<>();
            diff.put("source", valueDiff.leftValue());
            diff.put("destination", valueDiff.rightValue());
            result.put(key, diff);
        });
        return result;
    }

    /**
     * Retry manual cho failed sync
     */
    public SyncResult retrySyncManually(String dataObjCode, Map<String, Object> runtimeParams) {
        log.info("Manual retry triggered for: {}", dataObjCode);
        return syncData(dataObjCode, runtimeParams);
    }

    /**
     * Get sync history by dataObjCode
     */
    public List<SyncLog> getSyncHistory(String dataObjCode) {
        return syncLogRepository.findByDataObjCodeOrderByStartedAtDesc(dataObjCode);
    }

    /**
     * Get all sync history (all data objects)
     */
    public List<SyncLog> getAllSyncHistory() {
        return syncLogRepository.findAllByOrderByStartedAtDesc();
    }

    /**
     * Convert value sang type phù hợp với destination column
     */
    private Object convertValueType(Object value, String targetType) {
        if (value == null) {
            return null;
        }

        if (targetType == null || targetType.isEmpty()) {
            return value; // Không convert
        }

        try {
            String type = targetType.toUpperCase();
            String valueStr = value.toString();

            switch (type) {
                case "INTEGER":
                case "INT":
                case "INT4":
                    return Integer.valueOf(valueStr);

                case "BIGINT":
                case "INT8":
                case "LONG":
                    return Long.valueOf(valueStr);

                case "DECIMAL":
                case "NUMERIC":
                    return new BigDecimal(valueStr);

                case "FLOAT":
                case "FLOAT4":
                    return Float.valueOf(valueStr);

                case "DOUBLE":
                case "FLOAT8":
                    return Double.valueOf(valueStr);

                case "BOOLEAN":
                case "BOOL":
                    return Boolean.valueOf(valueStr);

                case "VARCHAR":
                case "TEXT":
                case "STRING":
                    return valueStr;

                case "TIMESTAMP":
                case "DATETIME":
                    if (value instanceof Timestamp) {
                        return value;
                    }
                    return Timestamp.valueOf(valueStr);

                case "DATE":
                    return java.sql.Date.valueOf(valueStr);

                case "JSONB":
                case "JSON":
                    // Nếu đã là JSON string, return as-is
                    if (valueStr.startsWith("{") || valueStr.startsWith("[")) {
                        return valueStr;
                    }
                    // Convert object to JSON
                    return objectMapper.writeValueAsString(value);

                default:
                    log.warn("Unknown target type: {}, returning original value", targetType);
                    return value;
            }

        } catch (Exception e) {
            log.error("Type conversion failed for value '{}' to type '{}': {}",
                    value, targetType, e.getMessage());
            throw new DataSyncException(
                    String.format("Type conversion failed: %s to %s", value, targetType), e
            );
        }
    }
}
