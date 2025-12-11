package com.piplineData.loadService.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dl_sync_log", schema = "sts")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String syncLogId;

    String dataObjectCode;

    String syncType; // BATCH, STREAM

    Integer recordsFetched;

    Integer recordsInserted;

    Integer recordsUpdated;

    Integer recordsDeleted;

    String status; // SUCCESS, FAILED, RUNNING

    String errorMessage;

    LocalDateTime startedAt;

    LocalDateTime finishedAt;

    Integer retryCount;

}
