package com.scheduler.distributed;

import com.scheduler.entity.JobStatus;
import com.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DistributedLock {
    
    private final JobRepository jobRepo;

    public boolean tryLock(String jobId, String nodeId) {
        int rows = jobRepo.tryLockJob(jobId, nodeId, LocalDateTime.now());
        return rows > 0;
    }

    public void releaseLock(String jobId, JobStatus status) {
        jobRepo.releaseLock(jobId, status, LocalDateTime.now());
    }

    public void releaseAndReschedule(String jobId, JobStatus status, LocalDateTime nextRunTime) {
        jobRepo.releaseAndReschedule(jobId, status, LocalDateTime.now(), nextRunTime);
    }
}
