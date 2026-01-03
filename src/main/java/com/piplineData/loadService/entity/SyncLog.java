package com.piplineData.loadService.entity;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dl_sync_log", schema = "sts")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_log_id")
    private Long syncLogId;

    @Column(name = "data_obj_code", nullable = false, length = 250)
    private String dataObjCode;

    @Column(name = "sync_type", length = 50)
    private String syncType; // BATCH / STREAM

    @Column(name = "records_fetched")
    private Integer recordsFetched;

    @Column(name = "records_inserted")
    private Integer recordsInserted;

    @Column(name = "records_updated")
    private Integer recordsUpdated;

    @Column(name = "records_deleted")
    private Integer recordsDeleted;

    @Column(name = "status", length = 50)
    private String status; // SUCCESS / FAILED / RUNNING

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;
}
