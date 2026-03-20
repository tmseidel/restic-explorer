# Restic Explorer

![Restic Explorer Logo](src/main/resources/static/images/logo.svg)

A web-based dashboard for managing and exploring [restic](https://restic.net/) backup repositories. Built with Spring Boot, Thymeleaf, and Bootstrap 5.

## Features

- **Repository Management** – CRUD operations for restic backup repositories (S3, Azure Blob Storage, and SFTP supported; extensible architecture for additional backends)
- **Automated Scanning** – Configurable scheduled scans to cache restic metadata
- **Integrity Checks** – Scheduled restic consistency and integrity verification (`restic check`) with configurable intervals per repository
- **Dashboard** – Overview of all repositories, snapshot counts, scan and integrity check status
- **Snapshot Browser** – View all snapshots with details (hostname, paths, tags, timestamps)
- **Snapshot Download** – Admin-only download of specific snapshots as tar archives
- **Single Admin Account** – Simple authentication with password setup on first launch
- **Encrypted Sensitive Data** – Repository passwords and backend credentials encrypted at rest using AES-256-GCM
- **Health Monitoring** – Spring Actuator endpoint reporting restic metadata cache and integrity check status
- **Internationalization** – All UI text externalized via message bundles; add new languages by adding `messages_xx.properties`
- **Responsive UI** – Modern, mobile-friendly design using Bootstrap 5 and Thymeleaf

## Screenshots
### Dashboard
The dashboard provides a high-level overview of all configured repositories, including the total number of snapshots and the status of the last scan (OK, Failed, Pending). Admins can quickly navigate to the snapshot browser or trigger a manual scan.
![Dashboard](docs/screenshot_dashboard.png)
### Snapshot Browser
The snapshot browser lists all cached snapshots for a selected repository, showing key details such as snapshot ID, timestamp, hostname, paths, and tags. Admins can trigger a re-scan or download specific snapshots directly from this interface.
![Snapshots](docs/screenshot_snapshots.png)
### Snapshot Details
Clicking on a snapshot opens a detailed view with all metadata and a download option (admin only) for that snapshot.
![Snapshots-Details](docs/screenshot_snapshot.png)

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- [restic](https://restic.readthedocs.io/en/stable/020_installation.html) installed on the system

### Run Locally

```bash
# Clone the repository
git clone https://github.com/tmseidel/restic-explorer.git
cd restic-explorer

# Build and run
mvn spring-boot:run
```

The application starts on [http://localhost:8080](http://localhost:8080). On first launch, you will be redirected to the setup page to create the admin account.

### Run with Docker

```bash
# Build and start with Docker Compose (includes PostgreSQL)
docker compose up --build -d
```

The application will be available at [http://localhost:8080](http://localhost:8080).

### Run with Docker Hub Image

A pre-built image is available on Docker Hub at [`tmseidel/restic-explorer`](https://hub.docker.com/r/tmseidel/restic-explorer).

Create a `docker-compose.yml`:

```yaml
services:
  app:
    image: tmseidel/restic-explorer:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: resticexplorer
      DB_USER: resticexplorer
      DB_PASSWORD: resticexplorer
      RESTIC_ENCRYPTION_KEY: # optional, generate with: openssl rand -base64 32
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped
    volumes:
      - app-data:/app/data

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: resticexplorer
      POSTGRES_USER: resticexplorer
      POSTGRES_PASSWORD: resticexplorer
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U resticexplorer"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

volumes:
  app-data:
  db-data:
```

Then start it:

```bash
docker compose up -d
```

The application will be available at [http://localhost:8080](http://localhost:8080).

## User Guide

### 1. Initial Setup

On first launch, you are redirected to the **Setup** page:
1. Enter and confirm an admin password (minimum 8 characters)
2. Click **Create Admin Account**
3. Log in with username `admin` and the password you just set

### 2. Adding a Repository

1. Log in as admin
2. Navigate to **Repositories** → **Add Repository**
3. Fill in the form:
   - **Name**: A friendly display name
   - **Enabled**: Toggle to enable or disable the repository. When disabled, automatic scanning and integrity checks are paused.
   - **Group**: Optionally assign the repository to a group for organizing the dashboard
   - **Repository Type**: Select the backend type (see [Supported Repository Connectors](#supported-repository-connectors))
   - **Repository URL**: The restic repository URL (format depends on the backend type)
   - **Repository Password**: The encryption password for the restic repository
   - **Backend-specific settings**: Depending on the selected type (S3 credentials, Azure account details, or SFTP command options)
   - **Retention Policy** *(optional)*: Define expected backup frequency (see [Retention Policies](#9-retention-policies))
   - **Comment**: Optional notes or description for the repository
   - **Scan Interval**: How often (in minutes) to automatically scan for new snapshots
   - **Check Interval**: How often (in minutes) to run `restic check` for integrity verification (set to `0` to disable)
4. Click **Save**

### Supported Repository Connectors

| Connector | Repository Type | URL Format | Additional Settings |
|---|---|---|---|
| **Amazon S3 / S3-Compatible** | `S3` | `s3:https://s3.amazonaws.com/bucket/path` or `s3:https://custom-endpoint/bucket/path` | Access Key, Secret Key, Region |
| **Microsoft Azure Blob Storage** | `AZURE` | `azure:container-name:/path` | Account Name, Account Key, Endpoint Suffix |
| **SFTP** | `SFTP` | `sftp:user@host:/path/to/repo` or `sftp://user@host:port//path/to/repo` | Password Command (optional), SFTP Command (optional) |

#### SFTP Connector Details

The SFTP connector allows browsing restic repositories stored on remote servers accessible via SSH/SFTP.

- **Repository URL**: Use the standard restic SFTP URL format, e.g. `sftp:user@host:/srv/restic-repo`
- **Password Command** *(optional)*: A shell command that prints the repository password to stdout (e.g. `cat /path/to/password-file`). When set, this takes precedence over the Repository Password field.
- **SFTP Command** *(optional)*: A custom SSH command used for the SFTP connection. Use this to specify a private key for key-based authentication, e.g. `ssh user@host -i /root/.ssh/id_rsa -s sftp`.

#### Mounting SSH Private Keys in Docker

When using the SFTP connector in a Docker deployment, the container needs access to the SSH private key file on the host machine. Mount the key file (or the `.ssh` directory) into the container:

**Docker Compose** — add a bind-mount volume to the `app` service in `docker-compose.yml`:

```yaml
services:
  app:
    image: tmseidel/restic-explorer:latest
    # ... other settings ...
    volumes:
      - app-data:/app/data
      - /home/youruser/.ssh/id_rsa:/root/.ssh/id_rsa:ro
```

**Docker Run** — use the `-v` flag:

```bash
docker run -d \
  -p 8080:8080 \
  -v /home/youruser/.ssh/id_rsa:/root/.ssh/id_rsa:ro \
  tmseidel/restic-explorer:latest
```

Then, in the SFTP repository configuration, set the **SFTP Command** to reference the mounted key path inside the container:

```
ssh user@host -i /root/.ssh/id_rsa -s sftp
```

> ⚠️ **Tip**: Mount the key as read-only (`:ro`) for security. Ensure the file permissions on the host key are restrictive (`chmod 600`).

### 3. Dashboard

The dashboard shows:
- Total number of repositories and snapshots
- Per-repository scan status (OK, Failed, Pending)
- Per-repository integrity check status (OK, Failed, Pending, or disabled)
- Quick actions to view snapshots, trigger a manual scan, or run an integrity check

### 4. Browsing Snapshots

Click on a repository name or the eye icon to see all cached snapshots:
- Snapshot ID, timestamp, hostname, paths, and tags
- Admins can trigger a re-scan, run an integrity check, or download a snapshot

### 5. Downloading Snapshots

> **Admin only**: Only logged-in admins can download snapshots.

Click the download icon next to any snapshot to download it as a `.tar` archive via `restic dump`.

### 6. Administration

Navigate to **Admin** to:
- Change the admin password
- View system information and actuator health link

### 7. Health & Monitoring

The application exposes Spring Actuator endpoints:

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health including restic metadata status |
| `GET /actuator/info` | Application information |
| `GET /actuator/metrics` | Application metrics |

The custom `resticMetadata` health indicator reports:
- Total repositories and cached snapshots
- Per-repository scan status and last scan time
- Per-repository integrity check status and last check time
- Per-repository retention policy status and violations (when a policy is configured)
- Overall status: UP (all scans and checks successful), DOWN (any scan or check failed), UNKNOWN (no repositories)

### 8. Integrity Checks

Restic Explorer can periodically run `restic check --read-data` to verify the consistency and integrity of your backup repositories.

#### Configuration

- When adding or editing a repository, set the **Check Interval** field to a non-zero value (in minutes) to enable scheduled integrity checks.
- Set it to `0` to disable automatic integrity checks for that repository.

#### Manual Trigger

Admins can trigger an integrity check at any time from the **Dashboard** or the **Snapshots** page by clicking the **Check Now** button.

#### Status Reporting

- **Dashboard**: Each repository shows an integrity check status badge (OK, Failed, Pending, or Disabled) alongside the scan status.
- **Snapshots page**: The repository info section displays the last check time and result.
- **Health endpoint**: `GET /actuator/health` includes per-repository integrity check status and last check time.

> ⚠️ `restic check --read-data` reads **all data** in the repository, which can take a long time and generate significant network traffic for large repositories. Choose the check interval accordingly.

### 9. Retention Policies

Each repository can optionally have a **retention policy** that defines expected backup frequency. The policy is purely advisory — it never deletes snapshots.

#### Configuration

When adding or editing a repository, fill in the optional **Retention Policy** fields:

| Field | Meaning |
|---|---|
| **Keep Daily** | Number of days in the recent past that must each have at least one snapshot |
| **Keep Weekly** | Number of weeks in the recent past that must each have at least one snapshot |
| **Keep Monthly** | Number of months in the recent past that must each have at least one snapshot |
| **Keep Yearly** | Number of years in the recent past that must each have at least one snapshot |
| **Keep Last** | Minimum total number of snapshots that must exist |

Leave all fields empty (or set to `0`) to disable the retention policy for a repository.

#### Semantics

- `keepDaily = 7` → There must be at least one snapshot for each of the last 7 days (today through 6 days ago).
- `keepWeekly = 4` → There must be at least one snapshot in each of the last 4 calendar weeks.
- `keepMonthly = 12` → At least one snapshot in each of the last 12 calendar months.
- `keepYearly = 2` → At least one snapshot in each of the last 2 calendar years.
- `keepLast = 10` → There must be at least 10 snapshots total.
- Fields set to `null` or `0` are **skipped** (not checked).

#### How Violations Are Surfaced

After each scan, the system evaluates the cached snapshots against the configured policy:

- **Dashboard**: Each repository shows a `Policy OK` (info/blue) or `Policy Warning` (amber/yellow) badge next to the scan status badge. No badge is shown if no policy is configured.
- **Snapshots page**: If the policy is violated, an amber warning banner at the top of the page lists each specific violation (e.g., *"keepDaily: Missing backup for 2026-03-12"*).

> ⚠️ **Retention violations are soft warnings, not errors.** They use amber/yellow (`bg-warning`), never red (`bg-danger`). They do **not** affect the overall dashboard status card at the top.

## Configuration

### Application Properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Server port |
| `restic.binary` | `restic` | Path to the restic binary |
| `restic.timeout` | `300` | Timeout in seconds for restic commands |
| `restic.scan.check-interval` | `60000` | Interval in ms to check for due scans |
| `restic.encryption.key` | *(empty)* | Base64-encoded AES key for encrypting sensitive data at rest (16/24/32 bytes) |

### Encryption of Sensitive Data

Repository passwords and S3 credentials (access key, secret key) are encrypted at rest in the database using AES-GCM when an encryption key is configured.

**Generate a key:**

```bash
openssl rand -base64 32
```

**Configure via environment variable (recommended):**

```bash
export RESTIC_ENCRYPTION_KEY="your-generated-base64-key"
```

Or set in `application.properties`:

```properties
restic.encryption.key=your-generated-base64-key
```

> ⚠️ **Important**: Without an encryption key, sensitive data is stored in plain text. Always configure encryption in production.
> 
> The system gracefully handles legacy unencrypted data — existing plain-text values will be readable even after encryption is enabled, and will be encrypted upon the next save.

### Docker Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `db` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `resticexplorer` | Database name |
| `DB_USER` | `resticexplorer` | Database user |
| `DB_PASSWORD` | `resticexplorer` | Database password |
| `RESTIC_ENCRYPTION_KEY` | *(empty)* | Base64-encoded AES key for encrypting sensitive data at rest |

## Deployment

### Docker Compose

The included `docker-compose.yml` runs the application with PostgreSQL:

```bash
docker compose up --build -d
```

### Ansible

An Ansible playbook is provided in `deploy/ansible/`:

```bash
cd deploy/ansible
ansible-playbook -i inventory.ini deploy.yml
```

Edit `inventory.ini` to point to your target server.

## Development

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test
```

### Project Structure

```
src/main/java/org/remus/resticexplorer/
├── ResticExplorerApplication.java     # Main application entry point
├── config/                            # Security, web config & encryption
│   ├── crypto/                        # AES-GCM encryption service & converter
│   └── exception/                     # Custom exceptions & global handler
├── admin/                             # Admin feature (auth, setup)
│   ├── web/                           # Controllers, DTOs
│   ├── data/                          # JPA entities, repositories
│   └── AdminService.java             # Service layer
├── repository/                        # Repository management feature
│   ├── web/                           # Controllers, DTOs
│   ├── data/                          # JPA entities, repositories
│   ├── RepositoryService.java        # Repository CRUD service
│   └── GroupService.java             # Repository group management
├── scanning/                          # Scanning, checks & retention policy
│   ├── web/                           # Dashboard controller
│   ├── data/                          # Snapshot, ScanResult, CheckResult entities
│   ├── ScanService.java              # Scheduled scanning service
│   ├── CheckService.java             # Scheduled integrity check service
│   ├── RetentionPolicyChecker.java   # Retention policy evaluation logic
│   └── RetentionPolicyResult.java    # Retention policy result DTO
├── download/                          # Snapshot download feature
│   └── web/                           # Download controller
├── restic/                            # Restic CLI integration
│   ├── ResticRepositoryProvider.java  # Provider interface (extensible)
│   ├── ResticS3Provider.java          # S3 implementation
│   ├── ResticAzureProvider.java       # Azure Blob Storage implementation
│   ├── ResticSftpProvider.java        # SFTP implementation
│   └── ResticCommandService.java      # Command execution service
└── health/                            # Actuator health indicator
    └── ResticMetadataHealthIndicator.java
```

## License

[MIT License](LICENSE)
