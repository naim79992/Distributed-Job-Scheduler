package com.scheduler.distributed;

import com.scheduler.core.WorkerNode;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.TreeMap;

@Component
public class ConsistentHashing {
    
    private final TreeMap<Integer, WorkerNode> ring = new TreeMap<>();

    public void addNode(WorkerNode node) {
        int hash = getHash(node.getNodeId());
        ring.put(hash, node);
    }

    public void removeNode(String nodeId) {
        int hash = getHash(nodeId);
        ring.remove(hash);
    }

    public WorkerNode getNodeForJob(String jobId) {
        if (ring.isEmpty()) return null;
        int hash = getHash(jobId);
        Map.Entry<Integer, WorkerNode> entry = ring.ceilingEntry(hash);
        if (entry == null) entry = ring.firstEntry();
        return entry.getValue();
    }

    private int getHash(String key) {
        return key.hashCode(); // In production, use a better hash function like MurmurHash
    }
}
