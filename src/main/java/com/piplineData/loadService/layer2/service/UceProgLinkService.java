package com.piplineData.loadService.layer2.service;

import com.piplineData.loadService.layer2.dto.ProcessCreationResult;
import com.piplineData.loadService.layer2.dto.UceProgLinkRequest;
import com.piplineData.loadService.layer2.dto.UceProgLinkResponse;
import com.piplineData.loadService.layer2.entity.AirflowConnection;
import com.piplineData.loadService.layer2.entity.UceProg;
import com.piplineData.loadService.layer2.entity.UceProgPeriod;
import com.piplineData.loadService.layer2.entity.UceProgUseMetaProcess;
import com.piplineData.loadService.layer2.repository.AirflowConnectionRepository;
import com.piplineData.loadService.layer2.repository.UceProgPeriodRepository;
import com.piplineData.loadService.layer2.repository.UceProgRepository;
import com.piplineData.loadService.layer2.repository.UceProgUseMetaProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Service quản lý liên kết Chương trình-Meta Process
 * Tự động tạo Process Instance và sync DAG
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UceProgLinkService {

    private final UceProgUseMetaProcessRepository linkRepository;
    private final UceProgRepository progRepository;
    private final UceProgPeriodRepository periodRepository;
    private final UceProcessService processService;
    private final AirflowIntegrationService airflowService;
    private final AirflowConnectionRepository airflowConnectionRepository;

    /**
     * Liên kết Program/Period với Meta Process
     * Tự động tạo Process Instance và sync DAG
     * Transaction tách riêng để commit process trước khi sync DAG
     */
    public UceProgLinkResponse linkAndExecute(UceProgLinkRequest request) {
        log.info("Linking Program {} / Period {} with Meta Process {}", 
                request.getProgId(), request.getPeriodId(), request.getMetaProcCode());

        // 1. Validate Program và Period tồn tại
        UceProg prog = progRepository.findById(request.getProgId())
                .orElseThrow(() -> new RuntimeException("Program not found: " + request.getProgId()));
        
        UceProgPeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Period not found: " + request.getPeriodId()));

        // 2. Check if same configuration already exists (compare useVar)
        linkRepository.findByProgIdAndPeriodIdAndMetaProcCode(
                request.getProgId(), request.getPeriodId(), request.getMetaProcCode()
        ).ifPresent(existing -> {
            // Compare useVar to determine if it's truly duplicate
            if (isSameUseVar(existing.getUseVar(), request.getUseVar())) {
                throw new RuntimeException(String.format(
                        "Process already exists for this configuration: Program %d / Period %d / MetaProcess %s",
                        request.getProgId(), request.getPeriodId(), request.getMetaProcCode()));
            }
            // Different useVar -> delete old link and create new one
            log.info("UseVar changed, removing old link {} and creating new configuration", existing.getProgUseMpId());
            linkRepository.delete(existing);
        });

        // 3. Tạo Process Instance (gọi lại logic existing /api/v2/process/create)
        Map<String, Object> businessParams = new HashMap<>();
        if (request.getUseVar() != null) {
            businessParams.put("useVar", request.getUseVar());
        }
        businessParams.put("progName", prog.getProgName());
        businessParams.put("periodName", period.getPeriodName());

        ProcessCreationResult processResult = processService.createProcessFromMeta(
                request.getMetaProcCode(),
                request.getProgId(),
                request.getPeriodId(),
                businessParams
        );

        log.info("Process created: {} with ID {}", processResult.getProcCode(), processResult.getProcId());

        // 4. Sync Process to DAG (gọi lại logic existing /api/v2/airflow/sync-process-to-dag)
        // Note: Không dùng @Transactional ở method này để process đã commit trước khi sync
        Map<String, Object> dagSyncResult;
        try {
            dagSyncResult = airflowService.syncProcessToDag(
                    processResult.getProcCode(),
                    request.getConnectionName(),
                    request.getDagDirectory()
            );
        } catch (Exception e) {
            log.error("Failed to sync DAG, but process already created: {}", processResult.getProcCode(), e);
            throw new RuntimeException("Process DAG sync failed: " + e.getMessage(), e);
        }

        String dagId = (String) dagSyncResult.get("dagId");
        log.info("DAG synced: {}", dagId);

        // 5. Tạo Link record trong uce_prog_use_meta_process (trong transaction riêng)
        return saveLinkRecord(request, processResult, dagSyncResult, dagId);
    }

    /**
     * Lưu link record trong transaction riêng
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected UceProgLinkResponse saveLinkRecord(
            UceProgLinkRequest request,
            ProcessCreationResult processResult,
            Map<String, Object> dagSyncResult,
            String dagId) {
        
        // Lấy connectionId từ connectionName
        AirflowConnection connection = airflowConnectionRepository.findByConnectionName(request.getConnectionName())
                .orElseThrow(() -> new RuntimeException("Airflow connection not found: " + request.getConnectionName()));
        
        UceProgUseMetaProcess link = UceProgUseMetaProcess.builder()
                .progId(request.getProgId())
                .periodId(request.getPeriodId())
                .metaProcCode(request.getMetaProcCode())
                .useVar(request.getUseVar())
                .dagId(dagId)
                .connectionId(connection.getConnectionId())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        UceProgUseMetaProcess savedLink = linkRepository.save(link);
        log.info("Link created successfully with ID: {}", savedLink.getProgUseMpId());

        // 6. Trả về kết quả
        return UceProgLinkResponse.builder()
                .progUseMpId(savedLink.getProgUseMpId())
                .progId(savedLink.getProgId())
                .periodId(savedLink.getPeriodId())
                .metaProcCode(savedLink.getMetaProcCode())
                .processId(processResult.getProcId())
                .processCode(processResult.getProcCode())
                .dagId(dagId)
                .connectionName(request.getConnectionName())
                .message(String.format(
                        "Successfully linked and created Process '%s' with DAG '%s'",
                        processResult.getProcCode(), dagId))
                .build();
    }

    /**
     * So sánh 2 useVar JsonNode
     */
    private boolean isSameUseVar(com.fasterxml.jackson.databind.JsonNode existing, 
                                  com.fasterxml.jackson.databind.JsonNode requested) {
        if (existing == null && requested == null) {
            return true;
        }
        if (existing == null || requested == null) {
            return false;
        }
        return existing.equals(requested);
    }

    /**
     * Lấy danh sách liên kết theo Program
     */
    public java.util.List<UceProgUseMetaProcess> getLinksByProgram(Long progId) {
        return linkRepository.findByProgId(progId);
    }

    /**
     * Lấy danh sách liên kết theo Period
     */
    public java.util.List<UceProgUseMetaProcess> getLinksByPeriod(Long periodId) {
        return linkRepository.findByPeriodId(periodId);
    }
}
