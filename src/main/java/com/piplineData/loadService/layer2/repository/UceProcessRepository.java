package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceProcessRepository extends JpaRepository<UceProcess, Long> {
    
    Optional<UceProcess> findByProcCode(String procCode);
    
    List<UceProcess> findByMetaProcCode(String metaProcCode);
    
    List<UceProcess> findByCalcProgId(Long calcProgId);
    
    List<UceProcess> findByCalcProgIdAndCalcPeriodId(Long calcProgId, Long calcPeriodId);
    
    List<UceProcess> findByStatus(String status);
    
    Optional<UceProcess> findTopByMetaProcCodeOrderByProcIdDesc(String metaProcCode);
    
    List<UceProcess> findByIsLasted(Boolean isLasted);
}
