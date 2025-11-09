
# ⚙️ QueueCTL — Production-Grade CLI Job Queue System (Java)

> **Author:** [Kaushik Muthumani](https://github.com/KaushikMuthumani)  
> **Tech Stack:** Java 21 · SQLite · Flyway · Spark Java · Picocli · Maven  
> **Domain:** Backend Systems · Job Scheduling  
> **Version:** 1.0.0  
> **Goal:** Build a self-contained, fault-tolerant background job queue demonstrating backend engineering depth.

---

## 🎯 **Overview**

**QueueCTL** is a **CLI-driven background job orchestration system** built completely in **Java**, designed to execute commands asynchronously with high reliability, persistence, and observability.

This project simulates the essential backbone of distributed background systems like **Celery**, **Sidekiq**, and **BullMQ** — but in a **minimal, production-style architecture**, demonstrating my capability to design and ship real backend infrastructure systems.

QueueCTL manages:
- Background job enqueuing and tracking
- Multi-worker parallel execution
- Automatic retry with exponential backoff
- Dead Letter Queue (DLQ) for failed jobs
- Persistent storage via SQLite
- Real-time monitoring dashboard (Spark Java)
- Clean, command-driven developer experience

---

## 🌟 **Why This Project Matters**

This project was built with **production thinking** — reliability, resilience, and observability — not just to pass a test, but to **prove readiness for backend engineering roles**.

✅ Fault-tolerant  
✅ Concurrent-safe  
✅ Persistent  
✅ Fully Observable  
✅ CLI + Dashboard Dual Interface  


---

## 🧩 **Core Problem & Solutions**

| Problem | Solution | Key Component |
|----------|-----------|---------------|
| Need reliable background job execution | CLI-based queue storing persistent jobs | `cli/EnqueueCmd.java`, `core/JobService.java` |
| Multiple workers executing concurrently | Thread pools + atomic DB locking | `core/WorkerService.java` |
| Jobs failing intermittently | Exponential backoff with capped retries | `core/Backoff.java` |
| Permanent failures after retries | Dead Letter Queue for failed jobs | `core/JobService.moveToDlq()` |
| Job recovery after crashes | Lease sweeper returning stuck jobs | `core/LeaseSweeper.java` |
| Observability & monitoring | Embedded Spark dashboard | `http/DashboardServer.java` |
| Resilient persistence | SQLite + Flyway migration | `db/DataSourceFactory.java` |

---

## 🏗️ **System Architecture**
                  ┌───────────────────────────┐
                  │      CLI (Picocli)        │
                  │  queuectl <subcommand>    │
                  └─────────────┬─────────────┘
                                │
                                ▼
                 ┌──────────────┴──────────────┐
                 │       Core Services         │
                 │  JobService | WorkerService │
                 │  QueueSvc   | BackoffPolicy │
                 │  Scheduler  | LeaseSweeper  │
                 └──────────────┬──────────────┘
                                │
                                ▼
                 ┌──────────────┴──────────────┐
                 │  SQLite Persistence Layer   │
                 │   Flyway schema migration   │
                 │   (jobs, dlq, logs, config) │
                 └──────────────┬──────────────┘
                                │
                                ▼
                 ┌──────────────┴──────────────┐
                 │  Dashboard (Spark Java)     │
                 │   /status /jobs /health     │
                 └─────────────────────────────┘
---

## 📁 **Folder Structure**
```
queue-ctl-FLAM-Backend-/
├── pom.xml # Maven build file
├── README.md # Project documentation
├── .gitignore # Ignored build artifacts
│
├── scripts/ # Utility & demo scripts
│ ├── demo.sh # Automated full system demo
│ └── clean.sh # Reset database & logs
│
├── docs/ # Visual proofs & logs
│ ├── screenshots/ # Screenshots & demo videos
│ └── proof/ # JSON + text outputs from demo.sh
│
├── src/main/java/com/queuectl/ # Core Java source
│ ├── cli/ (ConfigCmd, DlqCmd, EnqueueCmd, ListCmd, LogsCmd, QueueCmd, StatusCmd, WorkerCmd) # CLI commands
│ ├── core/ (Backoff, CommandExecutor, JobService, SchedulerService, WorkerService, LeaseSweeper, QueueService, RateLimiter, Idempotency) # Core backend logic
│ ├── http/ (DashboardServer) # Web dashboard server
│ ├── db/ (DataSourceFactory, Tx) # Database utilities
│ └── model/ (Job, JobState, QueueCtl) # Data models
│
└── src/main/resources/db/migration/
└── V1__init.sql # Database schema migration
```

---

## 🔄 **Job Lifecycle**

| State | Description |
|--------|-------------|
| `pending` | Waiting for worker to claim |
| `processing` | Currently executing |
| `completed` | Executed successfully |
| `failed` | Failed but retryable |
| `dead` | Moved to DLQ after retries |

### Visual Flow
```
enqueue → pending
↓
worker picks → processing
↓
success → completed
failure → backoff delay → retry
↓
retries exhausted → DLQ

```

---

## 💻 **CLI Usage**

| Command | Description |
|----------|-------------|
| `queuectl enqueue '{"id":"j1","command":"echo Hello"}'` | Add a new job |
| `queuectl worker --start --queues default:3 --dashboard` | Start workers + dashboard |
| `queuectl status` | View system state |
| `queuectl list --state pending` | List pending jobs |
| `queuectl dlq list` | View DLQ |
| `queuectl dlq retry <job>` | Retry from DLQ |
| `queuectl config set max-retries 3` | Update retry settings |
| `queuectl logs <jobId>` | View job logs |

---

## 🧠 **Advanced Features (Bonus)**

| Feature | Description |
|----------|-------------|
| ⏱ **Timeout Handling** | Cancels long-running jobs automatically |
| 🧩 **Priority Queues** | Higher priority processed first |
| 📅 **Delayed Jobs** | Execute in future using `run_after` |
| ⏰ **Cron Jobs** | Repeating schedules (`*/1 * * * *`) |
| 💀 **Dead Letter Queue** | Failed jobs retried or analyzed |
| 📊 **Metrics Dashboard** | `/status` shows counts live |
| 🔁 **Backoff Policy** | Retry delays via exponential formula |
| 🧹 **Lease Sweeper** | Reclaims jobs after worker crash |
| 🧰 **Config Management** | Update runtime configs persistently |
| 💾 **Persistence Layer** | SQLite DB survives restarts |
| 🧠 **Rate Limiting** | Control per-queue concurrency |

---

## 🧪 Proof of Work: Demo Execution

You can either run a quick manual test or execute the automated demo script.

---

### 🧩 Step-by-Step Setup & Run Guide

#### 1️⃣ Clone the Repository
```bash

git clone https://github.com/KaushikMuthumani/queue-ctl-FLAM-Backend-.git
cd queue-ctl-FLAM-Backend-
```
2️⃣ Clean Build
```bash
mvn clean package -DskipTests
```
✅ Expected: BUILD SUCCESS → JAR available at target/queue-ctl.jar

3️⃣ Manual Sanity Check (CLI Only)
3.1 Enqueue Jobs

```bash
java -jar target/queue-ctl.jar enqueue '{"id":"ok1","queue":"default","command":"echo OK"}'
java -jar target/queue-ctl.jar enqueue '{"id":"bad1","queue":"default","command":"no_such_cmd","max_retries":2}'
java -jar target/queue-ctl.jar enqueue '{"id":"slow1","queue":"default","command":"sleep 2"}'
```
✅ Expected: Each job shows “Enqueued: id=...”

3.2 Check Queue Status

```bash
java -jar target/queue-ctl.jar status
java -jar target/queue-ctl.jar list --state pending
```
✅ Expected: At least one pending job.

3.3 Start Workers + Dashboard

```bash
java -jar target/queue-ctl.jar worker --start --queues default:3 --dashboard
```
✅ Expected: “Dashboard: http://localhost:8088”

In a new terminal:

```bash
curl -s http://localhost:8088/health
curl -s http://localhost:8088/status
curl -s http://localhost:8088/jobs
```
✅ Expected: ok and JSON job stats.

3.4 Observe Job Progress

```bash
java -jar target/queue-ctl.jar status
java -jar target/queue-ctl.jar list --state completed
java -jar target/queue-ctl.jar dlq list
```
✅ Expected: ok1, slow1 → completed,bad1 → retries → DLQ

3.5 Retry a DLQ Job (optional)

```bash
java -jar target/queue-ctl.jar dlq retry bad1
java -jar target/queue-ctl.jar list --state pending
```
3.6 Stop Workers Gracefully
In the worker terminal:
🛑 Press Ctrl + C to stop all workers.

4️⃣ Automated Demo (Recommended)
Runs the entire workflow end-to-end and saves proof logs.

```bash
chmod +x scripts/demo.sh
./scripts/demo.sh
✅ Artifacts saved: docs/proof/
✅ Dashboard: http://localhost:8088
```
## 🖼️ Screenshots (Visual Proof)

### Lifecycle (end-to-end)
| Stage | Screenshot | Description |
|------|------------|-------------|
| 🟢 Enqueued Jobs | ![Enqueued Jobs](docs/screenshots/pending.png) | Queue initialized with jobs in `pending`. |
| 🟡 Worker Processing | ![Worker Processing](docs/screenshots/processing.png) | Workers actively executing jobs. |
| 🟢 Completed Jobs | ![Completed Jobs](docs/screenshots/completed.png) | Successful executions visible in `completed`. |
| 🔴 Dead Letter Queue | ![Dead Letter Queue](docs/screenshots/dlq.png) | Failed job moved to DLQ after retries. |
| 🔁 DLQ Retry | ![DLQ Retry](docs/screenshots/dlq-retry.png) | DLQ job retried → re-enqueued. |

### Dashboard Proofs (HTTP)
| Endpoint | Screenshot | What it shows |
|---------|------------|----------------|
| `/health` | ![Dashboard Health](docs/screenshots/dashboard-health.png) | Service health probe returns `ok`. |
| `/status` | ![Dashboard Status](docs/screenshots/dashboard-status.png) | Live job counts JSON (pending/processing/completed/failed/dead). |
| `/jobs` | ![Dashboard Jobs](docs/screenshots/dashboard-jobs.png) | Recent jobs with states/commands. |
| `/jobs?state=pending` | ![Dashboard Pending](docs/screenshots/dashboard-pending.png) | Filtered view for `pending` state. |

### CLI Status Snapshot
![CLI Status](docs/screenshots/status.png)

---

## 🎥 Demo Video

> **Watch the complete workflow (build → enqueue → processing → DLQ → retry → dashboard):**

https://github.com/KaushikMuthumani/queuectl-FLAM-/blob/main/docs/screenshots/demo-video.mp4

<!-- If you prefer inline playback as well, keep this HTML block (works on GitHub pages and many viewers). -->
<video width="900" controls>
  <source src="docs/screenshots/demo-video.mp4" type="video/mp4">
  Your browser does not support the video tag. Here is a direct link:
  https://github.com/KaushikMuthumani/queuectl-FLAM-/blob/main/docs/screenshots/demo-video.mp4
</video>

<!-- Optional future GIF preview if you add one
![Live Demo](docs/demo/demo.gif)
-->


---

## ⚙️ System Internals

### 🧩 Job Execution Engine
- Executes via `ProcessBuilder`
- Captures `stdout`, `stderr`, and exit codes
- Handles timeouts via `Future.get(timeout, TimeUnit.SECONDS)`

### 🔁 Retry Logic
- Formula: `delay = base ^ attempts + jitter`
- Default base = `2`, capped at 30s
- Moves to DLQ after exceeding `max_retries`

### 💾 Persistence
- Schema managed by **Flyway** (`V1__init.sql`)
- Tables: `jobs`, `dlq`, `logs`, `config`
- All operations are **transaction-safe (ACID)**

### 👷 Worker Model
- Configurable worker threads per queue
- Atomic SQL locking guarantees *exactly-once* job claim
- Graceful shutdown via `Runtime.addShutdownHook()`

### ⚙️ Configuration
- CLI-managed config stored in SQLite
- Update live configuration:
  ```bash
  queuectl config set max-retries 5
---
## 🧱 Deliverables Summary

| Deliverable | Status | File / Module |
|--------------|--------|---------------|
| CLI system | ✅ | `QueueCtl.java`, `cli/*` |
| SQLite persistence | ✅ | `db/*`, `V1__init.sql` |
| Multi-worker pool | ✅ | `WorkerService.java` |
| Exponential backoff | ✅ | `Backoff.java` |
| DLQ handling | ✅ | `JobService.java` |
| Cron & delayed jobs | ✅ | `SchedulerService.java` |
| Metrics dashboard | ✅ | `DashboardServer.java` |
| Rate limiting | ✅ | `QueueCfg.java` |
| Lease sweeper | ✅ | `LeaseSweeper.java` |
| Logging & proof | ✅ | `scripts/demo.sh`, `docs/proof/` |

---

## 🧠 Learning Outcomes

- Gained **deep understanding** of asynchronous task orchestration.  
- Implemented **retry**, **exponential backoff**, **DLQ**, and **idempotency** mechanisms.  
- Hands-on experience with **Flyway**, **SparkJava**, and **Picocli**.  
- Designed and managed **multi-threaded concurrency**, **persistent storage**, and **system observability**.  
- Emulated **real production-grade fault-tolerance** and recovery mechanisms.  



## 🧾 Conclusion

QueueCTL is a production-grade asynchronous job queue combining reliability, concurrency, and persistence — built cleanly from scratch in Java.
It demonstrates a system-thinking mindset, backend design maturity, and an ability to ship robust, observable systems.




