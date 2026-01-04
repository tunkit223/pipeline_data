package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceMetaTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceMetaTaskRepository extends JpaRepository<UceMetaTask, String> {
    
    Optional<UceMetaTask> findByMetaTaskCode(String metaTaskCode);
    
    List<UceMetaTask> findByMetaProcCode(String metaProcCode);
    
    List<UceMetaTask> findByMetaProcCodeOrderByTaskOrder(String metaProcCode);
    
    List<UceMetaTask> findByMetaProcCodeAndIsActive(String metaProcCode, Boolean isActive);
    
    List<UceMetaTask> findByMetaProcCodeAndIsStarting(String metaProcCode, Boolean isStarting);
    
    List<UceMetaTask> findByMetaProcCodeAndIsEnding(String metaProcCode, Boolean isEnding);
    
    boolean existsByMetaTaskName(String metaTaskName);
}
