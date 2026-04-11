package com.scheduler.distributed;

import com.scheduler.core.WorkerNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashingTest {

    private ConsistentHashing consistentHashing;

    @BeforeEach
    void setUp() {
        consistentHashing = new ConsistentHashing();
    }

    @Test
    void testNodeAssignment() {
        WorkerNode node1 = WorkerNode.builder().nodeId("node1").build();
        WorkerNode node2 = WorkerNode.builder().nodeId("node2").build();
        
        consistentHashing.addNode(node1);
        consistentHashing.addNode(node2);

        WorkerNode target1 = consistentHashing.getNodeForJob("job1");
        WorkerNode target2 = consistentHashing.getNodeForJob("job2");

        assertNotNull(target1);
        assertNotNull(target2);
        
        // Ensure job is always assigned to one of the nodes
        assertTrue(target1.getNodeId().equals("node1") || target1.getNodeId().equals("node2"));
    }

    @Test
    void testConsistentAssignment() {
        WorkerNode node1 = WorkerNode.builder().nodeId("node1").build();
        consistentHashing.addNode(node1);

        WorkerNode target1 = consistentHashing.getNodeForJob("job-abc");
        WorkerNode target2 = consistentHashing.getNodeForJob("job-abc");

        assertEquals(target1.getNodeId(), target2.getNodeId(), "Same job should map to same node");
    }

    @Test
    void testNodeRemoval() {
        WorkerNode node1 = WorkerNode.builder().nodeId("node1").build();
        WorkerNode node2 = WorkerNode.builder().nodeId("node2").build();
        
        consistentHashing.addNode(node1);
        consistentHashing.addNode(node2);

        consistentHashing.removeNode("node1");
        
        WorkerNode target = consistentHashing.getNodeForJob("any-job");
        assertEquals("node2", target.getNodeId(), "Should reroute to remaining node");
    }
}
