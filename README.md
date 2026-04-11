# Distributed Job Scheduler

A powerful, high-availability distributed job scheduler built with Spring Boot and MySQL.

## Features
- **Leader Election**: Automated leader election ensures only one node manages job assignments.
- **Distributed Locking**: Atomic database-backed locks prevent duplicate job execution across nodes.
- **Consistent Hashing**: Efficiently maps jobs to nodes, minimizing redistribution overhead when nodes join/leave.
- **Auto Failover**: Detects dead nodes via heartbeats and automatically reassigns orphaned jobs.
- **Real-time Dashboard**: Premium UI for monitoring node health and job status.

## Architecture
- Nodes communicate via a shared MySQL database.
- Each node runs its own `WorkerPool` with a fixed thread pool for job execution.
- Heartbeats are sent every 5 seconds. Nodes not responding for 15 seconds are marked as DEAD.

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL (Database: `job_scheduler`, User: `root`, Password: `password`)

### Step 1: Database Setup
```sql
CREATE DATABASE job_scheduler;
-- Ensure root or your specific user has access
```

### Step 2: Build
```bash
mvn clean package -DskipTests
```

### Step 3: Run Multiple Nodes
Open 3 terminals and run:

**Node 1 (Leader Candidate)**
```bash
NODE_ID=node-8081 PORT=8081 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar
```

**Node 2**
```bash
NODE_ID=node-8082 PORT=8082 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar
```

**Node 3**
```bash
NODE_ID=node-8083 PORT=8083 java -jar target/distributed-job-scheduler-0.0.1-SNAPSHOT.jar
```

### Step 4: Access Dashboard
Open `http://localhost:8081/dashboard` in your browser.

### Step 5: Submit a Job
```bash
curl -X POST http://localhost:8081/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"name":"Email Backup Service", "cronExpression":"0 */5 * * *", "priority": 1}'
```

## API Documentation
- `POST /api/jobs`: Submit a new job.
- `GET /api/jobs`: List all jobs.
- `GET /api/nodes`: List cluster nodes.
- `GET /api/dashboard`: Get real-time stats.
