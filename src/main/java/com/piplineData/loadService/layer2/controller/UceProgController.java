package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.UceProgRequest;
import com.piplineData.loadService.layer2.entity.UceProg;
import com.piplineData.loadService.layer2.service.UceProgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API quản lý Chương trình (uce_prog)
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/programs")
@RequiredArgsConstructor
public class UceProgController {

    private final UceProgService progService;

    /**
     * API 1: Tạo mới chương trình
     * POST /api/v2/programs
     */
    @PostMapping
    public ResponseEntity<UceProg> createProgram(@RequestBody UceProgRequest request) {
        log.info("Received request to create program: {}", request.getProgName());
        UceProg created = progService.createProgram(request);
        return ResponseEntity.ok(created);
    }

    /**
     * Lấy thông tin chương trình theo ID
     */
    @GetMapping("/{progId}")
    public ResponseEntity<UceProg> getProgram(@PathVariable Long progId) {
        UceProg prog = progService.getProgramById(progId);
        return ResponseEntity.ok(prog);
    }

    /**
     * Lấy danh sách chương trình theo công ty
     */
    @GetMapping("/by-company/{companyId}")
    public ResponseEntity<List<UceProg>> getProgramsByCompany(@PathVariable Long companyId) {
        List<UceProg> programs = progService.getProgramsByCompany(companyId);
        return ResponseEntity.ok(programs);
    }

    /**
     * Lấy danh sách chương trình theo trạng thái
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<List<UceProg>> getProgramsByStatus(@PathVariable String status) {
        List<UceProg> programs = progService.getProgramsByStatus(status);
        return ResponseEntity.ok(programs);
    }
}
