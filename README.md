# Distributed Job Scheduler

A highly available, fault-tolerant distributed job scheduler built with **Java 17**, **Spring Boot**, and **MySQL**. It coordinates, allocates, and executes computational tasks across a cluster of worker nodes using database-backed distributed algorithms — including Leader Election, Distributed Locking, and Consistent Hashing. A standalone **Angular 19** dashboard provides real-time monitoring of jobs and nodes.

---

## Table of Contents

- [Distributed Job Scheduler](#distributed-job-scheduler)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
    - [Core Scheduling \& Execution](#core-scheduling--execution)
    - [Distributed Coordination](#distributed-coordination)
  - [System Architecture](#system-architecture)
  - [Distributed Algorithms](#distributed-algorithms)
    - [1. Leader Election](#1-leader-election)
    - [2. Distributed Locking](#2-distributed-locking)
    - [3. Consistent Hashing](#3-consistent-hashing)
  - [Tech Stack](#tech-stack)
  - [Project Structure](#project-structure)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Database Setup](#database-setup)
    - [Building the Application](#building-the-application)
    - [Running a Local Multi-Node Cluster](#running-a-local-multi-node-cluster)
      - [1. Start the Java Backend (Windows PowerShell)](#1-start-the-java-backend-windows-powershell)
      - [2. Start the Angular Frontend](#2-start-the-angular-frontend)
  - [API Reference](#api-reference)
    - [1. Submit a Job](#1-submit-a-job)
    - [2. Retrieve All Jobs](#2-retrieve-all-jobs)
    - [3. List Active Nodes](#3-list-active-nodes)
    - [4. Delete/Cancel Job](#4-deletecancel-job)
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
| **Runtime** | Java 17 | Core backend programming platform |
| **Framework** | Spring Boot 3.2.3 | Dependency injection, scheduling, and REST API |
| **Data Access** | Spring Data JPA | ORM wrapper for SQL communication |
| **Database** | MySQL | Persistent store for job state and cluster registry |
| **Frontend** | Angular 19 | Standalone SPA dashboard for real-time monitoring |
| **HTTP Client** | RxJS + HttpClient | Reactive API communication from Angular to Java |
| **Dev Proxy** | Angular CLI Proxy | Routes `/api` calls to specific backend node ports |
| **Testing** | JUnit 5 + Mockito + H2 | Industry-standard unit, slice, and integration tests |
| **Build Tool** | Maven | Java dependency and build management |
| **Package Manager** | NPM | Frontend dependency management |

---

## Project Structure

The project follows a standard **Package-by-Layer** pattern for clear separation of concerns, readability, and ease of maintainability:

```text
Distributed-Job-Scheduler/
├── frontend/                           # Angular Dashboard Application
│   ├── src/app/
│   │   ├── components/                 # UI components (dashboard layout)
│   │   ├── models/                     # TypeScript interfaces
│   │   └── services/                   # HTTP services connecting to Java API
│   ├── proxy-8080.conf.json            # Proxy config for port 8080
│   └── package.json                    # NPM dependencies and custom start scripts
│
├── src/main/java/com/scheduler/        # Java Spring Boot Backend
│   ├── controller/
│   │   ├── JobController.java          # REST API endpoints for submitting/managing jobs
│   │   └── DashboardController.java    # REST API for dashboard stats and nodes
│   │
│   ├── entity/
│   │   ├── Job.java                    # JPA Entity representing a scheduler task
│   │   ├── JobStatus.java              # Enum: PENDING, RUNNING, DONE, FAILED, DEAD
│   │   ├── WorkerNode.java             # JPA Entity representing a node in the cluster
│   │   └── NodeStatus.java             # Enum: ALIVE, DEAD
│   │
│   ├── repository/
│   │   ├── JobRepository.java          # Database queries for Job manipulation
│   │   └── WorkerNodeRepository.java   # Leader/node status queries & leadership locks
│   │
│   ├── service/
│   │   ├── JobSchedulerService.java    # (Leader-only) Consistent hash job allocator
│   │   ├── WorkerPoolService.java      # Handles local job queueing and thread pool execution
│   │   ├── HeartbeatService.java       # Periodically broadcasts node heartbeat state
│   │   └── FailoverService.java        # (Leader-only) Detects dead nodes & reclaims tasks
│   │
│   ├── distributed/
│   │   ├── ConsistentHashing.java      # Node ring allocator algorithm
│   │   ├── DistributedLock.java        # DB-backed execution locking mechanics
│   │   └── LeaderElection.java         # Consensus and leadership management
│   │
│   └── DistributedJobSchedulerApplication.java # Spring Boot main startup class
│
├── src/test/java/com/scheduler/        # Unit and Integration Tests
│   ├── controller/                     # @WebMvcTest controller slice tests
│   ├── distributed/                    # Logic and mockito tests for core algorithms
│   ├── repository/                     # @DataJpaTest integration tests with H2
│   └── service/                        # Mockito-based service layer unit tests
```

---

## Getting Started

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Apache Maven 3.8+
* Node.js 18+ and NPM (for the Angular frontend)
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

### Running Tests
The project includes a comprehensive test suite (Unit Tests, `@WebMvcTest` controller slices, and `@DataJpaTest` repository integration tests using an in-memory **H2** database). To run the tests:
```bash
mvn test
```

### Running a Local Multi-Node Cluster
You can run multiple instances of the backend application on a single machine by passing dynamic port and node ID values.

#### 1. Start the Java Backend (Windows PowerShell)
```powershell
# Terminal 1: Backend Node 1 (8080)
$env:PORT="8080"; $env:NODE_ID="node-8080"; mvn spring-boot:run

# Terminal 2: Backend Node 2 (8081)
$env:PORT="8081"; $env:NODE_ID="node-8081"; mvn spring-boot:run

# Terminal 3: Backend Node 3 (8082)
$env:PORT="8082"; $env:NODE_ID="node-8082"; mvn spring-boot:run
```

#### 2. Start the Angular Frontend
The UI is a separate Angular application. We have configured scripts to proxy API calls to different backend nodes dynamically. 

Open a new terminal and navigate to the `frontend` directory:
```powershell
cd frontend
npm install
```

Now, start the Angular dashboard for your preferred backend node:
```powershell
npm start          # Runs on http://localhost:4200 (Connects to Java 8080)
npm run start:8081 # Runs on http://localhost:4201 (Connects to Java 8081)
npm run start:8082 # Runs on http://localhost:4202 (Connects to Java 8082)
npm run start:8083 # Runs on http://localhost:4203 (Connects to Java 8083)
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
    "cron": "0 */5 * * * *",
    "priority": 1
  }
  ```
  > `cron` is optional. If omitted, the job runs immediately as a one-time task.

### 2. Retrieve All Jobs
* **Endpoint:** `GET /api/jobs`
* **Description:** Lists all registered jobs with details (status, worker assigned, last run, next run, etc.).

### 3. Get Dashboard Stats
* **Endpoint:** `GET /api/dashboard`
* **Description:** Returns aggregate stats — total, running, completed, failed jobs and alive/dead node counts.

### 4. List Active Nodes
* **Endpoint:** `GET /api/nodes`
* **Description:** Returns JSON representation of all registered cluster nodes and their leadership status.

### 5. Delete/Cancel Job
* **Endpoint:** `DELETE /api/jobs/{id}`

---

## Dashboard UI

Access the real-time cluster monitor by opening the Angular dashboard in your browser. Each Angular instance connects to its corresponding Java backend node:

| Angular URL | Connected Backend |
|---|---|
| [http://localhost:4200](http://localhost:4200) | Java node on port 8080 |
| [http://localhost:4201](http://localhost:4201) | Java node on port 8081 |
| [http://localhost:4202](http://localhost:4202) | Java node on port 8082 |
| [http://localhost:4203](http://localhost:4203) | Java node on port 8083 |

The dashboard automatically polls every 5 seconds to display:
- Connected worker nodes with their **LEADER / FOLLOWER** status.
- Live system-wide metrics: Total Jobs, Running, Completed, and Active Nodes.
- Full job history table with worker assignment, start time, end time, and next scheduled run.

---

## License
Distributed under the MIT License. See `LICENSE` for more information.
