package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.entity.UceProcess;
import com.piplineData.loadService.layer2.service.UceProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller để quản lý Process
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/process")
@RequiredArgsConstructor
public class UceProcessController {

    private final UceProcessService processService;

    /**
     * Create Process từ Meta Process
     * POST /api/v2/process/create
     */
    @PostMapping("/create")
    public ResponseEntity<ProcessCreationResult> createProcess(@RequestBody Map<String, Object> request) {
        log.info("Creating Process from Meta Process");

        String metaProcCode = (String) request.get("metaProcCode");
        Long calcProgId = request.get("calcProgId") != null 
            ? Long.valueOf(request.get("calcProgId").toString()) 
            : null;
        Long calcPeriodId = request.get("calcPeriodId") != null 
            ? Long.valueOf(request.get("calcPeriodId").toString()) 
            : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> businessParams = (Map<String, Object>) request.getOrDefault("businessParams", new HashMap<>());

        ProcessCreationResult result = processService.createProcessFromMeta(
            metaProcCode, calcProgId, calcPeriodId, businessParams);

        return ResponseEntity.ok(result);
    }

    /**
     * Get Process by code
     * GET /api/v2/process/{procCode}
     */
    @GetMapping("/{procCode}")
    public ResponseEntity<UceProcess> getProcess(@PathVariable String procCode) {
        log.info("Getting Process: {}", procCode);
        UceProcess process = processService.getProcess(procCode);
        return ResponseEntity.ok(process);
    }

    /**
     * Get all Processes by Meta Process
     * GET /api/v2/process/by-meta/{metaProcCode}
     */
    @GetMapping("/by-meta/{metaProcCode}")
    public ResponseEntity<List<UceProcess>> getProcessesByMetaProc(@PathVariable String metaProcCode) {
        log.info("Getting Processes for Meta Process: {}", metaProcCode);
        List<UceProcess> processes = processService.getProcessesByMetaProc(metaProcCode);
        return ResponseEntity.ok(processes);
    }

    /**
     * Update Process status
     * PUT /api/v2/process/{procCode}/status
     */
    @PutMapping("/{procCode}/status")
    public ResponseEntity<Void> updateProcessStatus(
            @PathVariable String procCode,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        log.info("Updating Process {} status to {}", procCode, status);
        processService.updateProcessStatus(procCode, status);
        return ResponseEntity.ok().build();
    }
}
