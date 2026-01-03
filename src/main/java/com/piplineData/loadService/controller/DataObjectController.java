package com.piplineData.loadService.controller;

import com.piplineData.loadService.dto.DataObjectRequest;
import com.piplineData.loadService.entity.DataObject;
import com.piplineData.loadService.service.DataObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller để quản lý Data Object Configuration
 * Data collector sẽ gọi các API này để khai báo data objects
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/data-object")
@RequiredArgsConstructor
public class DataObjectController {

    private final DataObjectService service;

    /**
     * Tạo mới data object
     * POST /api/v1/data-object
     * 
     * Data collector gọi API này để khai báo mapping configuration
     */
    @PostMapping
    public ResponseEntity<DataObject> create(@RequestBody DataObjectRequest request) {
        log.info("Create data object request: {}", request.getDataObjCode());

        DataObject created = service.create(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lấy tất cả data objects đang active
     * GET /api/v1/data-object
     */
    @GetMapping
    public ResponseEntity<List<DataObject>> findAll() {
        log.info("Get all active data objects");

        List<DataObject> dataObjects = service.findAllActive();

        return ResponseEntity.ok(dataObjects);
    }

    /**
     * Lấy data object theo ID
     * GET /api/v1/data-object/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DataObject> findById(@PathVariable Long id) {
        log.info("Get data object by id: {}", id);

        DataObject dataObject = service.findById(id);

        return ResponseEntity.ok(dataObject);
    }

    /**
     * Lấy data object theo code
     * GET /api/v1/data-object/code/{dataObjCode}
     */
    @GetMapping("/code/{dataObjCode}")
    public ResponseEntity<DataObject> findByCode(@PathVariable String dataObjCode) {
        log.info("Get data object by code: {}", dataObjCode);

        DataObject dataObject = service.findByCode(dataObjCode);

        return ResponseEntity.ok(dataObject);
    }

    /**
     * Update data object
     * PUT /api/v1/data-object/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<DataObject> update(
            @PathVariable Long id,
            @RequestBody DataObjectRequest request) {

        log.info("Update data object: {}", id);

        DataObject updated = service.update(id, request);

        return ResponseEntity.ok(updated);
    }

    /**
     * Xóa data object (soft delete)
     * DELETE /api/v1/data-object/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("Delete data object: {}", id);

        service.delete(id);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Data object deleted: " + id
        ));
    }

    /**
     * Validate BatchSpec configuration
     * POST /api/v1/data-object/{dataObjCode}/validate
     * 
     * Data collector gọi API này để validate mapping config trước khi chạy sync
     */
    @PostMapping("/{dataObjCode}/validate")
    public ResponseEntity<Map<String, Object>> validateBatchSpec(@PathVariable String dataObjCode) {
        log.info("Validate BatchSpec for: {}", dataObjCode);

        boolean isValid = service.validateBatchSpecWithTestQuery(dataObjCode);

        return ResponseEntity.ok(Map.of(
                "dataObjCode", dataObjCode,
                "isValid", isValid,
                "message", isValid ? "BatchSpec is valid" : "BatchSpec validation failed"
        ));
    }
}
