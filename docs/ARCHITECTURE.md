# Architecture Documentation

## Overview

Restic Explorer is a Spring Boot 4 web application that provides a monitoring dashboard for restic backup repositories. It caches repository metadata in a database, provides automated scanning and integrity checking, and surfaces status through a web UI and health endpoints.

## System Architecture

```mermaid
graph TB
    subgraph "Browser"
        UI[Web Browser]
    end

    subgraph "Restic Explorer Application"
        subgraph "Web Layer"
            TC[Thymeleaf Controllers]
            SEC[Spring Security]
            ACT[Actuator Endpoints]
        end

        subgraph "Service Layer"
            AS[AdminService]
            ELS[ErrorLogService]
            RS[RepositoryService]
            GS[GroupService]
            SS[ScanService]
            CS[CheckService]
            RCS[ResticCommandService]
            RPC[RetentionPolicyChecker]
        end

        subgraph "Data Layer"
            AR[AdminUserRepository]
            ELR[ErrorLogRepository]
            RR[ResticRepositoryRepository]
            GR[RepositoryGroupRepository]
            SNR[SnapshotRepository]
            SRR[ScanResultRepository]
            CRR[CheckResultRepository]
        end

        subgraph "Restic Integration"
            RPI[ResticRepositoryProvider Interface]
            S3P[ResticS3Provider]
            AZP[ResticAzureProvider]
            SFTPP[ResticSftpProvider]
        end
    end

    subgraph "External Systems"
        DB[(Database<br/>H2 / PostgreSQL)]
        S3[S3 Storage]
        AZURE[Azure Blob Storage]
        SFTP[SFTP Server]
        RESTIC[Restic CLI]
    end

    UI -->|HTTP| TC
    UI -->|HTTP| ACT
    TC --> SEC
    TC --> AS
    TC --> ELS
    TC --> RS
    TC --> GS
    TC --> SS
    TC --> CS
    SS --> RCS
    SS --> RPC
    SS --> ELS
    CS --> RCS
    CS --> ELS
    RCS --> RPI
    RPI --> S3P
    RPI --> AZP
    RPI --> SFTPP
    S3P --> RESTIC
    AZP --> RESTIC
    SFTPP --> RESTIC
    RESTIC --> S3
    RESTIC --> AZURE
    RESTIC --> SFTP
    AS --> AR
    ELS --> ELR
    RS --> RR
    GS --> GR
    SS --> SNR
    SS --> SRR
    CS --> CRR
    AR --> DB
    ELR --> DB
    RR --> DB
    GR --> DB
    SNR --> DB
    SRR --> DB
    CRR --> DB
```

## Component Architecture

```mermaid
graph LR
    subgraph "Features"
        direction TB
        A[admin]
        B[repository]
        C[scanning]
        D[download]
        E[restic]
        F[health]
    end

    subgraph "Each Feature"
        direction TB
        W[web/ - Controllers, DTOs]
        S[Service Layer]
        DA[data/ - Entities, Repositories]
    end

    A --> W
    A --> S
    A --> DA
```

## Package Structure

The application follows a **feature-based package structure**:

| Package | Purpose |
|---|---|
| `o.r.r.admin` | Admin authentication, setup, password management, error log |
| `o.r.r.repository` | Restic repository CRUD, groups, properties |
| `o.r.r.scanning` | Scheduled scanning, integrity checks, retention policies, dashboard |
| `o.r.r.download` | Snapshot download (admin-only) |
| `o.r.r.restic` | Restic CLI integration, backend provider abstraction |
| `o.r.r.health` | Actuator health indicator |
| `o.r.r.config` | Security, web config, encryption, schema migration, utilities |

Each feature package contains up to three sub-packages:
- `web/` – Controllers, DTOs, form objects
- `data/` – JPA entities, Spring Data repositories
- Root package – Service classes

## Request Flow

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant Security as Spring Security
    participant Controller as DashboardController
    participant ScanSvc as ScanService
    participant CheckSvc as CheckService
    participant DB as Database

    User->>Browser: Navigate to Dashboard
    Browser->>Security: GET /
    Security->>Controller: dashboard()
    Controller->>ScanSvc: getSnapshotCount(id)
    ScanSvc->>DB: COUNT snapshots
    Controller->>ScanSvc: getLastScanResult(id)
    ScanSvc->>DB: SELECT TOP scan_result
    Controller->>CheckSvc: getLastCheckResult(id)
    CheckSvc->>DB: SELECT TOP check_result
    Controller-->>Browser: Render dashboard.html
    Browser-->>User: Display dashboard with status badges
```

## Scanning Flow

```mermaid
sequenceDiagram
    participant Scheduler as Spring Scheduler
    participant ScanService
    participant ResticCmd as ResticCommandService
    participant Provider as ResticRepositoryProvider
    participant CLI as restic binary
    participant ErrorLog as ErrorLogService
    participant RPC as RetentionPolicyChecker
    participant DB as Database

    Scheduler->>ScanService: scheduledScan()
    ScanService->>DB: findAllEnabled()

    loop For each repository due for scan
        ScanService->>DB: Save ScanResult(IN_PROGRESS)
        ScanService->>ResticCmd: listSnapshots(repo)
        ResticCmd->>Provider: buildEnvironment(repo)
        Provider-->>ResticCmd: env vars + extra args
        ResticCmd->>CLI: restic -r <url> snapshots --json
        CLI-->>ResticCmd: JSON snapshot list
        ResticCmd-->>ScanService: parsed snapshots

        ScanService->>DB: Delete old snapshots
        loop For each snapshot
            ScanService->>ResticCmd: getSnapshotStats(repo, id)
            ResticCmd->>CLI: restic stats <id> --json
            CLI-->>ResticCmd: size + file count
            ScanService->>DB: Save snapshot with stats
        end

        ScanService->>ResticCmd: getStats(repo)
        ScanService->>ResticCmd: listLocks(repo)
        ScanService->>RPC: check(repo, snapshots, today)
        RPC-->>ScanService: RetentionPolicyResult

        ScanService->>DB: Update ScanResult(SUCCESS, lockCount, retention)
        ScanService->>DB: Update repo.lastScanned

        alt Scan fails
            ScanService->>DB: Update ScanResult(FAILED)
            ScanService->>ErrorLog: logError(repoId, action, message, cause)
        end
    end
```

## Integrity Check Flow

```mermaid
sequenceDiagram
    participant Scheduler as Spring Scheduler
    participant CheckService
    participant ResticCmd as ResticCommandService
    participant ErrorLog as ErrorLogService
    participant DB as Database

    Scheduler->>CheckService: scheduledCheck()
    CheckService->>DB: findAllEnabled()

    loop For each repository due for check
        CheckService->>DB: Save CheckResult(IN_PROGRESS)
        CheckService->>ResticCmd: checkRepository(repo)
        ResticCmd->>ResticCmd: restic check --read-data
        alt Check succeeds
            CheckService->>DB: Update CheckResult(SUCCESS)
            CheckService->>DB: Update repo.lastChecked
        else Check fails
            CheckService->>DB: Update CheckResult(FAILED)
            CheckService->>ErrorLog: logError(repoId, "CHECK", message, cause)
        end
    end
```

## Security Model

```mermaid
graph TD
    subgraph "Public Access (No Auth)"
        A[Dashboard - GET / ]
        B[Repository List - GET /repositories]
        C[Snapshot List - GET /repositories/id/snapshots]
        C2[Snapshot Detail - GET /repositories/id/snapshots/sid]
        D[Actuator - GET /actuator/**]
        E[Setup - GET/POST /setup]
        F[Login - GET /login]
        G[Static Resources - /css /js /images]
    end

    subgraph "Admin Only (ROLE_ADMIN)"
        H[Add Repository - GET /repositories/new]
        I[Edit Repository - GET /repositories/id/edit]
        J[Delete Repository - POST /repositories/id/delete]
        K[Trigger Scan - POST /repositories/id/scan]
        K2[Trigger Check - POST /repositories/id/check]
        K3[Unlock Repo - POST /repositories/id/unlock]
        L[Download Snapshot - GET /download/**]
        M[Admin Panel - GET /admin/**]
        M2[Error Log - GET /admin/error-log]
        N[Groups - GET/POST /groups/**]
    end

    style A fill:#c8e6c9
    style B fill:#c8e6c9
    style C fill:#c8e6c9
    style C2 fill:#c8e6c9
    style D fill:#c8e6c9
    style E fill:#c8e6c9
    style F fill:#c8e6c9
    style G fill:#c8e6c9
    style H fill:#ffcdd2
    style I fill:#ffcdd2
    style J fill:#ffcdd2
    style K fill:#ffcdd2
    style K2 fill:#ffcdd2
    style K3 fill:#ffcdd2
    style L fill:#ffcdd2
    style M fill:#ffcdd2
    style M2 fill:#ffcdd2
    style N fill:#ffcdd2
```

## Data Model

```mermaid
erDiagram
    ADMIN_USERS {
        bigint id PK
        varchar username UK
        varchar password
    }

    ERROR_LOG_ENTRIES {
        bigint id PK
        timestamp timestamp
        bigint repository_id
        varchar repository_name
        varchar action
        text error_message
        text stack_trace
    }

    REPOSITORY_GROUPS {
        bigint id PK
        varchar name UK
    }

    RESTIC_REPOSITORIES {
        bigint id PK
        varchar name
        varchar type
        varchar url
        varchar repository_password
        int scan_interval_minutes
        int check_interval_minutes
        timestamp last_scanned
        timestamp last_checked
        boolean enabled
        bigint group_id FK
        text comment
        int keep_daily
        int keep_weekly
        int keep_monthly
        int keep_yearly
        int keep_last
        timestamp created_at
        timestamp updated_at
    }

    REPOSITORY_PROPERTIES {
        bigint repository_id FK
        varchar property_key
        varchar property_value
    }

    SNAPSHOTS {
        bigint id PK
        bigint repository_id FK
        varchar snapshot_id
        varchar hostname
        varchar username
        text paths
        text tags
        timestamp snapshot_time
        text tree_hash
        bigint total_size
        bigint total_file_count
        timestamp cached_at
    }

    SCAN_RESULTS {
        bigint id PK
        bigint repository_id FK
        varchar status
        text message
        int snapshot_count
        bigint total_size
        bigint total_file_count
        int lock_count
        boolean retention_policy_fulfilled
        text retention_policy_violations
        timestamp scanned_at
    }

    CHECK_RESULTS {
        bigint id PK
        bigint repository_id FK
        varchar status
        text message
        timestamp checked_at
    }

    REPOSITORY_GROUPS ||--o{ RESTIC_REPOSITORIES : "has many"
    RESTIC_REPOSITORIES ||--o{ REPOSITORY_PROPERTIES : "has many"
    RESTIC_REPOSITORIES ||--o{ SNAPSHOTS : "has many"
    RESTIC_REPOSITORIES ||--o{ SCAN_RESULTS : "has many"
    RESTIC_REPOSITORIES ||--o{ CHECK_RESULTS : "has many"
```

## Extensibility: Adding New Repository Types

The restic integration uses the **Strategy Pattern** via the `ResticRepositoryProvider` interface:

```mermaid
classDiagram
    class ResticRepositoryProvider {
        <<interface>>
        +getType() String
        +buildEnvironment(ResticRepository) Map
        +buildRepositoryUrl(ResticRepository) String
        +buildExtraArguments(ResticRepository) List~String~
    }

    class ResticS3Provider {
        +getType() "S3"
        +buildEnvironment() AWS_ACCESS_KEY_ID, etc.
    }

    class ResticAzureProvider {
        +getType() "AZURE"
        +buildEnvironment() AZURE_ACCOUNT_NAME, etc.
    }

    class ResticSftpProvider {
        +getType() "SFTP"
        +buildEnvironment() RESTIC_PASSWORD or RESTIC_PASSWORD_COMMAND
        +buildExtraArguments() -o sftp.command=...
    }

    class ResticRestProvider {
        +getType() "REST"
        +buildEnvironment() RESTIC_PASSWORD
        +buildRepositoryUrl() injects user:pass into URL
    }

    ResticRepositoryProvider <|.. ResticS3Provider
    ResticRepositoryProvider <|.. ResticAzureProvider
    ResticRepositoryProvider <|.. ResticSftpProvider
    ResticRepositoryProvider <|.. ResticRestProvider

    class ResticCommandService {
        -providers Map~String, ResticRepositoryProvider~
        +listSnapshots(ResticRepository) List
        +getStats(ResticRepository) Map
        +getSnapshotStats(ResticRepository, String) Map
        +checkRepository(ResticRepository) String
        +listLocks(ResticRepository) List~String~
        +unlockRepository(ResticRepository) String
        +downloadSnapshot(ResticRepository, String) InputStream
    }

    ResticCommandService --> ResticRepositoryProvider : uses
```

To add a new repository type:

1. Add a new value to `RepositoryType` enum
2. Add any backend-specific keys to `RepositoryPropertyKey` enum
3. Create a new `ResticRepositoryProvider` implementation annotated with `@Component`
4. Update the UI form template to show type-specific fields
5. If using PostgreSQL, the `SchemaFixRunner` will automatically reconcile check constraints

## Backend Properties Architecture

Backend-specific configuration (S3 keys, Azure credentials, SFTP commands) is stored as a `Map<RepositoryPropertyKey, String>` on `ResticRepository`, persisted in a separate `repository_properties` table. Sensitive properties (marked via `RepositoryPropertyKey.isSensitive()`) are encrypted/decrypted manually in `RepositoryService` using `EncryptionService`.

| Backend | Property Keys | Sensitive |
|---|---|---|
| S3 | `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION` | Access Key, Secret Key |
| Azure | `AZURE_ACCOUNT_NAME`, `AZURE_ACCOUNT_KEY`, `AZURE_ENDPOINT_SUFFIX` | Account Key |
| SFTP | `SFTP_PASSWORD_COMMAND`, `SFTP_COMMAND` | None |
| REST | `REST_USERNAME`, `REST_PASSWORD` | Password |

## UI Architecture

- **Layout**: Thymeleaf fragment-based layout (`fragments/layout.html`) with navbar, footer, and shared head
- **Dark Mode**: Auto-detected via `prefers-color-scheme` media query, applied via Bootstrap 5.3 `data-bs-theme`
- **Pagination**: Reusable Thymeleaf fragment (`fragments/pagination.html`) with sortable headers and page controls
- **Internationalization**: All text from `messages.properties`; add `messages_xx.properties` for new locales
- **Templates**: Follow controller path convention — `scanning/dashboard`, `scanning/snapshots`, `scanning/snapshot-detail`, `repository/form`, `admin/index`, `admin/error-log`, `group/list`

## Deployment Architecture

```mermaid
graph TB
    subgraph "Docker Compose"
        subgraph "App Container (Eclipse Temurin 21 JRE Alpine)"
            JAVA[Java 21 JRE]
            RESTIC[Restic Binary]
            SSH[OpenSSH Client]
            APP[Restic Explorer JAR]
            SSHDIR["/app/ssh – mounted keys"]
        end

        subgraph "Database Container"
            PG[PostgreSQL 16]
        end
    end

    APP --> PG
    APP --> RESTIC
    RESTIC -->|S3 API| S3[S3 Storage]
    RESTIC -->|Azure API| AZ[Azure Blob]
    RESTIC -->|SSH/SFTP| SFTP[SFTP Server]

    subgraph "Ansible"
        PLAY[deploy.yml]
    end

    PLAY -->|Deploys| APP
    PLAY -->|Deploys| PG
```

## Technology Stack

| Component | Technology |
|---|---|
| Backend Framework | Spring Boot 4.0 |
| Template Engine | Thymeleaf |
| CSS Framework | Bootstrap 5.3 |
| Icons | Bootstrap Icons |
| Database (dev) | H2 (file-based) |
| Database (prod) | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate 7 |
| Security | Spring Security 7 |
| Monitoring | Spring Actuator |
| Build Tool | Maven |
| Code Generation | Lombok |
| Containerization | Docker (multi-stage build) |
| Deployment | Docker Compose, Ansible |
| Backup Tool | Restic CLI |

## Schema Migration

The `SchemaFixRunner` (in `config/`) runs on startup to reconcile Hibernate-generated check constraints on PostgreSQL for `@Enumerated(STRING)` columns. When new enum values are added (e.g. a new `RepositoryType` or `RepositoryPropertyKey`), `ddl-auto=update` does not update existing constraints. The runner replaces stale constraints with current values. This will be removed in version 1.0.

