package com.piplineData.loadService.layer2.dto;

import com.piplineData.loadService.layer2.entity.UceMetaProcess;
import com.piplineData.loadService.layer2.entity.UceMetaTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO để trả về Meta Process kèm danh sách Meta Tasks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaProcessWithTasks {
    
    private UceMetaProcess metaProcess;
    private List<UceMetaTask> tasks;
    private Integer totalTasks;
    private Integer startingTasks;
    private Integer endingTasks;
}
