# Distributed Job Scheduler

A highly available, fault-tolerant distributed job scheduler built with Java 17, Spring Boot, and MySQL. It is designed to coordinate, allocate, and execute computational tasks across a cluster of nodes using database-backed distributed algorithms including Leader Election, Distributed Locking, and Consistent Hashing.

---

## Table of Contents

- [Features](#features)
- [System Architecture](#system-architecture)
- [Distributed Algorithms](#distributed-algorithms)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Database Setup](#database-setup)
  - [Building the Application](#building-the-application)
  - [Running a Local Multi-Node Cluster](#running-a-local-multi-node-cluster)
- [API Reference](#api-reference)
- [Dashboard UI](#dashboard-ui)
- [License](#license)

---

## Features

### Core Scheduling & Execution
- **Cron-based & One-time Jobs:** Support for standard Cron expressions as well as one-time immediate executions.
- **Priority Queueing:** Jobs are prioritized dynamically (1 = HIGH, 2 = MEDIUM, 3 = LOW), ensuring high-priority tasks are executed first.
- **Worker Thread Pool:** Each node runs its own isolated thread pool to manage concurrent task execution locally.
- **Automatic Retry Policy:** Failed jobs are automatically retried with configurable limits (max 3 retries by default) before being designated as `DEAD`.

### Distributed Coordination
- **Leader Election:** Active leader selection ensures exactly one node manages global job-to-node assignments, preventing race conditions.
- **Split-Brain Resolution:** Automatic consensus alignment steps down competing leaders if network partitioning/split-brain occurs.
- **Consistent Hashing:** Jobs are mapped to node hashes on a logical ring. This minimizes job migration overhead when nodes join or leave the cluster.
- **Distributed Locking:** Multi-layered, database-level locking prevents duplicate job executions on different nodes.
- **Auto Failover:** Active heartbeats monitor node health. If a worker goes offline, the leader automatically reclaims and reassigns its orphan jobs to online workers.

---

## System Architecture

The cluster consists of multiple stateless worker nodes communicating over a shared database layer (MySQL).

```text
               +----------------------------------------+
               |           Shared MySQL DB              |
               |  (Jobs, Worker Nodes, Locks & Status)  |
               +----------------------------------------+
                             ^    ^    ^
        Heartbeats / Locks   |    |    |   Heartbeats / Locks
       +---------------------+    |    +---------------------+
       |                          |                          |
+------------+             +------------+             +------------+
| Worker 1   |             |  Worker 2  |             |  Worker 3  |
| (Leader)   |             | (Follower) |             | (Follower) |
+------------+             +------------+             +------------+
| Scheduler  |             | Exec Pool  |             | Exec Pool  |
| Exec Pool  |             +------------+             +------------+
+------------+
```

1. **Leader Role:** The active Leader polls the database for due jobs and maps them to healthy workers using the Consistent Hash Ring.
2. **Follower Role:** Followers skip the global scheduler loop but continuously run heartbeats and fetch jobs assigned to their specific `NODE_ID` for local execution.
3. **Failover Loop:** If any node fails to heartbeat within the threshold (15s), the Leader marks it `DEAD` and reclaims its assigned tasks.

---

## Distributed Algorithms

### 1. Leader Election
Leader election is achieved atomically using SQL statements with timestamp checks:
- The node updates leadership state in the database only if no active leader exists, or if the current leader's heartbeat is older than the `dead-threshold` (15 seconds).
- In the event of a split-brain situation, nodes compare lexicographical IDs to step down competing leaders.

### 2. Distributed Locking
Before executing any job, a worker node attempts to lock the job row atomically:
```sql
UPDATE Job j SET j.lockedBy = :nodeId, j.lockedAt = :now, j.status = 'RUNNING'
WHERE j.id = :jobId AND j.lockedBy IS NULL AND j.status = 'PENDING'
```
Only the node that updates `1` row succeeds in acquiring the execution lock.

### 3. Consistent Hashing
Instead of standard modulo routing, a virtual `TreeMap` ring is constructed:
- Nodes are hashed onto a ring based on their `NODE_ID`.
- Jobs are matched to nodes by traversing the ring clockwise to find the nearest node (using `ring.ceilingEntry(hash)`).

---

## Tech Stack

| Layer | Technology | Description |
|---|---|---|
| **Runtime** | Java 17 | Core programming platform |
| **Framework** | Spring Boot 3.2.3 | Dependency injection, scheduling, and MVC |
| **Data Access**| Spring Data JPA | ORM wrapper for SQL communication |
| **Database** | MySQL | Persistent store for job state and cluster registry |
| **Template** | Thymeleaf | Server-side template rendering for UI |
| **Build Tool**| Maven | Dependency and build management |

---

## Project Structure

The project follows a standard **Package-by-Layer** pattern for clear separation of concerns, readability, and ease of maintainability:

```text
src/main/java/com/scheduler/
│
├── controller/
│   ├── JobController.java          # REST API endpoints for submitting/managing jobs
│   └── DashboardController.java    # MVC controller serving UI and dashboard stats
│
├── entity/
│   ├── Job.java                    # JPA Entity representing a scheduler task
│   ├── JobStatus.java              # Enum: PENDING, RUNNING, DONE, FAILED, DEAD
│   ├── WorkerNode.java             # JPA Entity representing a node in the cluster
│   └── NodeStatus.java             # Enum: ALIVE, DEAD
│
├── repository/
│   ├── JobRepository.java          # Database queries for Job manipulation
│   └── WorkerNodeRepository.java   # Leader/node status queries & leadership locks
│
├── service/
│   ├── JobSchedulerService.java    # (Leader-only) Consistent hash job allocator
│   ├── WorkerPoolService.java      # Handles local job queueing and thread pool execution
│   ├── HeartbeatService.java       # Periodically broadcasts node heartbeat state
│   └── FailoverService.java        # (Leader-only) Detects dead nodes & reclaims tasks
│
├── distributed/
│   ├── ConsistentHashing.java      # Node ring allocator algorithm
│   ├── DistributedLock.java        # DB-backed execution locking mechanics
│   └── LeaderElection.java         # Consensus and leadership management
│
└── DistributedJobSchedulerApplication.java # Spring Boot main startup class
```

---

## Getting Started

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Apache Maven 3.8+
* Running MySQL instance

### Database Setup
1. Connect to your MySQL database and create the schema:
   ```sql
   CREATE DATABASE job_scheduler;
   ```
2. Adjust configuration settings in `src/main/resources/application.properties` (e.g., MySQL host, port, username, password) if necessary.

### Building the Application
Compile and package the JAR file:
```bash
mvn clean package -DskipTests
```

### Running a Local Multi-Node Cluster
You can run multiple instances of the application on a single machine by passing dynamic port and node ID values.

#### Unix / Linux / macOS (Bash/Zsh)
```bash
# Terminal 1: Node 1 (Leader Candidate)
PORT=8081 NODE_ID=node-8081 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar

# Terminal 2: Node 2
PORT=8082 NODE_ID=node-8082 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar

# Terminal 3: Node 3
PORT=8083 NODE_ID=node-8083 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar
```

#### Windows (PowerShell)
```powershell
# Terminal 1
$env:PORT="8081"; $env:NODE_ID="node-8081"; mvn spring-boot:run

# Terminal 2
$env:PORT="8082"; $env:NODE_ID="node-8082"; mvn spring-boot:run

# Terminal 3
$env:PORT="8083"; $env:NODE_ID="node-8083"; mvn spring-boot:run
```

---

## API Reference

### 1. Submit a Job
* **Endpoint:** `POST /api/jobs`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "name": "Database Backup Job",
    "cronExpression": "0 */5 * * * *",
    "priority": 1
  }
  ```

### 2. Retrieve All Jobs
* **Endpoint:** `GET /api/jobs`
* **Description:** Lists all registered jobs with details (status, worker assigned, last run, etc.).

### 3. List Active Nodes
* **Endpoint:** `GET /api/nodes`
* **Description:** Returns JSON representation of all registered cluster nodes and leadership statuses.

### 4. Delete/Cancel Job
* **Endpoint:** `DELETE /api/jobs/{id}`

---

## Dashboard UI

Access the real-time cluster monitor by opening any running node's base address in your web browser:
* **URL:** [http://localhost:8081/dashboard](http://localhost:8081/dashboard)

The interface automatically polls health data every 5 seconds to visualize:
- Connected worker nodes and their leadership indicators.
- Live system-wide metrics (Total Jobs, Running tasks, Completed queue, Active nodes).
- Job history table mapping active workloads to processing nodes.

---

## License
Distributed under the MIT License. See `LICENSE` for more information.
