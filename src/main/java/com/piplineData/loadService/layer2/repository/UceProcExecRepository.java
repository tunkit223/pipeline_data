package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceProcExec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceProcExecRepository extends JpaRepository<UceProcExec, Long> {
    
    Optional<UceProcExec> findByProcExecCode(String procExecCode);
    
    List<UceProcExec> findByProcCode(String procCode);
    
    List<UceProcExec> findByMetaProcCode(String metaProcCode);
    
    List<UceProcExec> findByStatus(String status);
    
    List<UceProcExec> findByCalcProgIdAndCalcPeriodId(Long calcProgId, Long calcPeriodId);
    
    Optional<UceProcExec> findTopByProcCodeOrderByProcExecIdDesc(String procCode);
}
