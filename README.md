# 📋 Distributed Job Scheduler

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-19-red.svg)](https://angular.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A **production-grade, fault-tolerant distributed job scheduling system** built with Java 17 and Spring Boot 3. It runs multiple instances (nodes) of the same application simultaneously, each competing to become a **Leader** and coordinate the execution of scheduled tasks across the cluster. A real-time **Angular 19** dashboard provides live visibility into jobs and cluster health.

> This is **not** a simple cron job runner. It is a fully distributed system with leader election, consistent hashing, distributed locking, and automatic failover — all backed by a single shared MySQL database. No ZooKeeper, no Kafka, no external message broker needed.

---

## 📚 Table of Contents

- [What Problem Does This Solve?](#-what-problem-does-this-solve)
- [How It Works — The Big Picture](#️-how-it-works--the-big-picture)
- [Core Distributed Algorithms](#️-core-distributed-algorithms)
  - [1. Leader Election](#1-leader-election)
  - [2. Consistent Hashing](#2-consistent-hashing)
  - [3. Distributed Locking](#3-distributed-locking)
  - [4. Auto Failover](#4-auto-failover)
- [Tech Stack](#️-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
  - [Option A: Run with Docker](#option-a-run-with-docker-recommended--one-command)
  - [Option B: Run Manually](#option-b-run-manually-local-development)
- [API Reference](#-api-reference)
- [Running Tests](#-running-tests)
- [Dashboard UI](#-dashboard-ui)
- [Configuration Reference](#️-configuration-reference)
- [FAQ](#-faq)
- [License](#-license)

---

## ❓ What Problem Does This Solve?

Imagine you have a task: "Send an email report every morning at 9 AM."

On a **single server**, this is easy — just schedule a cron job. But what happens when:
- Your server crashes at 8:59 AM? → The job never runs.
- You have 10 servers running the same app? → The job runs **10 times** (duplicate execution).
- One server becomes slow or dies mid-task? → The task is stuck forever.

This project solves all three problems:
- ✅ If one node crashes, another takes over automatically (**Auto Failover**)
- ✅ Only **one** node executes each job at a time, even with 10 servers (**Distributed Locking**)
- ✅ Jobs are distributed evenly across all healthy nodes (**Consistent Hashing**)
- ✅ Exactly **one** node acts as the coordinator/manager (**Leader Election**)

---

## 🏗️ How It Works — The Big Picture

```
                     ┌─────────────────────────────────┐
                     │         Shared MySQL DB          │
                     │  (jobs, worker_nodes tables)     │
                     └────────────┬────────────┬────────┘
                                  │            │
              ┌───────────────────┘            └───────────────────┐
              │                                                     │
  ┌───────────▼──────────┐                         ┌──────────────▼───────┐
  │   Node 1 (LEADER)    │                         │  Node 2 (FOLLOWER)   │
  │   Port: 8080         │                         │  Port: 8081          │
  │                      │                         │                      │
  │ ✔ Polls for due jobs │                         │ ✔ Sends heartbeats   │
  │ ✔ Assigns to nodes   │                         │ ✔ Executes assigned  │
  │ ✔ Monitors health    │                         │   jobs locally       │
  │ ✔ Sends heartbeats   │                         │ ✔ Watches for leader │
  └──────────────────────┘                         └──────────────────────┘
```

### The lifecycle of a single job

```
User submits job via API
          │
          ▼
   Job saved to DB (status: PENDING)
          │
          ▼
  Leader node polls DB every second
          │
          ▼
  Leader picks a healthy node using Consistent Hashing
          │
          ▼
  Leader assigns job to that node (sets workerNodeId)
          │
          ▼
  Assigned node locks the job atomically (status: RUNNING)
          │
          ├── Success → releases lock (status: DONE)
          │
          └── Failure → retries up to 3 times → FAILED or DEAD
```

---

## ⚙️ Core Distributed Algorithms

### 1. Leader Election

Every node continuously competes to become the **Leader**. The Leader is the only one who polls the database for due jobs and assigns them to healthy worker nodes.

**How it works:**
- Each node sends a **heartbeat** to the database every 5 seconds, updating its `lastHeartbeat` timestamp.
- A node claims leadership by setting `isLeader = true` in the DB **only if** no other node has sent a heartbeat in the last 15 seconds.
- If two nodes both think they are leader (split-brain), they compare their `nodeId` values — the smaller ID wins, the other steps down immediately.

```
Node A (node-8080) → heartbeat at 12:00:00
Node B (node-8081) → heartbeat at 12:00:01
...
Node A goes offline → no heartbeat for 15 seconds
Node B detects silence → claims leadership
Node A restarts → sees Node B is leader → becomes follower
```

---

### 2. Consistent Hashing

The Leader distributes jobs across worker nodes using a **Consistent Hash Ring** instead of simple round-robin.

**How it works:**
1. All healthy nodes are placed on an imaginary circular ring based on a hash of their `nodeId`.
2. Each job is also hashed to a point on the ring.
3. The job is assigned to the **nearest node clockwise** from its hash position.

**Why this is better than round-robin:**
- When a new node joins: Only ~1/N jobs get redistributed (not all of them)
- When a node leaves: Only that node's jobs get reassigned (not all of them)

```java
// Simplified implementation
TreeMap<Integer, WorkerNode> ring = new TreeMap<>();

ring.put(hash("node-8080"), node1);  // position: 234
ring.put(hash("node-8081"), node2);  // position: 891

// job-123 hashes to 450 → nearest clockwise = node2 at 891
WorkerNode assigned = ring.ceilingEntry(hash("job-123")).getValue();
```

---

### 3. Distributed Locking

Even after the Leader assigns a job to a node, we need a guarantee that **only one node executes it**. This is done with an atomic SQL lock:

```sql
UPDATE jobs
SET locked_by = 'node-8081',
    locked_at  = NOW(),
    status     = 'RUNNING'
WHERE id        = 'job-uuid-123'
  AND locked_by IS NULL        -- not already locked
  AND status    = 'PENDING';   -- waiting to run
```

- `1 row updated` → this node won the lock → proceeds to execute
- `0 rows updated` → another node already locked it → this node skips

This is **Optimistic Locking** — no deadlocks, no blocking, no external lock server required.

---

### 4. Auto Failover

The Leader monitors all nodes' heartbeat timestamps. If a node hasn't sent a heartbeat in **15 seconds**, it is marked `DEAD` and all its jobs are automatically reassigned.

```
Node 3 goes offline
       │
       ▼
Leader detects no heartbeat for 15s
       │
       ▼
Leader marks Node 3 as DEAD
       │
       ▼
Leader finds all jobs where workerNodeId = 'node-3'
       │
       ▼
Leader reassigns them to Node 1 or Node 2
       │
       ▼
System continues working — zero data loss
```

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 17 | Core backend language |
| **Framework** | Spring Boot 3.2.3 | REST API, dependency injection, scheduling |
| **Database** | MySQL 8.0 | Shared state, job queue, distributed locks |
| **ORM** | Spring Data JPA + Hibernate 6 | Database interaction |
| **Frontend** | Angular 19 | Real-time monitoring dashboard |
| **HTTP Client** | RxJS + HttpClient | Reactive API calls from Angular |
| **Web Server** | Nginx (in Docker) | Serves Angular + proxies API requests |
| **API Docs** | OpenAPI 3 (Springdoc Swagger) | Interactive API documentation |
| **Testing** | JUnit 5 + Mockito + AssertJ + H2 | Unit, slice, and integration tests |
| **Build** | Maven 3.8+ | Java dependency and build management |
| **Packaging** | Docker & Docker Compose | Container orchestration |
| **Boilerplate** | Lombok | Reduces repetitive Java code |

---

## 📁 Project Structure

```
Distributed-Job-Scheduler/
│
├── Dockerfile                             ← Builds the Java backend into a Docker image
├── docker-compose.yml                     ← Orchestrates all services (DB + Backend + Frontend)
├── pom.xml                                ← Maven: all Java dependencies
│
├── frontend/                              ← Standalone Angular 19 SPA
│   ├── Dockerfile                         ← Builds Angular and serves via Nginx
│   ├── nginx.conf                         ← Serves UI + proxies /api to backend
│   ├── package.json                       ← NPM dependencies and start scripts
│   ├── proxy-8080.conf.json               ← Dev-only: proxies dev server → backend
│   └── src/app/
│       ├── components/                    ← Dashboard UI components
│       ├── models/                        ← TypeScript interfaces (Job, WorkerNode)
│       └── services/                      ← HTTP services that call the Java API
│
└── src/
    ├── main/java/com/scheduler/
    │   ├── config/
    │   │   └── SwaggerConfig.java         ← OpenAPI 3 / Swagger UI configuration
    │   ├── controller/
    │   │   ├── JobController.java         ← REST: POST/GET/DELETE /api/jobs
    │   │   └── DashboardController.java   ← REST: GET /api/nodes, /api/dashboard
    │   ├── entity/
    │   │   ├── Job.java                   ← DB table: jobs
    │   │   └── WorkerNode.java            ← DB table: worker_nodes
    │   ├── enums/
    │   │   ├── JobStatus.java             ← PENDING → RUNNING → DONE | FAILED | DEAD
    │   │   └── NodeStatus.java            ← ALIVE | DEAD
    │   ├── repository/
    │   │   ├── JobRepository.java         ← Custom JPQL queries (tryLockJob, releaseLock)
    │   │   └── WorkerNodeRepository.java  ← Leader election & node status queries
    │   ├── service/
    │   │   ├── JobSchedulerService.java   ← LEADER ONLY: polls DB, assigns jobs via hash ring
    │   │   ├── WorkerPoolService.java     ← ALL NODES: thread pool, executes assigned jobs
    │   │   ├── HeartbeatService.java      ← ALL NODES: sends heartbeat every 5s, runs election
    │   │   └── FailoverService.java       ← LEADER ONLY: detects dead nodes, reassigns jobs
    │   ├── distributed/
    │   │   ├── ConsistentHashing.java     ← TreeMap-based virtual ring routing
    │   │   ├── DistributedLock.java       ← Atomic SQL-based optimistic locking
    │   │   └── LeaderElection.java        ← DB consensus, split-brain resolution
    │   └── DistributedJobSchedulerApplication.java
    │
    ├── main/resources/
    │   └── application.properties         ← DB config, node ID, scheduler intervals
    │
    └── test/java/com/scheduler/
        ├── controller/                    ← @WebMvcTest: REST layer in isolation
        ├── distributed/                   ← Unit: ConsistentHashing, DistributedLock
        ├── repository/                    ← @DataJpaTest: JPQL queries against H2
        └── service/                       ← Unit: JobSchedulerService, FailoverService
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Required For |
|---|---|---|
| Docker Desktop | Latest | Option A (Docker) |
| Java JDK | 17+ | Option B (Manual) |
| Apache Maven | 3.8+ | Option B (Manual) |
| Node.js + NPM | 18+ | Option B (Manual, Angular) |
| MySQL | 8.0+ | Option B (Manual) |

---

### Option A: Run with Docker (Recommended — One Command)

Docker automatically sets up the database, backend, and frontend. **No MySQL installation or manual configuration required.**

**Step 1:** Install and launch [Docker Desktop](https://www.docker.com/products/docker-desktop/).

**Step 2:** In a terminal, navigate to the project root and run:

```bash
docker-compose up -d --build
```

Docker will:
1. Start a MySQL 8.0 database
2. Build and launch the Spring Boot backend on port 8080
3. Build the Angular app and serve it via Nginx on port 4200

**Step 3:** Wait ~60 seconds, then open your browser:

| URL | What It Opens |
|---|---|
| http://localhost:4200 | Angular Dashboard |
| http://localhost:8080/swagger-ui.html | Swagger API Docs |
| http://localhost:8080/api/jobs | Raw JSON API |

**Step 4:** To stop and remove all containers:

```bash
docker-compose down
```

> **First-time note:** Maven downloads all dependencies during the first build. It may take 3–5 minutes. Subsequent builds are fast.

---

### Option B: Run Manually (Local Development)

Use this to simulate a real multi-node cluster on your machine.

#### Step 1 — Database Setup

```sql
CREATE DATABASE job_scheduler;
```

Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/job_scheduler
spring.datasource.username=your_username
spring.datasource.password=your_password
```

#### Step 2 — Build the Project

```bash
mvn clean package -DskipTests
```

#### Step 3 — Start Backend Nodes

Open **a separate terminal for each node**. Each needs a unique `PORT` and `NODE_ID`:

```powershell
# Terminal 1 — First node (will become Leader)
$env:PORT="8080"; $env:NODE_ID="node-8080"; mvn spring-boot:run

# Terminal 2 — Second node (Follower)
$env:PORT="8081"; $env:NODE_ID="node-8081"; mvn spring-boot:run

# Terminal 3 — Third node (Follower)
$env:PORT="8082"; $env:NODE_ID="node-8082"; mvn spring-boot:run
```

> **Watch the logs!** One node will print `"Became LEADER"`. The others will print `"Staying as FOLLOWER"`. Kill the leader terminal and watch a follower automatically take over within 15 seconds.

#### Step 4 — Start the Angular Frontend

```bash
cd frontend
npm install
npm start           # http://localhost:4200 (connects to node-8080)
npm run start:8081  # http://localhost:4201 (connects to node-8081)
npm run start:8082  # http://localhost:4202 (connects to node-8082)
```

---

## 📡 API Reference

### Swagger UI (Interactive Docs)

The fastest way to explore and test the API:
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

---

### `POST /api/jobs` — Submit a Job

```json
{
  "name": "Daily Report",
  "cron": "0 0 9 * * *",
  "priority": 1
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | String | ✅ | Job display name |
| `cron` | String | ❌ | 6-field Spring cron expression. Omit for a one-time immediate run |
| `priority` | Integer | ✅ | `1` = HIGH, `2` = MEDIUM, `3` = LOW |

**Cron Examples:**
```
"0 0 9 * * *"          → Every day at 9:00 AM
"0 */5 * * * *"        → Every 5 minutes
"0 0 18 * * MON-FRI"   → Weekdays at 6:00 PM
```

---

### `GET /api/jobs` — List All Jobs

**Response:**
```json
[
  {
    "id": "uuid-abc-123",
    "name": "Daily Report",
    "cron": "0 0 9 * * *",
    "status": "RUNNING",
    "priority": 1,
    "workerNodeId": "node-8081",
    "retryCount": 0,
    "maxRetry": 3,
    "nextRunTime": "2024-01-16T09:00:00",
    "lastRunTime": "2024-01-15T09:00:00"
  }
]
```

**Job Status Flow:**
```
PENDING → RUNNING → DONE     (completed successfully)
                 → FAILED    (error occurred, will retry)
                 → DEAD      (max retries reached, no more attempts)
```

---

### `GET /api/jobs/{id}` — Get One Job

Response: `200 OK` with job object, or `404 Not Found`.

---

### `DELETE /api/jobs/{id}` — Cancel a Job

Response: `204 No Content`.

---

### `GET /api/nodes` — List All Nodes

```json
[
  {
    "nodeId": "node-8080",
    "status": "ALIVE",
    "isLeader": true,
    "activeJobs": 3,
    "lastHeartbeat": "2024-01-15T09:00:05"
  },
  {
    "nodeId": "node-8081",
    "status": "ALIVE",
    "isLeader": false,
    "activeJobs": 2,
    "lastHeartbeat": "2024-01-15T09:00:04"
  }
]
```

---

### `GET /api/dashboard` — Cluster Statistics

```json
{
  "totalJobs": 42,
  "runningJobs": 5,
  "completedJobs": 35,
  "failedJobs": 1,
  "deadJobs": 1,
  "aliveNodes": 3,
  "deadNodes": 0
}
```

---

## 🧪 Running Tests

```bash
mvn test
```

### Test Coverage by Layer

| Test Class | Type | What It Tests |
|---|---|---|
| `ConsistentHashingTest` | Unit | Hash ring routing correctness |
| `DistributedLockTest` | Unit (Mockito) | Lock acquisition and contention |
| `JobSchedulerServiceTest` | Unit (Mockito) | Leader-only scheduling logic |
| `FailoverServiceTest` | Unit (Mockito) | Dead node detection and job reassignment |
| `JobControllerTest` | Slice (`@WebMvcTest`) | HTTP request/response behavior |
| `JobRepositoryTest` | Integration (`@DataJpaTest`) | Custom JPQL queries against real H2 DB |

### Testing Best Practices Applied

- `@ExtendWith(MockitoExtension.class)` — fast unit tests, no Spring context loaded
- `@WebMvcTest` — tests only the web layer, service layer is mocked
- `@DataJpaTest` — tests only JPA layer using H2 in-memory database
- BDD-style naming: `givenX_whenY_thenZ()` for clarity
- `@Nested` classes to group related test scenarios
- `TestEntityManager.flush().clear()` to bypass first-level JPA cache in integration tests

---

## 📊 Dashboard UI

The Angular dashboard auto-refreshes every 5 seconds and displays:

- **Summary Cards:** Total Jobs, Running, Completed, Active Nodes count
- **Node Table:** Node ID, Leader/Follower badge, ALIVE/DEAD status, last heartbeat timestamp
- **Job Table:** Job name, status badge, assigned node, priority, last run, next scheduled run

**Local multi-node setup — each Angular instance targets a specific node:**

| Dashboard URL | Backend Node |
|---|---|
| http://localhost:4200 | node-8080 |
| http://localhost:4201 | node-8081 |
| http://localhost:4202 | node-8082 |

**Docker setup — single URL, Nginx handles routing:**

| Dashboard URL | What Happens |
|---|---|
| http://localhost:4200 | Angular UI served by Nginx |
| http://localhost:4200/api/... | Nginx proxies to Spring Boot on 8080 |

---

## ⚙️ Configuration Reference

`src/main/resources/application.properties`

```properties
# Database connection
spring.datasource.url=jdbc:mysql://localhost:3306/job_scheduler
spring.datasource.username=root
spring.datasource.password=your_password

# Node identity — always pass via environment variable
node.id=${NODE_ID:node-default}
server.port=${PORT:8080}

# How often (ms) the leader polls for due jobs
scheduler.check-interval=1000

# How often (ms) each node broadcasts its heartbeat
scheduler.heartbeat-interval=5000

# Seconds of silence before a node is declared DEAD
scheduler.dead-threshold=15
```

---

## ❓ FAQ

**Q: What happens if the Leader node crashes?**
> Followers detect no heartbeat within 15 seconds. One follower automatically claims leadership and resumes assigning jobs. No jobs are lost.

**Q: Can two nodes run the same job simultaneously?**
> No. The distributed lock uses a conditional atomic SQL UPDATE (`WHERE locked_by IS NULL`). Only the node that modifies 1 row wins the lock. All others skip the job.

**Q: Why MySQL for coordination instead of Redis or ZooKeeper?**
> By design. Most enterprise systems already run MySQL. This project shows you can implement robust distributed primitives — leader election, distributed locks — on a plain relational database with zero extra infrastructure.

**Q: What does `nginx.conf` do in Docker?**
> It serves two roles: (1) serves the compiled Angular app as static files, and (2) acts as a reverse proxy — forwarding all `/api/` requests to the Spring Boot container. This eliminates CORS issues and means the frontend and API share the same origin.

**Q: Why does each node need a unique `NODE_ID`?**
> The `NODE_ID` is each node's cluster identity. It's used for heartbeat tracking, leader election, consistent hash ring position, and failover job reassignment. Without a unique ID, nodes are indistinguishable in the shared database.

**Q: What happens when a job fails and retries?**
> `retryCount` is incremented and the job is reset to `PENDING` to go through the normal assignment cycle again. Once `retryCount >= maxRetry` (default: 3), the job is permanently marked `DEAD` and ignored by the scheduler.

**Q: Can I add more nodes at runtime?**
> Yes. Just start a new instance with a unique `PORT` and `NODE_ID`. It registers itself via heartbeat, the leader detects it as a new `ALIVE` node, and it's immediately added to the consistent hash ring and starts receiving jobs.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
