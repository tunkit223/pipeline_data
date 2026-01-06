package com.piplineData.loadService.layer2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Service to generate dynamic DAG files for each Process
 * Phase 2: One Process → One DAG file + One Variable
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagFileGeneratorService {

    /**
     * Generate DAG file for a Process
     * 
     * @param dagDirectory Airflow DAGs directory path (e.g., "e:/nam3/DA1/pipline_data/airflow/dags")
     * @param procCode Process code (e.g., PROC_STUDENT_ANALYTICS_2026_202601)
     * @param metaProcCode Meta Process code (e.g., STUDENT_ANALYTICS_2026)
     * @param calcProgId Calculation Program ID
     * @param calcPeriodId Calculation Period ID
     * @param scheduleInterval Airflow schedule (e.g., "@daily", null for manual)
     * @param tasks List of task definitions
     * @return Path to generated DAG file
     */
    public Path generateDagFile(
            String dagDirectory,
            String procCode, 
            String metaProcCode,
            Long calcProgId,
            Long calcPeriodId,
            String scheduleInterval,
            List<Map<String, Object>> tasks) {
        
        log.info("Generating DAG file for Process: {}", procCode);

        // DAG ID format: uce_{meta_proc_code}_{calc_prog_id}_{calc_period_id}
        String dagId = String.format("uce_%s_%s_%s", 
            metaProcCode.toLowerCase(),
            calcProgId != null ? calcProgId : "0",
            calcPeriodId != null ? calcPeriodId : "0");
        
        // Variable name format: uce_var_{proc_code}
        String variableName = String.format("uce_var_%s", procCode.toLowerCase());
        
        // File name: {dag_id}.py
        String fileName = dagId + ".py";
        Path dagFilePath = Paths.get(dagDirectory, fileName);

        try {
            String dagContent = generateDagContent(dagId, variableName, metaProcCode, scheduleInterval);
            
            // Create directory if not exists
            Files.createDirectories(Paths.get(dagDirectory));
            
            // Check if DAG file already exists
            if (Files.exists(dagFilePath)) {
                log.warn("⚠️ DAG file already exists: {}. Will be overwritten.", dagFilePath);
            }
            
            // Write DAG file
            Files.writeString(dagFilePath, dagContent, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
            
            log.info("✅ Generated DAG file: {}", dagFilePath);
            log.info("   DAG ID: {}", dagId);
            log.info("   Variable: {}", variableName);
            log.info("   Schedule: {}", scheduleInterval != null ? scheduleInterval : "None (manual)");
            
            return dagFilePath;
            
        } catch (IOException e) {
            log.error("Failed to generate DAG file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate DAG file: " + e.getMessage(), e);
        }
    }

    /**
     * Generate DAG Python content based on template
     */
    private String generateDagContent(String dagId, String variableName, String metaProcCode, String scheduleInterval) {
        String scheduleValue = scheduleInterval != null ? "'" + scheduleInterval + "'" : "None";
        
        return String.format("""
from airflow import DAG
from airflow.models import Variable
from airflow.providers.http.hooks.http import HttpHook
from airflow.utils.dates import days_ago
from airflow.operators.python import PythonOperator
from airflow.operators.dummy import DummyOperator
import json

default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'start_date': days_ago(1),
    'retries': 0
}

def call_http_task(task_def, process_code, http_conn_id, company_id, brand_id, calculated_prog_id, calculated_period_id, meta_process_code):
    \"\"\"Thực hiện gọi API HTTP.\"\"\"
    def _inner(**context):
        dag_conf = context.get("dag_run").conf or {}
        process_exec_code = dag_conf.get("process_exec_code", process_code)
        
        # 🧩 Kiểm tra process_code trong cấu hình có khớp với process_code truyền vào hay không
        process_code_on_config = process_exec_code.split("_E", 1)[0]
        if process_code_on_config != process_code:
            print(f"⚠️ [WARNING] Đang chạy process_code từ config runtime: {process_code_on_config} "
                  f"→ KHÔNG khớp với process_code hiện tại trong var: {process_code}")
        else:
            print(f"ℹ️ [INFO] Chạy đúng process_code: {process_code}")
            
        http = HttpHook(http_conn_id=http_conn_id, method='POST')
        payload = {
              "company_id": company_id,
              "brand_id": brand_id,
              "calculated_prog_id": calculated_prog_id,
              "calculated_period_id": calculated_period_id,
              "meta_process_code": meta_process_code,
              "process_code": process_code,
              "task_code": task_def["task_code"],
              "process_exec_code": process_exec_code
        }
        print(f"http_conn_id = {http_conn_id}")
        print(f"▶️ Gọi {task_def['endpoint']} với payload: {payload}")
        resp = http.run(endpoint=task_def["endpoint"], json=payload)
        print(f"✅ {task_def['task_code']} → {resp.status_code}")
        return {"task": task_def["task_code"], "status": resp.status_code}
    return _inner


# =========================
# 🧭 Build DAG
# =========================
with DAG(
    dag_id='%s',
    default_args=default_args,
    schedule_interval=%s,
    catchup=False,
    tags=['uce', 'dynamic', '%s'],
    description='Auto-generated DAG for Meta Process: %s',
) as dag:

    start = DummyOperator(task_id='start')
    end = DummyOperator(task_id='end')

    # 🧩 Load variable tại parse-time (Airflow yêu cầu task có sẵn)
    try:
        variable_data = Variable.get("%s", default_var=None, deserialize_json=True)
        if variable_data is None:
            variable_data = {}
        elif isinstance(variable_data, str):
            variable_data = json.loads(variable_data)
    except Exception as e:
        print(f"⚠️ Error loading variable: {e}")
        variable_data = {}

    tasks = variable_data.get("tasks", [])
    http_conn_id = variable_data.get("httpConnId", variable_data.get("http_conn_id", "spring_boot_api"))
    process_code = variable_data.get("processCode", variable_data.get("process_code", "PROC_DEFAULT"))
    
    company_id = variable_data.get("companyId", variable_data.get("company_id", ""))
    brand_id = variable_data.get("brandId", variable_data.get("brand_id", ""))
    calculated_prog_id = variable_data.get("calculatedProgId", variable_data.get("calculated_prog_id", ""))
    calculated_period_id = variable_data.get("calculatedPeriodId", variable_data.get("calculated_period_id", ""))
    meta_process_code = variable_data.get("metaProcessCode", variable_data.get("meta_process_code", ""))

    http_tasks = {}

    # 🧱 Sinh task thật tại parse-time
    for t in tasks:
        task_code = t.get("taskCode", t.get("task_code", ""))
        if not task_code:
            continue
        
        # Convert camelCase to snake_case for task definition
        task_def = {
            "task_code": task_code,
            "endpoint": t.get("endpoint", "/api/v2/execution/execute-task")
        }
        
        http_tasks[task_code] = PythonOperator(
            task_id=task_code,
            python_callable=call_http_task(task_def, process_code, http_conn_id, company_id, brand_id, calculated_prog_id, calculated_period_id, meta_process_code),
            provide_context=True,
        )

    # 🔗 Gắn dependency động
    for t in tasks:
        task_code = t.get("taskCode", t.get("task_code", ""))
        if not task_code:
            continue
        depends = t.get("dependsOn", t.get("depends_on", []))
        if isinstance(depends, str):
            depends = [depends]
        for d in depends:
            if d in http_tasks:
                http_tasks[d] >> http_tasks[task_code]

    # 🚀 Nối start / end
    if http_tasks:
        first_tasks = [t for t in tasks if not t.get("dependsOn", t.get("depends_on", []))]
        last_tasks = [
            t for t in tasks
            if not any(
                t.get("taskCode", t.get("task_code", "")) in td.get("dependsOn", td.get("depends_on", []))
                for td in tasks
            )
        ]
        for ft in first_tasks:
            ft_code = ft.get("taskCode", ft.get("task_code", ""))
            if ft_code in http_tasks:
                start >> http_tasks[ft_code]
        for lt in last_tasks:
            lt_code = lt.get("taskCode", lt.get("task_code", ""))
            if lt_code in http_tasks:
                http_tasks[lt_code] >> end
    else:
        start >> end
""", dagId, scheduleValue, metaProcCode.toLowerCase(), metaProcCode, variableName);
    }

    /**
     * Delete DAG file
     */
    public void deleteDagFile(String dagDirectory, String procCode, String metaProcCode, Long calcProgId, Long calcPeriodId) {
        String dagId = String.format("uce_%s_%s_%s", 
            metaProcCode.toLowerCase(),
            calcProgId != null ? calcProgId : "0",
            calcPeriodId != null ? calcPeriodId : "0");
        
        String fileName = dagId + ".py";
        Path dagFilePath = Paths.get(dagDirectory, fileName);

        try {
            if (Files.exists(dagFilePath)) {
                Files.delete(dagFilePath);
                log.info("✅ Deleted DAG file: {}", dagFilePath);
            } else {
                log.warn("DAG file not found: {}", dagFilePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete DAG file: {}", e.getMessage(), e);
        }
    }
}
