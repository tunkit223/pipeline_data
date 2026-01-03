package com.piplineData.loadService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piplineData.loadService.dto.BatchSpec;
import com.piplineData.loadService.dto.DataObjectRequest;
import com.piplineData.loadService.entity.DataObject;
import com.piplineData.loadService.exception.DataSyncException;
import com.piplineData.loadService.repository.DataObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataObjectService {

    private final DataObjectRepository repository;
    private final BatchSpecParser batchSpecParser;
    private final DatabaseSourceConfigService databaseSourceConfigService;
    private final ObjectMapper objectMapper;

    /**
     * Tạo mới Data Object
     */
    @Transactional
    public DataObject create(DataObjectRequest request) {
        log.info("Creating data object: {}", request.getDataObjCode());

        // Validate code không trùng
        if (repository.existsByDataObjCode(request.getDataObjCode())) {
            throw new DataSyncException("Data object code already exists: " + request.getDataObjCode());
        }

        // Validate source database config tồn tại
        if (request.getSourceDbConfigCode() != null) {
            databaseSourceConfigService.findByCode(request.getSourceDbConfigCode());
        }

        // Convert request DTO sang Entity
        DataObject dataObject = convertToEntity(request);

        // Validate BatchSpec JSON format
        if (dataObject.getBatchSpec() != null) {
            validateBatchSpec(dataObject.getBatchSpec());
        }

        return repository.save(dataObject);
    }

    /**
     * Lấy tất cả data objects đang active
     */
    public List<DataObject> findAllActive() {
        return repository.findAllByIsActiveTrue();
    }

    /**
     * Lấy data object theo code
     */
    public DataObject findByCode(String dataObjCode) {
        return repository.findByDataObjCodeAndIsActiveTrue(dataObjCode)
                .orElseThrow(() -> new DataSyncException(
                        "Data object not found or inactive: " + dataObjCode
                ));
    }

    /**
     * Lấy data object theo ID
     */
    public DataObject findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DataSyncException("Data object not found: " + id));
    }

    /**
     * Update data object
     */
    @Transactional
    public DataObject update(Long id, DataObjectRequest request) {
        log.info("Updating data object: {}", id);

        DataObject existing = findById(id);

        // Update fields
        existing.setDataObjName(request.getDataObjName());
        existing.setSourceDbConfigCode(request.getSourceDbConfigCode());
        existing.setSourceSchema(request.getSourceSchema());
        existing.setDestSchema(request.getDestSchema());
        existing.setDestTablename(request.getDestTablename());
        existing.setDataObjNote(request.getDataObjNote());
        existing.setSyncMode(request.getSyncMode());

        // Convert và validate BatchSpec
        if (request.getBatchSpec() != null) {
            String batchSpecJson = convertObjectToJson(request.getBatchSpec());
            validateBatchSpec(batchSpecJson);
            existing.setBatchSpec(batchSpecJson);
        }

        // Validate database config nếu có thay đổi
        if (request.getSourceDbConfigCode() != null) {
            databaseSourceConfigService.findByCode(request.getSourceDbConfigCode());
        }

        return repository.save(existing);
    }
    
    /**
     * Convert DataObjectRequest DTO sang Entity
     */
    private DataObject convertToEntity(DataObjectRequest request) {
        DataObject entity = new DataObject();
        entity.setDataObjCode(request.getDataObjCode());
        entity.setDataObjName(request.getDataObjName());
        entity.setSourceDbConfigCode(request.getSourceDbConfigCode());
        entity.setSourceSchema(request.getSourceSchema());
        entity.setDestSchema(request.getDestSchema());
        entity.setDestTablename(request.getDestTablename());
        entity.setDataObjNote(request.getDataObjNote());
        entity.setSyncMode(request.getSyncMode());
        
        // Convert batchSpec object sang JSON string
        if (request.getBatchSpec() != null) {
            entity.setBatchSpec(convertObjectToJson(request.getBatchSpec()));
        }
        
        // Convert streamSpec object sang JSON string
        if (request.getStreamSpec() != null) {
            entity.setStreamSpec(convertObjectToJson(request.getStreamSpec()));
        }
        
        return entity;
    }
    
    /**
     * Convert Object sang JSON String
     */
    private String convertObjectToJson(Object obj) {
        try {
            if (obj instanceof String) {
                return (String) obj;
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataSyncException("Failed to convert object to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Xóa data object (soft delete)
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting data object: {}", id);

        DataObject dataObject = findById(id);
        dataObject.setIsActive(false);
        repository.save(dataObject);
    }

    /**
     * Validate BatchSpec JSON
     */
    private void validateBatchSpec(String batchSpecJson) {
        try {
            // Parse để kiểm tra JSON format
            BatchSpec batchSpec = batchSpecParser.parseBatchSpec(batchSpecJson);
            
            // Validate structure
            batchSpecParser.validateBatchSpec(batchSpec);
            
            log.info("BatchSpec validation passed");
            
        } catch (Exception e) {
            log.error("BatchSpec validation failed: {}", e.getMessage());
            throw new DataSyncException("Invalid BatchSpec: " + e.getMessage(), e);
        }
    }

    /**
     * Validate BatchSpec với test query (không thực thi, chỉ validate syntax)
     */
    public boolean validateBatchSpecWithTestQuery(String dataObjCode) {
        log.info("Validating BatchSpec with test query for: {}", dataObjCode);

        try {
            DataObject dataObject = findByCode(dataObjCode);
            BatchSpec batchSpec = batchSpecParser.parseBatchSpec(dataObject.getBatchSpec());
            
            // Validate SQL syntax (có thể mở rộng để test với database thật)
            BatchSpec.ScriptConfig scriptConfig = batchSpec.getScript().get(0);
            batchSpecParser.validateSql(scriptConfig.getSource().getBatchscript());
            
            if (scriptConfig.getDestination().getBatchscript() != null) {
                batchSpecParser.validateSql(scriptConfig.getDestination().getBatchscript());
            }

            log.info("Test query validation passed for: {}", dataObjCode);
            return true;

        } catch (Exception e) {
            log.error("Test query validation failed for {}: {}", dataObjCode, e.getMessage());
            return false;
        }
    }
}
