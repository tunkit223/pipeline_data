package com.piplineData.loadService.layer2.dto;

import lombok.Data;

/**
 * Request DTO để tạo/cập nhật Meta Process
 */
@Data
public class MetaProcessRequest {
    
    private String metaProcCode;
    private String metaProcName;
    private Long companyId;
    private Long brandId;
    private Long departmentId;
    private String metaProcNote;
    private Boolean isActive = true;
    
    /**
     * Schema để lưu metadata của tiến trình này
     * Ví dụ: "hr_calc", "finance_calc", "test_dynamic_schema"
     * Required field - must be specified when creating Meta Process
     */
    private String metadataSchema;

    /**
     * Airflow schedule interval
     * Examples: "@daily", "@hourly", "0 0 * * *", null (manual trigger only)
     */
    private String scheduleInterval;
}
