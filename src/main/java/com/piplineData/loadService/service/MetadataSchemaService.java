package com.piplineData.loadService.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataSchemaService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Đảm bảo schema và các metadata tables tồn tại
     * Nếu chưa có sẽ tự động tạo
     * Note: Sử dụng REQUIRES_NEW để DDL statements không bị rollback cùng transaction cha
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void ensureMetadataSchema(String schemaName) {
        log.info("Ensuring metadata schema exists: {}", schemaName);
        
        try {
            // 1. Tạo schema nếu chưa có
            createSchemaIfNotExists(schemaName);
            
            // 2. Tạo các metadata tables
            createMetadataTables(schemaName);
            
            log.info("Metadata schema ready: {}", schemaName);
        } catch (Exception e) {
            log.error("Failed to ensure metadata schema: {}", schemaName, e);
            throw new RuntimeException("Failed to create metadata schema: " + schemaName, e);
        }
    }

    private void createSchemaIfNotExists(String schemaName) {
        String checkSchemaSQL = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
        Integer count = jdbcTemplate.queryForObject(checkSchemaSQL, Integer.class, schemaName);
        
        if (count == null || count == 0) {
            log.info("Creating schema: {}", schemaName);
            jdbcTemplate.execute("CREATE SCHEMA " + schemaName);
        } else {
            log.info("Schema already exists: {}", schemaName);
        }
    }

    private void createMetadataTables(String schemaName) {
        List<String> ddls = List.of(
            // uce_meta_process
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_meta_process (
                    meta_proc_code VARCHAR(250) PRIMARY KEY,
                    meta_proc_name VARCHAR(500) NOT NULL UNIQUE,
                    company_id BIGINT,
                    brand_id BIGINT,
                    department_id BIGINT,
                    meta_proc_note TEXT,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    metadata_schema VARCHAR(100) NOT NULL,
                    schedule_interval VARCHAR(50)
                )
                """, schemaName),
            
            // uce_meta_task
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_meta_task (
                    meta_task_code VARCHAR(250) PRIMARY KEY,
                    meta_task_name VARCHAR(500) NOT NULL UNIQUE,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    task_order INTEGER,
                    meta_task_type VARCHAR(100),
                    pre_meta_task_codelist TEXT,
                    post_meta_task_codelist TEXT,
                    selector TEXT,
                    processor TEXT,
                    insertor TEXT,
                    meta_task_note TEXT,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    is_starting BOOLEAN DEFAULT false,
                    is_ending BOOLEAN DEFAULT false,
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code) ON DELETE CASCADE
                )
                """, schemaName, schemaName),
            
            // uce_process
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_process (
                    id BIGSERIAL PRIMARY KEY,
                    proc_id BIGINT,
                    proc_code VARCHAR(250) UNIQUE,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    process_instance_code VARCHAR(100) NOT NULL UNIQUE,
                    process_instance_name VARCHAR(255),
                    runtime_params TEXT,
                    status VARCHAR(50) DEFAULT 'CREATED',
                    dest_schema VARCHAR(100),
                    is_lasted BOOLEAN DEFAULT true,
                    proc_note TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code)
                )
                """, schemaName, schemaName),
            
            // uce_task
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_task (
                    task_id BIGSERIAL PRIMARY KEY,
                    task_code VARCHAR(250) NOT NULL UNIQUE,
                    proc_code VARCHAR(250) NOT NULL,
                    meta_task_code VARCHAR(250) NOT NULL,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    selector_biz TEXT,
                    processor_biz TEXT,
                    insertor_biz TEXT,
                    task_note TEXT,
                    status VARCHAR(100) DEFAULT 'READY',
                    FOREIGN KEY (proc_code) REFERENCES %s.uce_process(proc_code),
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code),
                    FOREIGN KEY (meta_task_code) REFERENCES %s.uce_meta_task(meta_task_code)
                )
                """, schemaName, schemaName, schemaName, schemaName),
            
            // uce_proc_exec
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_proc_exec (
                    id BIGSERIAL PRIMARY KEY,
                    process_id BIGINT NOT NULL,
                    execution_params TEXT,
                    status VARCHAR(50) DEFAULT 'RUNNING',
                    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP,
                    error_message TEXT,
                    FOREIGN KEY (process_id) REFERENCES %s.uce_process(id)
                )
                """, schemaName, schemaName),
            
            // uce_task_exec
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_task_exec (
                    id BIGSERIAL PRIMARY KEY,
                    proc_exec_id BIGINT NOT NULL,
                    task_id BIGINT NOT NULL,
                    status VARCHAR(50) DEFAULT 'RUNNING',
                    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP,
                    rows_affected INTEGER,
                    error_message TEXT,
                    FOREIGN KEY (proc_exec_id) REFERENCES %s.uce_proc_exec(id),
                    FOREIGN KEY (task_id) REFERENCES %s.uce_task(id)
                )
                """, schemaName, schemaName, schemaName)
        );

        for (String ddl : ddls) {
            try {
                jdbcTemplate.execute(ddl);
                log.debug("Executed DDL in schema {}", schemaName);
            } catch (Exception e) {
                log.error("Failed to create table in schema {}: {}", schemaName, e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Kiểm tra schema có tồn tại không
     */
    public boolean schemaExists(String schemaName) {
        String checkSchemaSQL = "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?";
        Integer count = jdbcTemplate.queryForObject(checkSchemaSQL, Integer.class, schemaName);
        return count != null && count > 0;
    }
}
