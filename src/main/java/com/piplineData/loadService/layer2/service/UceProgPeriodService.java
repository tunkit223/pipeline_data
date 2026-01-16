package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.UceProgPeriodRequest;
import com.piplineData.loadService.layer2.entity.UceProg;
import com.piplineData.loadService.layer2.entity.UceProgPeriod;
import com.piplineData.loadService.layer2.repository.UceProgPeriodRepository;
import com.piplineData.loadService.layer2.repository.UceProgRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service quản lý Chu kỳ chương trình (uce_prog_period)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceProgPeriodService {

    private final UceProgPeriodRepository periodRepository;
    private final UceProgRepository progRepository;

    /**
     * Tạo mới một Chu kỳ cho chương trình
     */
    @Transactional
    public UceProgPeriod createPeriod(Long progId, UceProgPeriodRequest request) {
        log.info("Creating period for program {}: {}", progId, request.getPeriodName());

        // Validate program exists
        UceProg prog = progRepository.findById(progId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + progId));

        // Validate unique periodName
        if (periodRepository.findByPeriodName(request.getPeriodName()).isPresent()) {
            throw new RuntimeException("Period name already exists: " + request.getPeriodName());
        }

        UceProgPeriod period = UceProgPeriod.builder()
                .periodName(request.getPeriodName())
                .progId(progId)
                .periodSpec(request.getPeriodSpec())
                .execMode(request.getExecMode())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .build();

        UceProgPeriod saved = periodRepository.save(period);
        log.info("Period created successfully with ID: {}", saved.getPeriodId());
        return saved;
    }

    /**
     * Lấy thông tin chu kỳ theo ID
     */
    public UceProgPeriod getPeriodById(Long periodId) {
        return periodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found: " + periodId));
    }

    /**
     * Lấy danh sách chu kỳ của một chương trình
     */
    public List<UceProgPeriod> getPeriodsByProgram(Long progId) {
        return periodRepository.findByProgId(progId);
    }

    /**
     * Lấy danh sách chu kỳ theo trạng thái
     */
    public List<UceProgPeriod> getPeriodsByStatus(String status) {
        return periodRepository.findByStatus(status);
    }
}
