# Architecture Documentation

## Overview

Restic Explorer is a Spring Boot web application that provides a dashboard for managing and browsing restic backup repositories. It caches repository metadata in a lightweight database and offers both automated and manual scanning capabilities.

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
            RS[RepositoryService]
            SS[ScanService]
            RCS[ResticCommandService]
        end

        subgraph "Data Layer"
            AR[AdminUserRepository]
            RR[ResticRepositoryRepository]
            SNR[SnapshotRepository]
            SRR[ScanResultRepository]
        end

        subgraph "Restic Integration"
            RPI[ResticRepositoryProvider Interface]
            S3P[ResticS3Provider]
            FP[Future Providers...]
        end
    end

    subgraph "External Systems"
        DB[(Database<br/>H2 / PostgreSQL)]
        S3[S3 Storage]
        RESTIC[Restic CLI]
    end

    UI -->|HTTP| TC
    UI -->|HTTP| ACT
    TC --> SEC
    TC --> AS
    TC --> RS
    TC --> SS
    SS --> RCS
    RCS --> RPI
    RPI --> S3P
    RPI -.-> FP
    S3P --> RESTIC
    RESTIC --> S3
    AS --> AR
    RS --> RR
    SS --> SNR
    SS --> SRR
    AR --> DB
    RR --> DB
    SNR --> DB
    SRR --> DB
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
| `org.remus.resticexplorer.admin` | Admin authentication, setup, password management |
| `org.remus.resticexplorer.repository` | Restic repository CRUD management |
| `org.remus.resticexplorer.scanning` | Scheduled scanning, metadata caching, dashboard |
| `org.remus.resticexplorer.download` | Snapshot download (admin-only) |
| `org.remus.resticexplorer.restic` | Restic CLI integration, provider abstraction |
| `org.remus.resticexplorer.health` | Actuator health indicator |
| `org.remus.resticexplorer.config` | Security and web configuration |

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
    participant Controller as Thymeleaf Controller
    participant Service as Service Layer
    participant DB as Database
    participant Restic as Restic CLI

    User->>Browser: Navigate to Dashboard
    Browser->>Security: GET /
    Security->>Security: Check authentication
    Security->>Controller: DashboardController.dashboard()
    Controller->>Service: RepositoryService.findAll()
    Service->>DB: SELECT * FROM restic_repositories
    DB-->>Service: Repository list
    Controller->>Service: ScanService.getSnapshotCount(id)
    Service->>DB: COUNT snapshots
    DB-->>Service: Snapshot count
    Controller-->>Browser: Render dashboard.html
    Browser-->>User: Display dashboard
```

## Scanning Flow

```mermaid
sequenceDiagram
    participant Scheduler as Spring Scheduler
    participant ScanService
    participant RepoService as RepositoryService
    participant ResticCmd as ResticCommandService
    participant S3Provider as ResticS3Provider
    participant CLI as restic binary
    participant DB as Database

    Scheduler->>ScanService: scheduledScan()
    ScanService->>RepoService: findAllEnabled()
    RepoService-->>ScanService: enabled repositories

    loop For each repository due for scan
        ScanService->>ScanService: shouldScan(repo)?
        ScanService->>DB: Save ScanResult(IN_PROGRESS)
        ScanService->>ResticCmd: listSnapshots(repo)
        ResticCmd->>S3Provider: buildEnvironment(repo)
        S3Provider-->>ResticCmd: env vars (AWS keys, etc.)
        ResticCmd->>CLI: restic -r <url> snapshots --json
        CLI-->>ResticCmd: JSON snapshot list
        ResticCmd-->>ScanService: parsed snapshots

        ScanService->>DB: Delete old snapshots for repo
        ScanService->>DB: Save new snapshots

        ScanService->>ResticCmd: getStats(repo)
        ResticCmd->>CLI: restic -r <url> stats --json
        CLI-->>ResticCmd: JSON stats
        ResticCmd-->>ScanService: parsed stats

        ScanService->>DB: Update ScanResult(SUCCESS)
        ScanService->>DB: Update repo.lastScanned
    end
```

## Security Model

```mermaid
graph TD
    subgraph "Public Access (No Auth)"
        A[Dashboard - GET /]
        B[Repository List - GET /repositories]
        C[Snapshot List - GET /repositories/id/snapshots]
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
        L[Download Snapshot - GET /download/**]
        M[Admin Panel - GET /admin/**]
    end

    style A fill:#c8e6c9
    style B fill:#c8e6c9
    style C fill:#c8e6c9
    style D fill:#c8e6c9
    style E fill:#c8e6c9
    style F fill:#c8e6c9
    style G fill:#c8e6c9
    style H fill:#ffcdd2
    style I fill:#ffcdd2
    style J fill:#ffcdd2
    style K fill:#ffcdd2
    style L fill:#ffcdd2
    style M fill:#ffcdd2
```

## Data Model

```mermaid
erDiagram
    ADMIN_USERS {
        bigint id PK
        varchar username UK
        varchar password
    }

    RESTIC_REPOSITORIES {
        bigint id PK
        varchar name
        varchar type
        varchar url
        varchar repository_password
        varchar s3_access_key
        varchar s3_secret_key
        varchar s3_region
        int scan_interval_minutes
        timestamp last_scanned
        boolean enabled
        timestamp created_at
        timestamp updated_at
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
        timestamp scanned_at
    }

    RESTIC_REPOSITORIES ||--o{ SNAPSHOTS : "has many"
    RESTIC_REPOSITORIES ||--o{ SCAN_RESULTS : "has many"
```

## Extensibility: Adding New Repository Types

The restic integration is built on the **Strategy Pattern** via the `ResticRepositoryProvider` interface:

```mermaid
classDiagram
    class ResticRepositoryProvider {
        <<interface>>
        +getType() String
        +buildEnvironment(ResticRepository) Map
        +buildRepositoryUrl(ResticRepository) String
    }

    class ResticS3Provider {
        +getType() String
        +buildEnvironment(ResticRepository) Map
        +buildRepositoryUrl(ResticRepository) String
    }

    class ResticSftpProvider {
        +getType() String
        +buildEnvironment(ResticRepository) Map
        +buildRepositoryUrl(ResticRepository) String
    }

    class ResticLocalProvider {
        +getType() String
        +buildEnvironment(ResticRepository) Map
        +buildRepositoryUrl(ResticRepository) String
    }

    ResticRepositoryProvider <|.. ResticS3Provider
    ResticRepositoryProvider <|.. ResticSftpProvider : future
    ResticRepositoryProvider <|.. ResticLocalProvider : future

    class ResticCommandService {
        -providers Map
        +listSnapshots(ResticRepository) List
        +getStats(ResticRepository) Map
        +downloadSnapshot(ResticRepository, String) InputStream
    }

    ResticCommandService --> ResticRepositoryProvider : uses
```

To add a new repository type:

1. Add a new value to `RepositoryType` enum
2. Create a new `ResticRepositoryProvider` implementation annotated with `@Component`
3. Add any type-specific fields to the `ResticRepository` entity
4. Update the UI form to show type-specific fields

## Deployment Architecture

```mermaid
graph TB
    subgraph "Docker Compose"
        subgraph "App Container"
            JAVA[Java 21 JRE]
            RESTIC[Restic Binary]
            APP[Restic Explorer JAR]
        end

        subgraph "Database Container"
            PG[PostgreSQL 16]
        end
    end

    APP --> PG
    APP --> RESTIC
    RESTIC -->|S3 API| S3[S3 Storage]

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
| Database (dev) | H2 (file-based) |
| Database (prod) | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security 7 |
| Monitoring | Spring Actuator |
| Build Tool | Maven |
| Code Generation | Lombok |
| Containerization | Docker, Docker Compose |
| Deployment | Ansible |
| Backup Tool | Restic CLI |
