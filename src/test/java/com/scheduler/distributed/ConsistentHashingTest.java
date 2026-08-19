package com.scheduler.distributed;

import com.scheduler.entity.WorkerNode;
import com.scheduler.enums.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConsistentHashing.
 * Tests the core ring-based node assignment algorithm in isolation — no Spring context needed.
 */
@DisplayName("ConsistentHashing")
class ConsistentHashingTest {

    private ConsistentHashing consistentHashing;

    @BeforeEach
    void setUp() {
        consistentHashing = new ConsistentHashing();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private WorkerNode node(String id) {
        return WorkerNode.builder()
                .nodeId(id)
                .status(NodeStatus.ALIVE)
                .build();
    }

    // ── Empty Ring ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("when ring is empty")
    class WhenRingIsEmpty {

        @Test
        @DisplayName("getNodeForJob returns null")
        void getNodeForJob_returnsNull() {
            assertThat(consistentHashing.getNodeForJob("any-job")).isNull();
        }
    }

    // ── Single Node ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("when ring has a single node")
    class WhenSingleNode {

        @BeforeEach
        void addOneNode() {
            consistentHashing.addNode(node("node-1"));
        }

        @Test
        @DisplayName("all jobs are assigned to the only node")
        void allJobsAssignedToSingleNode() {
            assertThat(consistentHashing.getNodeForJob("job-alpha").getNodeId()).isEqualTo("node-1");
            assertThat(consistentHashing.getNodeForJob("job-beta").getNodeId()).isEqualTo("node-1");
            assertThat(consistentHashing.getNodeForJob("job-gamma").getNodeId()).isEqualTo("node-1");
        }

        @Test
        @DisplayName("same job always maps to the same node (deterministic)")
        void sameJobAlwaysMapsToSameNode() {
            String firstResult  = consistentHashing.getNodeForJob("job-xyz").getNodeId();
            String secondResult = consistentHashing.getNodeForJob("job-xyz").getNodeId();
            assertThat(firstResult).isEqualTo(secondResult);
        }
    }

    // ── Multiple Nodes ──────────────────────────────────────────────────────────
    @Nested
    @DisplayName("when ring has multiple nodes")
    class WhenMultipleNodes {

        @BeforeEach
        void addNodes() {
            consistentHashing.addNode(node("node-1"));
            consistentHashing.addNode(node("node-2"));
            consistentHashing.addNode(node("node-3"));
        }

        @Test
        @DisplayName("every job is assigned to one of the registered nodes")
        void everyJobAssignedToRegisteredNode() {
            for (int i = 0; i < 20; i++) {
                WorkerNode assigned = consistentHashing.getNodeForJob("job-" + i);
                assertThat(assigned).isNotNull();
                assertThat(assigned.getNodeId()).isIn("node-1", "node-2", "node-3");
            }
        }

        @Test
        @DisplayName("consistent routing — same job always maps to same node")
        void consistentRouting() {
            String r1 = consistentHashing.getNodeForJob("stable-job").getNodeId();
            String r2 = consistentHashing.getNodeForJob("stable-job").getNodeId();
            assertThat(r1).isEqualTo(r2);
        }
    }

    // ── Node Removal ────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("when a node is removed")
    class WhenNodeRemoved {

        @Test
        @DisplayName("jobs reroute to remaining nodes after removal")
        void jobsRerouteAfterRemoval() {
            consistentHashing.addNode(node("node-1"));
            consistentHashing.addNode(node("node-2"));

            consistentHashing.removeNode("node-1");

            WorkerNode assigned = consistentHashing.getNodeForJob("any-job");
            assertThat(assigned).isNotNull();
            assertThat(assigned.getNodeId()).isEqualTo("node-2");
        }

        @Test
        @DisplayName("returns null when last node is removed")
        void returnsNullWhenLastNodeRemoved() {
            consistentHashing.addNode(node("node-1"));
            consistentHashing.removeNode("node-1");

            assertThat(consistentHashing.getNodeForJob("any-job")).isNull();
        }
    }
}
