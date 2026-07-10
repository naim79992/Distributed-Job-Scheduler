package com.scheduler.service;

import com.scheduler.entity.Job;
import com.scheduler.entity.JobStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.distributed.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.scheduling.support.CronExpression;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkerPoolService {

    private final JobRepository jobRepo;
    private final DistributedLock distributedLock;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @Value("${node.id}")
    private String nodeId;

    @Scheduled(fixedRate = 2000)
    public void processAssignedJobs() {
        // Find jobs assigned to this node that are PENDING
        List<Job> myJobs = jobRepo.findByWorkerNodeIdAndStatus(nodeId, JobStatus.PENDING);

        for (Job job : myJobs) {
            // Try to acquire distributed lock before execution
            if (distributedLock.tryLock(job.getId(), nodeId)) {
                log.info("Node {} starting execution of Job {}: {}", nodeId, job.getId(), job.getName());
                executor.submit(() -> executeJob(job));
            }
        }
    }

    private void executeJob(Job job) {
        try {
            // Simulate work
            Thread.sleep(15000);

            // Calculate next run time
            LocalDateTime nextRun = null;
            if (job.getCronExpression() != null && !job.getCronExpression().trim().isEmpty()) {
                try {
                    CronExpression cron = CronExpression.parse(job.getCronExpression());
                    nextRun = cron.next(LocalDateTime.now());
                } catch (Exception ex) {
                    log.error("Invalid cron expression for job {}: {}", job.getId(), job.getCronExpression());
                }
            }

            if (nextRun != null) {
                // Reschedule for next run
                distributedLock.releaseAndReschedule(job.getId(), JobStatus.PENDING, nextRun);
                log.info("Job {} completed and rescheduled for {}", job.getId(), nextRun);
            } else {
                // Mark job as DONE permanently
                distributedLock.releaseLock(job.getId(), JobStatus.DONE);
                log.info("Job {} completed successfully (no next run) by node {}", job.getId(), nodeId);
            }
        } catch (Exception e) {
            log.error("Job {} failed on node {}: {}", job.getId(), nodeId, e.getMessage());
            handleFailure(job);
        }
    }

    private void handleFailure(Job job) {
        int currentRetry = job.getRetryCount() + 1;
        if (currentRetry < job.getMaxRetry()) {
            job.setRetryCount(currentRetry);
            distributedLock.releaseLock(job.getId(), JobStatus.PENDING);
            log.info("Job {} rescheduled for retry (count: {})", job.getId(), currentRetry);
        } else {
            distributedLock.releaseLock(job.getId(), JobStatus.DEAD);
            log.error("Job {} reached max retries and marked as DEAD", job.getId());
        }
    }
}
