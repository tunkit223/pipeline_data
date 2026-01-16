package com.piplineData.loadService.layer2.dto;

import lombok.Data;

import java.util.Map;

/**
 * Request DTO khi Airflow gọi để khởi tạo Process Execution
 */
@Data
public class ProcessExecutionRequest {
    
    private String metaProcCode;
    private Long companyId;
    private Long brandId;
    private Long calcProgId;
    private Long calcPeriodId;
    private String procExecCode; // dag_run_id từ Airflow
    private String processCode; // Optional: nếu đã có process sẵn
    private Map<String, Object> runtimeParams; // Params để render SQL template
}
