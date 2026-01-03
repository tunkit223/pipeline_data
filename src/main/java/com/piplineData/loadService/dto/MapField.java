package com.piplineData.loadService.dto;

import lombok.Data;

@Data
public class MapField {
    private String to;      // tên cột đích
    private String from;    // tên cột nguồn
    private String build;   // "key" hoặc "value"
}
