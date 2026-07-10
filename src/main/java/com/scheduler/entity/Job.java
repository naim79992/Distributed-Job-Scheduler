package com.scheduler.entity;

import com.scheduler.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String name;
    @com.fasterxml.jackson.annotation.JsonProperty("cron")
    private String cronExpression;
    
    @Enumerated(EnumType.STRING)
    private JobStatus status;
    
    private int priority; // 1=HIGH, 2=MED, 3=LOW
    private int retryCount;
    @Builder.Default
    private int maxRetry = 3;
    
    private String workerNodeId;
    private String lockedBy;
    private LocalDateTime lockedAt;
    
    private LocalDateTime nextRunTime;
    private LocalDateTime lastRunTime;
}
