package com.scheduler.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FailoverService.
 * Validates dead-node detection and orphan job reassignment logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FailoverService")
class FailoverServiceTest {

    @Mock private WorkerNodeRepository nodeRepo;
    @Mock private JobRepository        jobRepo;
    @Mock private LeaderElection       leaderElection;

    @InjectMocks
    private FailoverService failoverService;

    private static final int DEAD_THRESHOLD_SECONDS = 15;

    void setDeadThreshold() {
        ReflectionTestUtils.setField(failoverService, "deadThresholdSeconds", DEAD_THRESHOLD_SECONDS);
    }

    // ── Follower skips failover ──────────────────────────────────────────────────
    @Nested
    @DisplayName("when this node is a follower")
    class WhenFollower {

        @Test
        @DisplayName("detectFailures() does nothing")
        void detectFailures_doesNothing_whenFollower() {
            given(leaderElection.isLeader()).willReturn(false);

            failoverService.detectFailures();

            verify(nodeRepo, never()).findAll();
        }
    }

    // ── Leader detects failures ──────────────────────────────────────────────────
    @Nested
    @DisplayName("when this node is the leader")
    class WhenLeader {

        @Test
        @DisplayName("marks stale node as DEAD and reassigns its jobs")
        void marksStaleNode_asDeadAndReassignsJobs() {
            setDeadThreshold();
            LocalDateTime staleHeartbeat = LocalDateTime.now().minusSeconds(DEAD_THRESHOLD_SECONDS + 5);

            WorkerNode staleNode = WorkerNode.builder()
                    .nodeId("dead-node")
                    .status(NodeStatus.ALIVE)
                    .lastHeartbeat(staleHeartbeat)
                    .isLeader(false)
                    .build();

            Job runningJob = Job.builder().name("Orphan Job")
                    .status(JobStatus.RUNNING)
                    .workerNodeId("dead-node")
                    .build();

            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findAll()).willReturn(List.of(staleNode));
            given(jobRepo.findByWorkerNodeIdAndStatus("dead-node", JobStatus.RUNNING))
                    .willReturn(new java.util.ArrayList<>(List.of(runningJob)));
            given(jobRepo.findByWorkerNodeIdAndStatus("dead-node", JobStatus.PENDING))
                    .willReturn(new java.util.ArrayList<>());

            failoverService.detectFailures();

            // Node should be marked as DEAD
            ArgumentCaptor<WorkerNode> nodeCaptor = ArgumentCaptor.forClass(WorkerNode.class);
            verify(nodeRepo).save(nodeCaptor.capture());
            assertThat(nodeCaptor.getValue().getStatus()).isEqualTo(NodeStatus.DEAD);
            assertThat(nodeCaptor.getValue().isLeader()).isFalse();

            // Orphan job should be reset to PENDING with cleared assignments
            ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepo).save(jobCaptor.capture());
            assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(jobCaptor.getValue().getWorkerNodeId()).isNull();
            assertThat(jobCaptor.getValue().getLockedBy()).isNull();
        }

        @Test
        @DisplayName("does not mark healthy node as dead")
        void doesNotMark_healthyNodeAsDead() {
            setDeadThreshold();
            WorkerNode healthyNode = WorkerNode.builder()
                    .nodeId("healthy-node")
                    .status(NodeStatus.ALIVE)
                    .lastHeartbeat(LocalDateTime.now())
                    .build();

            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findAll()).willReturn(List.of(healthyNode));

            failoverService.detectFailures();

            verify(nodeRepo, never()).save(any());
        }

        @Test
        @DisplayName("ignores nodes that are already marked DEAD")
        void ignoresNodes_alreadyDead() {
            setDeadThreshold();
            WorkerNode deadNode = WorkerNode.builder()
                    .nodeId("already-dead")
                    .status(NodeStatus.DEAD)
                    .lastHeartbeat(LocalDateTime.now().minusSeconds(DEAD_THRESHOLD_SECONDS + 30))
                    .build();

            given(leaderElection.isLeader()).willReturn(true);
            given(nodeRepo.findAll()).willReturn(List.of(deadNode));

            failoverService.detectFailures();

            verify(nodeRepo, never()).save(any());
        }
    }
}
