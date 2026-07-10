package com.scheduler.service;

import com.scheduler.entity.WorkerNode;
import com.scheduler.enums.NodeStatus;
import com.scheduler.repository.WorkerNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class HeartbeatService {

    private final WorkerNodeRepository nodeRepo;

    @Value("${node.id}")
    private String nodeId;
    
    @Value("${server.port}")
    private int port;
    
    @Value("${node.host:localhost}")
    private String host;

    @Scheduled(fixedRateString = "${scheduler.heartbeat-interval}")
    public void sendHeartbeat() {
        WorkerNode node = nodeRepo.findById(nodeId).orElse(
            WorkerNode.builder().nodeId(nodeId).build()
        );
        
        node.setHost(host);
        node.setPort(port);
        node.setStatus(NodeStatus.ALIVE);
        node.setLastHeartbeat(LocalDateTime.now());
        
        nodeRepo.save(node);
        log.debug("Heartbeat sent for node {}", nodeId);
    }
}
