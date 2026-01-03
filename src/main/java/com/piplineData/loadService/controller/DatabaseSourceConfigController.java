package com.piplineData.loadService.controller;

import com.piplineData.loadService.entity.DatabaseSourceConfig;
import com.piplineData.loadService.service.DatabaseSourceConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller để quản lý Database Source Configuration
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/database-config")
@RequiredArgsConstructor
public class DatabaseSourceConfigController {

    private final DatabaseSourceConfigService service;

    /**
     * Tạo mới database source config
     * POST /api/v1/database-config
     */
    @PostMapping
    public ResponseEntity<DatabaseSourceConfig> create(@RequestBody DatabaseSourceConfig config) {
        log.info("Create database config request: {}", config.getConfigCode());

        DatabaseSourceConfig created = service.create(config);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lấy tất cả configs đang active
     * GET /api/v1/database-config
     */
    @GetMapping
    public ResponseEntity<List<DatabaseSourceConfig>> findAll() {
        log.info("Get all active database configs");

        List<DatabaseSourceConfig> configs = service.findAllActive();

        return ResponseEntity.ok(configs);
    }

    /**
     * Lấy config theo ID
     * GET /api/v1/database-config/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DatabaseSourceConfig> findById(@PathVariable Long id) {
        log.info("Get database config by id: {}", id);

        DatabaseSourceConfig config = service.findById(id);

        return ResponseEntity.ok(config);
    }

    /**
     * Lấy config theo code
     * GET /api/v1/database-config/code/{configCode}
     */
    @GetMapping("/code/{configCode}")
    public ResponseEntity<DatabaseSourceConfig> findByCode(@PathVariable String configCode) {
        log.info("Get database config by code: {}", configCode);

        DatabaseSourceConfig config = service.findByCode(configCode);

        return ResponseEntity.ok(config);
    }

    /**
     * Update config
     * PUT /api/v1/database-config/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<DatabaseSourceConfig> update(
            @PathVariable Long id,
            @RequestBody DatabaseSourceConfig config) {

        log.info("Update database config: {}", id);

        DatabaseSourceConfig updated = service.update(id, config);

        return ResponseEntity.ok(updated);
    }

    /**
     * Xóa config (soft delete)
     * DELETE /api/v1/database-config/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        log.info("Delete database config: {}", id);

        service.delete(id);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Database config deleted: " + id
        ));
    }

    /**
     * Test connection
     * POST /api/v1/database-config/{configCode}/test-connection
     */
    @PostMapping("/{configCode}/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String configCode) {
        log.info("Test connection for: {}", configCode);

        boolean success = service.testConnection(configCode);

        return ResponseEntity.ok(Map.of(
                "configCode", configCode,
                "connectionSuccess", success,
                "message", success ? "Connection successful" : "Connection failed"
        ));
    }
}
