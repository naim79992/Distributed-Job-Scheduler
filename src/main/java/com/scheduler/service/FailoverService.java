package com.scheduler.service;

import com.scheduler.entity.WorkerNode;
import com.scheduler.enums.NodeStatus;
import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.WorkerNodeRepository;
import com.scheduler.repository.JobRepository;
import com.scheduler.distributed.LeaderElection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FailoverService {

    private final WorkerNodeRepository nodeRepo;
    private final JobRepository jobRepo;
    private final LeaderElection leaderElection;

    @Value("${scheduler.dead-threshold}")
    private int deadThresholdSeconds;

    @Scheduled(fixedRate = 10000)
    public void detectFailures() {
        if (!leaderElection.isLeader()) return;

        LocalDateTime threshold = LocalDateTime.now().minusSeconds(deadThresholdSeconds);
        List<WorkerNode> nodes = nodeRepo.findAll();

        for (WorkerNode node : nodes) {
            if (node.getStatus() == NodeStatus.ALIVE && node.getLastHeartbeat().isBefore(threshold)) {
                log.warn("Node {} detected as DEAD", node.getNodeId());
                node.setStatus(NodeStatus.DEAD);
                node.setLeader(false);
                nodeRepo.save(node);
                
                reassignJobs(node.getNodeId());
            }
        }
    }

    private void reassignJobs(String deadNodeId) {
        // Find jobs that were running on the dead node or assigned to it
        List<Job> orphans = jobRepo.findByWorkerNodeIdAndStatus(deadNodeId, JobStatus.RUNNING);
        orphans.addAll(jobRepo.findByWorkerNodeIdAndStatus(deadNodeId, JobStatus.PENDING));

        for (Job job : orphans) {
            log.info("Reassigning orphan Job {} from dead node {}", job.getId(), deadNodeId);
            job.setWorkerNodeId(null);
            job.setStatus(JobStatus.PENDING);
            job.setLockedBy(null);
            job.setLockedAt(null);
            jobRepo.save(job);
        }
    }
}
