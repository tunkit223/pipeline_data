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
}
