package com.piplineData.loadService.repository;

import com.piplineData.loadService.entity.DataObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataObjectRepository extends JpaRepository<DataObject, Integer> {
    Optional<DataObject> findByDataObjCode(String dataObjCode);
    Optional<DataObject> findByDataObjCodeAndIsActiveTrue(String dataObjCode);
}
