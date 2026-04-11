package com.scheduler.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "worker_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerNode {
    @Id
    private String nodeId;
    private String host;
    private int port;
    
    @Enumerated(EnumType.STRING)
    private NodeStatus status;
    
    private boolean isLeader;
    private LocalDateTime lastHeartbeat;
    private int activeJobs;
}
