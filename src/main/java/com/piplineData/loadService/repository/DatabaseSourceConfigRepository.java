package com.piplineData.loadService.repository;

import com.piplineData.loadService.entity.DatabaseSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseSourceConfigRepository extends JpaRepository<DatabaseSourceConfig, Long> {

    Optional<DatabaseSourceConfig> findByConfigCode(String configCode);

    Optional<DatabaseSourceConfig> findByConfigCodeAndIsActiveTrue(String configCode);

    List<DatabaseSourceConfig> findAllByIsActiveTrue();

    boolean existsByConfigCode(String configCode);
}
