package com.scheduler.service;

import com.scheduler.distributed.ConsistentHashing;
import com.scheduler.distributed.LeaderElection;
import com.scheduler.entity.Job;
import com.scheduler.entity.WorkerNode;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.NodeStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.WorkerNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JobSchedulerService.
 * Validates leader-only scheduling and consistent-hash-based job assignment.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobSchedulerService")
class JobSchedulerServiceTest {

    @Mock private JobRepository          jobRepo;
    @Mock private WorkerNodeRepository   nodeRepo;
    @Mock private LeaderElection         leaderElection;
    @Mock private ConsistentHashing      consistentHashing;

    @InjectMocks
    private JobSchedulerService jobSchedulerService;

    // ── Follower skips scheduling ────────────────────────────────────────────────
    @Nested
    @DisplayName("when this node is a follower")
    class WhenFollower {

        @Test
        @DisplayName("scheduleJobs() does nothing")
        void scheduleJobs_doesNothing_whenFollower() {
            given(leaderElection.isLeader()).willReturn(false);

            jobSchedulerService.scheduleJobs();

            verify(jobRepo, never()).findDueJobs(any(), any());
            verify(jobRepo, never()).save(any());
        }
    }

    // ── Leader schedules jobs ────────────────────────────────────────────────────
    @Nested
    @DisplayName("when this node is the leader")
    class WhenLeader {

        @Test
        @DisplayName("assigns pending jobs to nodes via consistent hashing")
        void assignsPendingJobs_toNodes() {
            WorkerNode node  = WorkerNode.builder().nodeId("node-1").status(NodeStatus.ALIVE).build();
            Job job          = Job.builder().name("Report Job").status(JobStatus.PENDING)
                    .nextRunTime(LocalDateTime.now().minusSeconds(1)).build();

            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findByStatus(NodeStatus.ALIVE)).willReturn(List.of(node));
            given(jobRepo.findDueJobs(eq(JobStatus.PENDING), any(LocalDateTime.class))).willReturn(List.of(job));
            given(consistentHashing.getNodeForJob(job.getId())).willReturn(node);

            jobSchedulerService.scheduleJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepo).save(captor.capture());
            assertThat(captor.getValue().getWorkerNodeId()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("does not save job when no node available in the ring")
        void doesNotSaveJob_whenNoNodeInRing() {
            Job job = Job.builder().name("Unroutable Job").status(JobStatus.PENDING)
                    .nextRunTime(LocalDateTime.now().minusSeconds(1)).build();

            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findByStatus(NodeStatus.ALIVE)).willReturn(List.of());
            given(jobRepo.findDueJobs(eq(JobStatus.PENDING), any(LocalDateTime.class))).willReturn(List.of(job));
            given(consistentHashing.getNodeForJob(job.getId())).willReturn(null);

            jobSchedulerService.scheduleJobs();

            verify(jobRepo, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when there are no pending due jobs")
        void doesNothing_whenNoPendingJobs() {
            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findByStatus(NodeStatus.ALIVE)).willReturn(List.of());
            given(jobRepo.findDueJobs(eq(JobStatus.PENDING), any(LocalDateTime.class))).willReturn(List.of());

            jobSchedulerService.scheduleJobs();

            verify(jobRepo, never()).save(any());
        }
    }
}
