package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceTaskExec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceTaskExecRepository extends JpaRepository<UceTaskExec, Long> {
    
    Optional<UceTaskExec> findByTaskExecCode(String taskExecCode);
    
    List<UceTaskExec> findByProcExecCode(String procExecCode);
    
    List<UceTaskExec> findByTaskCode(String taskCode);
    
    List<UceTaskExec> findByProcCode(String procCode);
    
    List<UceTaskExec> findByStatus(String status);
    
    List<UceTaskExec> findByCalcProgIdAndCalcPeriodId(Long calcProgId, Long calcPeriodId);
    
    Optional<UceTaskExec> findTopByTaskCodeOrderByTaskExecIdDesc(String taskCode);
}
