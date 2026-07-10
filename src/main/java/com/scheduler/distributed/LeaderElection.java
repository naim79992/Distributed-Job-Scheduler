package com.scheduler.distributed;

import com.scheduler.entity.WorkerNode;
import com.scheduler.entity.NodeStatus;
import com.scheduler.repository.WorkerNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class LeaderElection {

    private final WorkerNodeRepository nodeRepo;
    
    @Value("${node.id}")
    private String nodeId;
    
    @Value("${scheduler.dead-threshold}")
    private int deadThresholdSeconds;

    @Scheduled(fixedRateString = "${scheduler.heartbeat-interval}")
    public void tryBecomeLeader() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(deadThresholdSeconds);
        
        // 1. Split-brain detection and resolution
        java.util.List<WorkerNode> leaders = nodeRepo.findByIsLeaderTrue();
        if (leaders.size() > 1) {
            String bestLeaderId = leaders.stream()
                    .filter(n -> n.getStatus() == NodeStatus.ALIVE && n.getLastHeartbeat().isAfter(threshold))
                    .map(WorkerNode::getNodeId)
                    .min(String::compareTo)
                    .orElse("");

            if (nodeRepo.findById(nodeId).map(WorkerNode::isLeader).orElse(false) && !nodeId.equals(bestLeaderId)) {
                log.warn("Split-brain detected! Node {} stepping down as leader. Best leader: {}", nodeId, bestLeaderId);
                updateNodeLeadership(false);
                return;
            }
        }

        // 2. Try to become leader if no active leader exists
        int updated = nodeRepo.tryAcquireLeadership(nodeId, threshold);
        
        if (updated > 0) {
            log.info("Node {} has been elected as LEADER", nodeId);
            nodeRepo.stepDownOthers(nodeId);
            updateNodeLeadership(true);
        } else {
            // 3. Heartbeat for existing leader
            nodeRepo.findById(nodeId).ifPresent(node -> {
                if (node.isLeader()) {
                    updateNodeLeadership(true);
                }
            });
        }
    }
    
    private void updateNodeLeadership(boolean isLeader) {
        nodeRepo.findById(nodeId).ifPresent(node -> {
            node.setLeader(isLeader);
            node.setLastHeartbeat(LocalDateTime.now());
            node.setStatus(NodeStatus.ALIVE);
            nodeRepo.save(node);
        });
    }
    
    public boolean isLeader() {
        return nodeRepo.findById(nodeId)
                .map(WorkerNode::isLeader)
                .orElse(false);
    }
}
