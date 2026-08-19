package com.scheduler.distributed;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for DistributedLock.
 * Uses Mockito to isolate from the database layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedLock")
class DistributedLockTest {

    @Mock
    private JobRepository jobRepo;

    @InjectMocks
    private DistributedLock distributedLock;

    private static final String JOB_ID  = "job-001";
    private static final String NODE_ID = "node-001";

    // ── tryLock ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("tryLock()")
    class TryLock {

        @Test
        @DisplayName("returns true when lock is successfully acquired (1 row updated)")
        void returnsTrue_whenLockAcquired() {
            given(jobRepo.tryLockJob(eq(JOB_ID), eq(NODE_ID), any(LocalDateTime.class))).willReturn(1);

            boolean result = distributedLock.tryLock(JOB_ID, NODE_ID);

            assertThat(result).isTrue();
            verify(jobRepo).tryLockJob(eq(JOB_ID), eq(NODE_ID), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("returns false when lock is already held by another node (0 rows updated)")
        void returnsFalse_whenLockAlreadyHeld() {
            given(jobRepo.tryLockJob(eq(JOB_ID), eq(NODE_ID), any(LocalDateTime.class))).willReturn(0);

            boolean result = distributedLock.tryLock(JOB_ID, NODE_ID);

            assertThat(result).isFalse();
        }
    }

    // ── releaseLock ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("releaseLock()")
    class ReleaseLock {

        @Test
        @DisplayName("delegates to repository with correct status and timestamp")
        void delegatesToRepository() {
            distributedLock.releaseLock(JOB_ID, JobStatus.DONE);

            verify(jobRepo).releaseLock(eq(JOB_ID), eq(JobStatus.DONE), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("can release with FAILED status")
        void canReleaseWithFailedStatus() {
            distributedLock.releaseLock(JOB_ID, JobStatus.FAILED);

            verify(jobRepo).releaseLock(eq(JOB_ID), eq(JobStatus.FAILED), any(LocalDateTime.class));
        }
    }

    // ── releaseAndReschedule ─────────────────────────────────────────────────────
    @Nested
    @DisplayName("releaseAndReschedule()")
    class ReleaseAndReschedule {

        @Test
        @DisplayName("delegates to repository with correct next run time")
        void delegatesToRepository() {
            LocalDateTime nextRun = LocalDateTime.now().plusMinutes(5);

            distributedLock.releaseAndReschedule(JOB_ID, JobStatus.PENDING, nextRun);

            verify(jobRepo).releaseAndReschedule(
                    eq(JOB_ID),
                    eq(JobStatus.PENDING),
                    any(LocalDateTime.class),
                    eq(nextRun)
            );
        }
    }
}
