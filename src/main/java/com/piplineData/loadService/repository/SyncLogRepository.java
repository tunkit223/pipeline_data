package com.piplineData.loadService.repository;

import com.piplineData.loadService.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, String> {
    List<SyncLog> findByDataObjCodeOrderByStartedAtDesc(String dataObjCode);
    List<SyncLog> findByStatusOrderByStartedAtDesc(String status);
    List<SyncLog> findAllByOrderByStartedAtDesc();
}
