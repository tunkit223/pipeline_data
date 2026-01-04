package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceTaskRepository extends JpaRepository<UceTask, Long> {
    
    Optional<UceTask> findByTaskCode(String taskCode);
    
    List<UceTask> findByProcCode(String procCode);
    
    List<UceTask> findByMetaProcCode(String metaProcCode);
    
    List<UceTask> findByCalcProgIdAndCalcPeriodId(Long calcProgId, Long calcPeriodId);
    
    List<UceTask> findByStatus(String status);
}
