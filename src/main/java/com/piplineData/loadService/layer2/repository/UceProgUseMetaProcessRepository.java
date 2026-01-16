package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceProgUseMetaProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceProgUseMetaProcessRepository extends JpaRepository<UceProgUseMetaProcess, Long> {
    
    List<UceProgUseMetaProcess> findByProgId(Long progId);
    
    List<UceProgUseMetaProcess> findByPeriodId(Long periodId);
    
    List<UceProgUseMetaProcess> findByMetaProcCode(String metaProcCode);
    
    Optional<UceProgUseMetaProcess> findByProgIdAndPeriodIdAndMetaProcCode(
            Long progId, Long periodId, String metaProcCode);
    
    List<UceProgUseMetaProcess> findByIsActive(Boolean isActive);
    
    Optional<UceProgUseMetaProcess> findByDagId(String dagId);
}
