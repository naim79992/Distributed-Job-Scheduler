package com.scheduler.core;

import com.scheduler.distributed.ConsistentHashing;
import com.scheduler.distributed.LeaderElection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobScheduler {

    private final JobRepository jobRepo;
    private final WorkerNodeRepository nodeRepo;
    private final LeaderElection leaderElection;
    private final ConsistentHashing consistentHashing;

    @Scheduled(fixedRateString = "${scheduler.check-interval}")
    public void scheduleJobs() {
        if (!leaderElection.isLeader()) return;

        // Refresh hash ring
        List<WorkerNode> aliveNodes = nodeRepo.findByStatus(NodeStatus.ALIVE);
        aliveNodes.forEach(consistentHashing::addNode);

        // Find due PENDING jobs
        List<Job> pendingJobs = jobRepo.findDueJobs(JobStatus.PENDING, LocalDateTime.now());
        
        for (Job job : pendingJobs) {
            // Assign job to node using consistent hashing
            WorkerNode targetNode = consistentHashing.getNodeForJob(job.getId());
            if (targetNode != null) {
                job.setWorkerNodeId(targetNode.getNodeId());
                job.setStatus(JobStatus.PENDING); // Still pending but assigned
                jobRepo.save(job);
                log.debug("Job {} assigned to node {}", job.getId(), targetNode.getNodeId());
            }
        }
    }
}
