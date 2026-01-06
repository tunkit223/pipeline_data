package com.piplineData.loadService.layer2.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Entity: Khung tác vụ
 * Note: Không còn hard-code schema, sẽ được set động
 * Table: uce_meta_task
 */
@Data
@Entity
@Table(name = "uce_meta_task")
public class UceMetaTask {

    @Id
    @Column(name = "meta_task_code", nullable = false, length = 250)
    private String metaTaskCode;

    @Column(name = "meta_task_name", nullable = false, unique = true, length = 500)
    private String metaTaskName;

    @Column(name = "meta_task_type", nullable = false, length = 250)
    private String metaTaskType;

    @Column(name = "meta_proc_code", nullable = false, length = 250)
    private String metaProcCode;

    @Column(name = "task_order")
    private Integer taskOrder;

    @Column(name = "pre_meta_task_codelist", length = 250)
    private String preMetaTaskCodelist;

    @Column(name = "post_meta_task_codelist", length = 250)
    private String postMetaTaskCodelist;

    @Column(name = "is_starting", nullable = false)
    private Boolean isStarting = true;

    @Column(name = "is_ending", nullable = false)
    private Boolean isEnding = true;

    @Column(name = "selector", columnDefinition = "TEXT")
    private String selector;

    @Column(name = "processor", columnDefinition = "TEXT")
    private String processor;

    @Column(name = "insertor", columnDefinition = "TEXT")
    private String insertor;

    @Column(name = "meta_task_note", columnDefinition = "TEXT")
    private String metaTaskNote;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
