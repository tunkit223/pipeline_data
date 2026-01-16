package com.piplineData.loadService.layer2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO khi Airflow gọi để execute một Task
 */
@Data
public class TaskExecutionRequest {
    
    @JsonProperty(value = "taskCode", access = JsonProperty.Access.WRITE_ONLY)
    private String taskCodeCamel;
    
    @JsonProperty(value = "task_code", access = JsonProperty.Access.WRITE_ONLY)
    private String taskCodeSnake;
    
    @JsonProperty(value = "procExecCode", access = JsonProperty.Access.WRITE_ONLY)
    private String procExecCodeCamel;
    
    @JsonProperty(value = "process_exec_code", access = JsonProperty.Access.WRITE_ONLY)
    private String procExecCodeSnake; // dag_run_id từ Airflow
    
    @JsonProperty(value = "processCode", access = JsonProperty.Access.WRITE_ONLY)
    private String processCodeCamel;
    
    @JsonProperty(value = "process_code", access = JsonProperty.Access.WRITE_ONLY)
    private String processCodeSnake;
    
    @JsonProperty(value = "metaProcCode", access = JsonProperty.Access.WRITE_ONLY)
    private String metaProcCodeCamel;
    
    @JsonProperty(value = "meta_process_code", access = JsonProperty.Access.WRITE_ONLY)
    private String metaProcCodeSnake;
    
    @JsonProperty(value = "companyId", access = JsonProperty.Access.WRITE_ONLY)
    private Long companyIdCamel;
    
    @JsonProperty(value = "company_id", access = JsonProperty.Access.WRITE_ONLY)
    private Long companyIdSnake;
    
    @JsonProperty(value = "brandId", access = JsonProperty.Access.WRITE_ONLY)
    private Long brandIdCamel;
    
    @JsonProperty(value = "brand_id", access = JsonProperty.Access.WRITE_ONLY)
    private Long brandIdSnake;
    
    @JsonProperty(value = "calcProgId", access = JsonProperty.Access.WRITE_ONLY)
    private Long calcProgIdCamel;
    
    @JsonProperty(value = "calculated_prog_id", access = JsonProperty.Access.WRITE_ONLY)
    private Long calcProgIdSnake;
    
    @JsonProperty(value = "calcPeriodId", access = JsonProperty.Access.WRITE_ONLY)
    private Long calcPeriodIdCamel;
    
    @JsonProperty(value = "calculated_period_id", access = JsonProperty.Access.WRITE_ONLY)
    private Long calcPeriodIdSnake;
    
    @JsonProperty(value = "runtimeParams", access = JsonProperty.Access.WRITE_ONLY)
    private Map<String, Object> runtimeParamsCamel;
    
    @JsonProperty(value = "runtime_params", access = JsonProperty.Access.WRITE_ONLY)
    private Map<String, Object> runtimeParamsSnake; // Params để render SQL (rule điều khiển)
    
    // Getter methods that return the first non-null value
    public String getTaskCode() {
        return taskCodeCamel != null ? taskCodeCamel : taskCodeSnake;
    }
    
    public String getProcExecCode() {
        return procExecCodeCamel != null ? procExecCodeCamel : procExecCodeSnake;
    }
    
    public String getProcessCode() {
        return processCodeCamel != null ? processCodeCamel : processCodeSnake;
    }
    
    public String getMetaProcCode() {
        return metaProcCodeCamel != null ? metaProcCodeCamel : metaProcCodeSnake;
    }
    
    public Long getCompanyId() {
        return companyIdCamel != null ? companyIdCamel : companyIdSnake;
    }
    
    public Long getBrandId() {
        return brandIdCamel != null ? brandIdCamel : brandIdSnake;
    }
    
    public Long getCalcProgId() {
        return calcProgIdCamel != null ? calcProgIdCamel : calcProgIdSnake;
    }
    
    public Long getCalcPeriodId() {
        return calcPeriodIdCamel != null ? calcPeriodIdCamel : calcPeriodIdSnake;
    }
    
    public Map<String, Object> getRuntimeParams() {
        return runtimeParamsCamel != null ? runtimeParamsCamel : runtimeParamsSnake;
    }
}
