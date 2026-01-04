package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceProgPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UceProgPeriodRepository extends JpaRepository<UceProgPeriod, Long> {
    
    Optional<UceProgPeriod> findByPeriodName(String periodName);
    
    List<UceProgPeriod> findByProgId(Long progId);
    
    List<UceProgPeriod> findByStatus(String status);
    
    List<UceProgPeriod> findByStartDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    boolean existsByPeriodName(String periodName);
}
