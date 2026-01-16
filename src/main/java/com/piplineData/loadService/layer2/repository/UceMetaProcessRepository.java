package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceMetaProcessRepository extends JpaRepository<UceMetaProcess, String> {
    
    Optional<UceMetaProcess> findByMetaProcCode(String metaProcCode);
    
    List<UceMetaProcess> findByCompanyId(Long companyId);
    
    List<UceMetaProcess> findByCompanyIdAndBrandId(Long companyId, Long brandId);
    
    List<UceMetaProcess> findByIsActive(Boolean isActive);
    
    boolean existsByMetaProcName(String metaProcName);
}
