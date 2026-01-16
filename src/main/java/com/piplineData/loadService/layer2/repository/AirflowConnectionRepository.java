package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.AirflowConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AirflowConnectionRepository extends JpaRepository<AirflowConnection, Long> {
    
    Optional<AirflowConnection> findByConnectionName(String connectionName);
    
    List<AirflowConnection> findByIsActive(Boolean isActive);
    
    boolean existsByConnectionName(String connectionName);
}
