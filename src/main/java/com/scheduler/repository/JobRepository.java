package com.scheduler.repository;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {
    
    List<Job> findByStatusOrderByPriorityAsc(JobStatus status);
    
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.lockedBy = :nodeId, j.lockedAt = :now, j.status = 'RUNNING' " +
           "WHERE j.id = :jobId AND j.lockedBy IS NULL AND j.status = 'PENDING'")
    int tryLockJob(@Param("jobId") String jobId, @Param("nodeId") String nodeId, @Param("now") LocalDateTime now);
    
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.workerNodeId = NULL, j.lockedBy = NULL, j.lockedAt = NULL, j.status = :status, j.lastRunTime = :now " +
           "WHERE j.id = :jobId")
    void releaseLock(@Param("jobId") String jobId, @Param("status") JobStatus status, @Param("now") LocalDateTime now);
    
    @Query("SELECT j FROM Job j WHERE j.status = :status AND j.nextRunTime <= :now ORDER BY j.priority ASC")
    List<Job> findDueJobs(@Param("status") JobStatus status, @Param("now") LocalDateTime now);
    
    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.workerNodeId = NULL, j.lockedBy = NULL, j.lockedAt = NULL, j.status = :status, j.lastRunTime = :now, j.nextRunTime = :nextRunTime " +
           "WHERE j.id = :jobId")
    void releaseAndReschedule(@Param("jobId") String jobId, @Param("status") JobStatus status, @Param("now") LocalDateTime now, @Param("nextRunTime") LocalDateTime nextRunTime);

    List<Job> findByWorkerNodeIdAndStatus(String workerNodeId, JobStatus status);
}
