package com.scheduler.repository;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-layer integration tests using @DataJpaTest.
 * Runs against an H2 in-memory database — no real MySQL required.
 */
@DataJpaTest
@DisplayName("JobRepository")
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TestEntityManager entityManager;

    // ── Helper ─────────────────────────────────────────────────────────────────
    private Job saveJob(String name, JobStatus status, int priority, LocalDateTime nextRunTime) {
        return jobRepository.save(Job.builder()
                .name(name)
                .status(status)
                .priority(priority)
                .nextRunTime(nextRunTime)
                .build());
    }

    @BeforeEach
    void cleanUp() {
        jobRepository.deleteAll();
    }

    // ── findDueJobs ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("findDueJobs()")
    class FindDueJobs {

        @Test
        @DisplayName("returns only PENDING jobs whose nextRunTime is in the past")
        void returnsPendingJobsDueForExecution() {
            LocalDateTime past   = LocalDateTime.now().minusMinutes(1);
            LocalDateTime future = LocalDateTime.now().plusMinutes(5);

            saveJob("Due Job",    JobStatus.PENDING, 1, past);
            saveJob("Future Job", JobStatus.PENDING, 1, future);
            saveJob("Done Job",   JobStatus.DONE,    1, past);

            List<Job> due = jobRepository.findDueJobs(JobStatus.PENDING, LocalDateTime.now());

            assertThat(due).hasSize(1);
            assertThat(due.get(0).getName()).isEqualTo("Due Job");
        }

        @Test
        @DisplayName("returns jobs ordered by priority ascending (1 = highest)")
        void returnsDueJobsInPriorityOrder() {
            LocalDateTime past = LocalDateTime.now().minusMinutes(1);

            saveJob("Low Priority",  JobStatus.PENDING, 3, past);
            saveJob("High Priority", JobStatus.PENDING, 1, past);
            saveJob("Med Priority",  JobStatus.PENDING, 2, past);

            List<Job> due = jobRepository.findDueJobs(JobStatus.PENDING, LocalDateTime.now());

            assertThat(due).hasSize(3);
            assertThat(due.get(0).getName()).isEqualTo("High Priority");
            assertThat(due.get(1).getName()).isEqualTo("Med Priority");
            assertThat(due.get(2).getName()).isEqualTo("Low Priority");
        }

        @Test
        @DisplayName("returns empty list when no jobs are due")
        void returnsEmpty_whenNoDueJobs() {
            saveJob("Future Job", JobStatus.PENDING, 1, LocalDateTime.now().plusMinutes(10));

            List<Job> due = jobRepository.findDueJobs(JobStatus.PENDING, LocalDateTime.now());

            assertThat(due).isEmpty();
        }
    }

    // ── tryLockJob ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("tryLockJob()")
    class TryLockJob {

        @Test
        @DisplayName("acquires lock and transitions job to RUNNING when it is unlocked")
        void acquiresLock_whenJobIsUnlocked() {
            Job job = saveJob("Lockable Job", JobStatus.PENDING, 1, LocalDateTime.now());

            int updated = jobRepository.tryLockJob(job.getId(), "node-1", LocalDateTime.now());
            
            entityManager.flush();
            entityManager.clear();

            assertThat(updated).isEqualTo(1);
            Job locked = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(locked.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(locked.getLockedBy()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("fails to acquire lock when job is already locked by another node")
        void failsToAcquireLock_whenAlreadyLocked() {
            Job job = saveJob("Already Locked Job", JobStatus.RUNNING, 1, LocalDateTime.now());
            // Simulate pre-locked state
            job.setLockedBy("node-existing");
            jobRepository.save(job);

            int updated = jobRepository.tryLockJob(job.getId(), "node-2", LocalDateTime.now());

            assertThat(updated).isEqualTo(0);
        }
    }

    // ── releaseLock ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("releaseLock()")
    class ReleaseLock {

        @Test
        @DisplayName("clears lock and sets the specified status")
        void clearsLock_andSetsStatus() {
            Job job = saveJob("Running Job", JobStatus.RUNNING, 1, LocalDateTime.now());
            job.setLockedBy("node-1");
            job.setWorkerNodeId("node-1");
            jobRepository.save(job);

            jobRepository.releaseLock(job.getId(), JobStatus.DONE, LocalDateTime.now());

            entityManager.flush();
            entityManager.clear();

            Job released = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(released.getStatus()).isEqualTo(JobStatus.DONE);
            assertThat(released.getLockedBy()).isNull();
            assertThat(released.getWorkerNodeId()).isNull();
        }
    }

    // ── findByWorkerNodeIdAndStatus ─────────────────────────────────────────────
    @Nested
    @DisplayName("findByWorkerNodeIdAndStatus()")
    class FindByWorkerNodeAndStatus {

        @Test
        @DisplayName("returns only jobs matching given node and status")
        void returnsMatchingJobs() {
            Job job1 = jobRepository.save(Job.builder().name("Node1 Pending")
                    .status(JobStatus.PENDING).workerNodeId("node-1").build());
            jobRepository.save(Job.builder().name("Node2 Pending")
                    .status(JobStatus.PENDING).workerNodeId("node-2").build());
            jobRepository.save(Job.builder().name("Node1 Done")
                    .status(JobStatus.DONE).workerNodeId("node-1").build());

            List<Job> result = jobRepository.findByWorkerNodeIdAndStatus("node-1", JobStatus.PENDING);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Node1 Pending");
        }
    }
}
