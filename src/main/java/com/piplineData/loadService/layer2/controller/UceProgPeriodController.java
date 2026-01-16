package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.UceProgPeriodRequest;
import com.piplineData.loadService.layer2.entity.UceProgPeriod;
import com.piplineData.loadService.layer2.service.UceProgPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API quản lý Chu kỳ chương trình (uce_prog_period)
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/programs/{progId}/periods")
@RequiredArgsConstructor
public class UceProgPeriodController {

    private final UceProgPeriodService periodService;

    /**
     * API 2: Tạo mới chu kỳ cho chương trình
     * POST /api/v2/programs/{progId}/periods
     */
    @PostMapping
    public ResponseEntity<UceProgPeriod> createPeriod(
            @PathVariable Long progId,
            @RequestBody UceProgPeriodRequest request) {
        log.info("Received request to create period for program {}: {}", progId, request.getPeriodName());
        UceProgPeriod created = periodService.createPeriod(progId, request);
        return ResponseEntity.ok(created);
    }

    /**
     * Lấy thông tin chu kỳ theo ID
     */
    @GetMapping("/{periodId}")
    public ResponseEntity<UceProgPeriod> getPeriod(
            @PathVariable Long progId,
            @PathVariable Long periodId) {
        UceProgPeriod period = periodService.getPeriodById(periodId);
        return ResponseEntity.ok(period);
    }

    /**
     * Lấy danh sách chu kỳ của một chương trình
     */
    @GetMapping
    public ResponseEntity<List<UceProgPeriod>> getPeriodsByProgram(@PathVariable Long progId) {
        List<UceProgPeriod> periods = periodService.getPeriodsByProgram(progId);
        return ResponseEntity.ok(periods);
    }
}
