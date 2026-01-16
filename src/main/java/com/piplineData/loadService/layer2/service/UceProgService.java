package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.UceProgRequest;
import com.piplineData.loadService.layer2.entity.UceProg;
import com.piplineData.loadService.layer2.repository.UceProgRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service quản lý Chương trình (uce_prog)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceProgService {

    private final UceProgRepository uceProgRepository;

    /**
     * Tạo mới một Chương trình
     */
    @Transactional
    public UceProg createProgram(UceProgRequest request) {
        log.info("Creating program: {}", request.getProgName());

        // Validate unique progName
        if (uceProgRepository.findByProgName(request.getProgName()).isPresent()) {
            throw new RuntimeException("Program name already exists: " + request.getProgName());
        }

        UceProg prog = UceProg.builder()
                .progName(request.getProgName())
                .companyId(request.getCompanyId())
                .brandId(request.getBrandId())
                .departmentId(request.getDepartmentId())
                .progNote(request.getProgNote())
                .status(request.getStatus() != null ? request.getStatus() : "DECLARED")
                .progType(request.getProgType() != null ? request.getProgType() : "NOTARGET")
                .progSpec(request.getProgSpec())
                .build();

        UceProg saved = uceProgRepository.save(prog);
        log.info("Program created successfully with ID: {}", saved.getProgId());
        return saved;
    }

    /**
     * Lấy thông tin chương trình theo ID
     */
    public UceProg getProgramById(Long progId) {
        return uceProgRepository.findById(progId)
                .orElseThrow(() -> new RuntimeException("Program not found: " + progId));
    }

    /**
     * Lấy danh sách chương trình theo công ty
     */
    public List<UceProg> getProgramsByCompany(Long companyId) {
        return uceProgRepository.findByCompanyId(companyId);
    }

    /**
     * Lấy danh sách chương trình theo trạng thái
     */
    public List<UceProg> getProgramsByStatus(String status) {
        return uceProgRepository.findByStatus(status);
    }
}
