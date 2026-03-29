# Restic Explorer

![Restic Explorer Logo](src/main/resources/static/images/logo.svg)

A web-based dashboard for monitoring and exploring [restic](https://restic.net/) backup repositories. Built with Spring Boot 4, Thymeleaf, and Bootstrap 5.

## Features

- **Multi-Repository Dashboard** – Monitor S3, Azure Blob Storage, and SFTP restic repositories in one place with grouping, status badges, and lock warnings
- **Automated Scanning & Integrity Checks** – Configurable scheduled scans and `restic check --read-data` per repository
- **Snapshot Browser & Detail View** – Paginated, sortable snapshot list with dedicated detail pages (paths, tags, size, file count)
- **Retention Policies** – Advisory backup-frequency rules (daily/weekly/monthly/yearly/last); violations shown as amber warnings
- **Error Log** – Persistent log of scan/check failures with date filtering, pagination, and auto-cleanup
- **Health Endpoint** – Spring Actuator at `/actuator/health` reporting per-repo scan, check, and retention status
- **Encrypted Credentials** – AES-256-GCM encryption at rest for repository passwords and backend keys
- **Admin-Only Actions** – Snapshot download, repository CRUD, unlock, setup wizard with single admin account
- **Dark Mode & Responsive UI** – Bootstrap 5.3 with automatic light/dark theme switching

## Screenshots

### Dashboard
The dashboard provides a high-level overview of all configured repositories grouped by category, including snapshot counts, scan/check status badges, retention policy status, and lock warnings.
![Dashboard](docs/screenshot_dashboard.png)

### Snapshot Browser
The snapshot browser lists cached snapshots with paginated, sortable columns. Click any snapshot to open its detail page.
![Snapshots](docs/screenshot_snapshots.png)

### Snapshot Details
The detail page shows full snapshot metadata including all paths, tags, size, file count, and a download option for admins.
![Snapshot Detail](docs/screenshot_snapshot.png)

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- [restic](https://restic.readthedocs.io/en/stable/020_installation.html) installed on the system

### Run Locally

```bash
git clone https://github.com/tmseidel/restic-explorer.git
cd restic-explorer
mvn spring-boot:run
```

The application starts on [http://localhost:8080](http://localhost:8080). On first launch, you will be redirected to the setup page to create the admin account.

### Run with Docker

```bash
docker compose up --build -d
```

### Run with Docker Hub Image

A pre-built image is available at [`tmseidel/restic-explorer`](https://hub.docker.com/r/tmseidel/restic-explorer).

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
   - **Enabled**: Toggle scanning and integrity checks on/off
   - **Group**: Optionally assign to a group for dashboard organization
   - **Repository Type**: Select the backend type (see [Supported Repository Connectors](#supported-repository-connectors))
   - **Repository URL**: The restic repository URL (format depends on backend type)
   - **Repository Password**: The encryption password for the restic repository
   - **Backend-specific settings**: Depending on the selected type (S3 credentials, Azure account details, or SFTP options)
   - **Retention Policy** *(optional)*: Define expected backup frequency (see [Retention Policies](#11-retention-policies))
   - **Comment**: Optional notes or description
   - **Scan Interval**: How often (in minutes) to scan for new snapshots
   - **Check Interval**: How often (in minutes) to run `restic check` (set to `0` to disable)
4. Click **Save**

> **Note**: When editing a repository, sensitive fields (passwords, keys) are preserved if left unchanged — you only need to re-enter them if you want to change the value.

### Supported Repository Connectors

| Connector | Repository Type | URL Format | Additional Settings |
|---|---|---|---|
| **Amazon S3 / S3-Compatible** | `S3` | `s3:https://s3.amazonaws.com/bucket/path` | Access Key, Secret Key, Region |
| **Microsoft Azure Blob Storage** | `AZURE` | `azure:container-name:/path` | Account Name, Account Key, Endpoint Suffix |
| **SFTP** | `SFTP` | `sftp:user@host:/path/to/repo` | Password Command (optional), SFTP Command (optional) |
| **REST Server** | `REST` | `rest:http://host:8000/` | Username (optional), Password (optional) |
| **Rclone** | `RCLONE` | `rclone:remote:path` | Rclone Program (optional), Rclone Args (optional) |

#### SFTP Connector Details

The SFTP connector supports restic repositories on remote servers accessible via SSH/SFTP. Authentication is **key-based only** (no SSH password storage).

- **Repository URL**: Standard restic SFTP URL, e.g. `sftp:user@host:/srv/restic-repo`
- **Password Command** *(optional)*: Shell command printing the *repository* password to stdout (e.g. `cat /path/to/password-file`)
- **SFTP Command** *(optional)*: Custom SSH command for the SFTP connection, e.g. `ssh user@host -i /root/.ssh/id_rsa -s sftp`

#### Mounting SSH Private Keys in Docker

Mount the private key file into the container:

```yaml
services:
  app:
    image: tmseidel/restic-explorer:latest
    volumes:
      - app-data:/app/data
      - /home/youruser/.ssh/id_rsa:/app/ssh/id_rsa:ro
```

Then set the **SFTP Command** to: `ssh user@host -i /app/ssh/id_rsa -s sftp`

> ⚠️ Mount the key as read-only (`:ro`). The Docker image runs as UID 1000, so ensure the key file is readable by that user (`chmod 600`).

See [docs/SYSTEMTEST_SFTP.md](docs/SYSTEMTEST_SFTP.md) for a full SFTP testing tutorial.

#### REST Server Connector Details

The REST connector supports restic repositories hosted on a [restic REST server](https://github.com/restic/rest-server). It supports optional HTTP basic authentication.

- **Repository URL**: Standard restic REST URL, e.g. `rest:http://host:8000/` or `rest:https://host:8000/path`
- **Username** *(optional)*: Username for HTTP basic authentication
- **Password** *(optional)*: Password for HTTP basic authentication

When credentials are provided, they are injected into the URL for the restic CLI (e.g. `rest:http://user:pass@host:8000/`).

See [docs/SYSTEMTEST_REST.md](docs/SYSTEMTEST_REST.md) for a full REST server testing tutorial.

#### Rclone Connector Details

The Rclone connector supports restic repositories accessible via [rclone](https://rclone.org/), which provides access to many cloud storage services (Google Drive, Dropbox, OneDrive, Backblaze B2, etc.). Rclone must be installed and configured on the host (or in the Docker container).

- **Repository URL**: Standard restic rclone URL, e.g. `rclone:remote:path` (e.g. `rclone:b2prod:yggdrasil/repo`)
- **Rclone Program** *(optional)*: Path to the rclone binary. Defaults to `rclone` (found via PATH).
- **Rclone Args** *(optional)*: Custom arguments passed to rclone. Defaults to `serve restic --stdio --b2-hard-delete`.

Restic starts rclone as a subprocess — credentials are managed by rclone's own configuration (`~/.config/rclone/rclone.conf`), not by Restic Explorer.

See [docs/SYSTEMTEST_RCLONE.md](docs/SYSTEMTEST_RCLONE.md) for a full rclone testing tutorial.

### 3. Dashboard

The dashboard shows:
- Summary cards: total repositories, total snapshots, overall status
- Per-repository: scan status, integrity check status, retention policy status, lock warnings
- Repository groups with collapsible sections (collapse state saved in browser cookie)
- Quick actions: view snapshots, trigger scan, run integrity check, unlock

### 4. Browsing Snapshots

Click on a repository name or the eye icon to see cached snapshots:
- **Paginated list** (25 per page) with sortable columns (time, hostname, snapshot ID, size)
- Click any snapshot row to open the **Snapshot Detail Page**

### 5. Snapshot Detail Page

The detail page for a single snapshot shows:
- Snapshot ID, timestamp, hostname, username, tree hash
- Total size and file count
- Full paths list (one per line, not comma-separated)
- Tags as badges
- Admin-only download button

### 6. Downloading Snapshots

> **Admin only**: Click the download button on the snapshot detail page to get a `.tar` archive via `restic dump`.

### 7. Lock Detection & Unlock

During each scan, Restic Explorer checks for stale locks on the repository:
- A lock warning badge appears on the dashboard and snapshot page if locks are detected
- Admins can click **Unlock** to run `restic unlock` and release stale locks

### 8. Administration

Navigate to **Admin** to:
- Change the admin password
- View application version
- Access the **Error Log** (browse scan/check failures with date range filtering and pagination)
- Clear all error log entries

### 9. Health & Monitoring

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health including per-repo scan, check, and retention status |
| `GET /actuator/info` | Application name and version (from Maven build) |
| `GET /actuator/metrics` | Application metrics |

The custom `resticMetadata` health indicator reports:
- Total repositories and cached snapshots
- Per-repository scan status, check status, retention policy status
- Overall status: `UP` (all OK), `DOWN` (any failure), `UNKNOWN` (no repos)

### 10. Integrity Checks

Restic Explorer can periodically run `restic check --read-data` to verify repository integrity.

- Set **Check Interval** to a non-zero value (minutes) to enable; `0` to disable
- Admins can trigger checks manually from the dashboard or snapshot page
- Status badges: OK, Failed, Pending, Disabled

> ⚠️ `restic check --read-data` reads **all data** in the repository. Choose the interval accordingly for large repositories.

### 11. Retention Policies

Each repository can have an optional **retention policy** defining expected backup frequency. Policies are purely advisory — they **never delete** snapshots.

| Field | Meaning |
|---|---|
| **Keep Daily** | Number of recent days that must each have ≥1 snapshot |
| **Keep Weekly** | Number of recent weeks that must each have ≥1 snapshot |
| **Keep Monthly** | Number of recent months that must each have ≥1 snapshot |
| **Keep Yearly** | Number of recent years that must each have ≥1 snapshot |
| **Keep Last** | Minimum total number of snapshots |

Leave all fields empty or `0` to disable. After each scan, violations appear as amber warnings on the dashboard and snapshot page. Violations do **not** affect the overall health status.

### 12. Error Log

Scan and integrity check failures are automatically logged to a persistent error log:
- View in **Admin** → **Error Log** with date range filtering
- Paginated table with timestamp, repository name, action, error message, and stack trace
- Entries older than 12 months are automatically cleaned up (daily at 03:00)
- Admins can clear all entries manually

## Configuration

### Application Properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Server port |
| `restic.binary` | `restic` | Path to the restic binary |
| `restic.timeout` | `300` | Timeout in seconds for restic commands |
| `restic.scan.check-interval` | `60000` | Interval in ms to check for due scans/checks |
| `restic.encryption.key` | *(empty)* | Base64-encoded AES key for encrypting sensitive data (16/24/32 bytes) |

### Encryption of Sensitive Data

Repository passwords and backend credentials (S3 access/secret key, Azure account key) are encrypted at rest using AES-256-GCM when an encryption key is configured.

```bash
# Generate a key
openssl rand -base64 32

# Configure via environment variable (recommended)
export RESTIC_ENCRYPTION_KEY="your-generated-base64-key"
```

> ⚠️ Without an encryption key, sensitive data is stored in plain text. Always configure encryption in production.
>
> The system handles legacy unencrypted data gracefully — existing plain-text values remain readable and will be encrypted on next save.

### Docker Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `db` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `resticexplorer` | Database name |
| `DB_USER` | `resticexplorer` | Database user |
| `DB_PASSWORD` | `resticexplorer` | Database password |
| `RESTIC_ENCRYPTION_KEY` | *(empty)* | AES encryption key for sensitive data |

## Deployment

### Docker Compose

```bash
docker compose up --build -d
```

The included `docker-compose.yml` runs the application with PostgreSQL. The Docker image uses a multi-stage build with Eclipse Temurin 21 and includes restic, openssh-client, and curl.

### Ansible

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

## Migration Notes

### Upgrading from < 0.4 to ≥ 1.0

Version 0.4 includes a `SchemaFixRunner` that reconciles Hibernate check constraints for PostgreSQL enum columns (`RepositoryType`, `RepositoryPropertyKey`). This runner will be removed in 1.0. **You must run a version ≥ 0.4 and < 1.0 before upgrading to ≥ 1.0.**

## License

[MIT License](LICENSE)
