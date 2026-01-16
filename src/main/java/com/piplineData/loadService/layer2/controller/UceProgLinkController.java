package com.piplineData.loadService.layer2.controller;

import com.piplineData.loadService.layer2.dto.UceProgLinkRequest;
import com.piplineData.loadService.layer2.dto.UceProgLinkResponse;
import com.piplineData.loadService.layer2.entity.UceProgUseMetaProcess;
import com.piplineData.loadService.layer2.service.UceProgLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API quản lý liên kết Chương trình-Meta Process
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/programs/link")
@RequiredArgsConstructor
public class UceProgLinkController {

    private final UceProgLinkService linkService;

    /**
     * API 3: Liên kết Program/Period với Meta Process
     * Tự động tạo Process Instance và sync DAG
     * POST /api/v2/programs/link
     */
    @PostMapping
    public ResponseEntity<UceProgLinkResponse> linkProgramToMetaProcess(
            @RequestBody UceProgLinkRequest request) {
        log.info("Received request to link Program {} / Period {} with Meta Process {}", 
                request.getProgId(), request.getPeriodId(), request.getMetaProcCode());
        
        UceProgLinkResponse response = linkService.linkAndExecute(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh sách liên kết theo Program
     */
    @GetMapping("/by-program/{progId}")
    public ResponseEntity<List<UceProgUseMetaProcess>> getLinksByProgram(@PathVariable Long progId) {
        List<UceProgUseMetaProcess> links = linkService.getLinksByProgram(progId);
        return ResponseEntity.ok(links);
    }

    /**
     * Lấy danh sách liên kết theo Period
     */
    @GetMapping("/by-period/{periodId}")
    public ResponseEntity<List<UceProgUseMetaProcess>> getLinksByPeriod(@PathVariable Long periodId) {
        List<UceProgUseMetaProcess> links = linkService.getLinksByPeriod(periodId);
        return ResponseEntity.ok(links);
    }
}
