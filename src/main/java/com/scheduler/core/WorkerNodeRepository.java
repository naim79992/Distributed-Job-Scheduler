package com.scheduler.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public interface WorkerNodeRepository extends JpaRepository<WorkerNode, String> {
    
    List<WorkerNode> findByStatus(NodeStatus status);
    
    List<WorkerNode> findByIsLeaderTrue();
    
    @Modifying
    @Transactional
    @Query(value = "UPDATE worker_nodes SET is_leader = true " +
           "WHERE node_id = :nodeId AND NOT EXISTS (SELECT 1 FROM (SELECT is_leader, last_heartbeat FROM worker_nodes) AS dummy WHERE dummy.is_leader = true AND dummy.last_heartbeat > :threshold)", nativeQuery = true)
    int tryAcquireLeadership(@Param("nodeId") String nodeId, @Param("threshold") LocalDateTime threshold);
    
    @Modifying
    @Transactional
    @Query("UPDATE WorkerNode w SET w.isLeader = false WHERE w.isLeader = true")
    void resetLeaders();

    @Modifying
    @Transactional
    @Query("UPDATE WorkerNode w SET w.isLeader = false WHERE w.nodeId <> :nodeId")
    void stepDownOthers(@Param("nodeId") String nodeId);
}
