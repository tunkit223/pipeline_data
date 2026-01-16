package com.piplineData.loadService.layer2.service;

/**
 * DDL Templates for Metadata Tables
 * Used to create metadata tables in dynamic schemas
 */
public class MetadataTableDDLs {

    public static String[] getCreateTableDDLs(String schema) {
        return new String[] {
            // 1. uce_meta_process
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_meta_process (
                    meta_proc_code VARCHAR(250) PRIMARY KEY,
                    meta_proc_name VARCHAR(250) NOT NULL UNIQUE,
                    company_id BIGINT,
                    brand_id BIGINT,
                    department_id BIGINT,
                    meta_proc_note TEXT,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    metadata_schema VARCHAR(100) NOT NULL,
                    schedule_interval VARCHAR(100)
                )
                """, schema),

            // 2. uce_meta_task
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_meta_task (
                    meta_task_code VARCHAR(250) PRIMARY KEY,
                    meta_task_name VARCHAR(250) NOT NULL UNIQUE,
                    meta_task_type VARCHAR(100),
                    meta_proc_code VARCHAR(250) NOT NULL,
                    pre_meta_task_codelist TEXT,
                    post_meta_task_codelist TEXT,
                    selector TEXT,
                    processor TEXT,
                    insertor TEXT,
                    meta_task_note TEXT,
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    is_starting BOOLEAN DEFAULT false,
                    is_ending BOOLEAN DEFAULT false,
                    task_order INTEGER,
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code)
                )
                """, schema, schema),

            // 3. uce_process
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_process (
                    proc_id BIGSERIAL PRIMARY KEY,
                    proc_code VARCHAR(250) NOT NULL UNIQUE,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    proc_note TEXT,
                    status VARCHAR(100) DEFAULT 'READY',
                    is_lasted BOOLEAN NOT NULL DEFAULT true,
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code)
                )
                """, schema, schema),

            // 4. uce_task
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_task (
                    task_id BIGSERIAL PRIMARY KEY,
                    task_code VARCHAR(250) NOT NULL UNIQUE,
                    proc_code VARCHAR(250) NOT NULL,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    selector_biz TEXT,
                    processor_biz TEXT,
                    insertor_biz TEXT,
                    task_note TEXT,
                    status VARCHAR(100) DEFAULT 'READY',
                    FOREIGN KEY (proc_code) REFERENCES %s.uce_process(proc_code),
                    FOREIGN KEY (meta_proc_code) REFERENCES %s.uce_meta_process(meta_proc_code)
                )
                """, schema, schema, schema),

            // 5. uce_proc_exec
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_proc_exec (
                    proc_exec_id BIGSERIAL PRIMARY KEY,
                    proc_exec_code VARCHAR(250) NOT NULL UNIQUE,
                    proc_code VARCHAR(250) NOT NULL,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    status VARCHAR(100) DEFAULT 'RUNNING',
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    proc_exec_note TEXT,
                    FOREIGN KEY (proc_code) REFERENCES %s.uce_process(proc_code)
                )
                """, schema, schema),

            // 6. uce_task_exec
            String.format("""
                CREATE TABLE IF NOT EXISTS %s.uce_task_exec (
                    task_exec_id BIGSERIAL PRIMARY KEY,
                    task_exec_code VARCHAR(250) NOT NULL UNIQUE,
                    proc_exec_code VARCHAR(250) NOT NULL,
                    task_code VARCHAR(250) NOT NULL,
                    proc_code VARCHAR(250) NOT NULL,
                    meta_proc_code VARCHAR(250) NOT NULL,
                    calc_prog_id BIGINT,
                    calc_period_id BIGINT,
                    selector_biz_ctrl TEXT,
                    processor_biz_ctrl TEXT,
                    insertor_biz_ctrl TEXT,
                    status VARCHAR(100) DEFAULT 'RUNNING',
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    task_exec_note TEXT,
                    FOREIGN KEY (proc_exec_code) REFERENCES %s.uce_proc_exec(proc_exec_code),
                    FOREIGN KEY (task_code) REFERENCES %s.uce_task(task_code)
                )
                """, schema, schema, schema)
        };
    }

    public static String[] getCreateIndexDDLs(String schema) {
        return new String[] {
            String.format("CREATE INDEX IF NOT EXISTS idx_meta_task_proc ON %s.uce_meta_task(meta_proc_code)", schema),
            String.format("CREATE INDEX IF NOT EXISTS idx_process_meta ON %s.uce_process(meta_proc_code)", schema),
            String.format("CREATE INDEX IF NOT EXISTS idx_task_proc ON %s.uce_task(proc_code)", schema),
            String.format("CREATE INDEX IF NOT EXISTS idx_proc_exec_proc ON %s.uce_proc_exec(proc_code)", schema),
            String.format("CREATE INDEX IF NOT EXISTS idx_task_exec_proc ON %s.uce_task_exec(proc_exec_code)", schema)
        };
    }
}
