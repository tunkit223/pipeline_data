package com.piplineData.loadService.layer2.repository;

import com.piplineData.loadService.layer2.entity.UceProg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UceProgRepository extends JpaRepository<UceProg, Long> {
    
    Optional<UceProg> findByProgName(String progName);
    
    List<UceProg> findByCompanyId(Long companyId);
    
    List<UceProg> findByCompanyIdAndBrandId(Long companyId, Long brandId);
    
    List<UceProg> findByStatus(String status);
    
    List<UceProg> findByProgType(String progType);
    
    boolean existsByProgName(String progName);
}
