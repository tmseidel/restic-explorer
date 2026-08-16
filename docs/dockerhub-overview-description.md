# Restic Explorer

A lightweight monitoring interface for [restic](https://restic.net/) backup repositories. Its primary purpose is to expose reliable backup health signals to observability solutions such as Grafana, Prometheus, and Uptime Kuma, while also providing a clean web UI for browsing repositories and snapshots when you need it.

![Dashboard](https://raw.githubusercontent.com/tmseidel/restic-explorer/main/docs/screenshot_dashboard.png)

## Features

- **Health & Monitoring** – Actuator endpoints (`/actuator/health`, `/actuator/info`) report per-repository scan, integrity check, and retention status — ready for Prometheus, Uptime Kuma, Grafana, and other observability tools
- **Multi-Backend Support** – S3 / S3-compatible, Azure Blob Storage, SFTP, REST Server, and Rclone (Google Drive, Dropbox, B2, OneDrive, …)
- **Repository Groups** – Organize repositories into groups for a structured dashboard
- **Automated Scanning** – Configurable per-repository scan intervals cache restic metadata for fast browsing
- **Integrity Checks** – Scheduled `restic check --read-data` with configurable intervals per repository
- **Retention Policies** – Optional per-repository policies (daily/weekly/monthly/yearly/last) with soft warning badges
- **Snapshot Browser** – Paginated, sortable snapshot list with a dedicated detail page per snapshot
- **Snapshot Download** – Admin-only download of snapshots as `.tar` archives
- **Lock Detection & Unlock** – Automatic stale lock detection with one-click unlock for admins
- **Error Log** – Persistent scan/check failure log with date filtering and auto-cleanup
- **Encrypted at Rest** – Repository passwords and backend credentials encrypted via AES-256-GCM
- **Dark Mode & Responsive UI** – Bootstrap 5.3 with automatic light/dark theme switching

| Snapshots | Snapshot Detail |
|---|---|
| ![Snapshots](https://raw.githubusercontent.com/tmseidel/restic-explorer/main/docs/screenshot_snapshots.png) | ![Detail](https://raw.githubusercontent.com/tmseidel/restic-explorer/main/docs/screenshot_snapshot.png) |

## Quick Start

### Docker Compose (recommended)

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

Then run:

```bash
docker compose up -d
```

The application is available at [http://localhost:8080](http://localhost:8080). On first launch you will be redirected to create the admin account.

### Standalone (bring your own PostgreSQL)

```bash
docker run -d \
  --name restic-explorer \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=your-postgres-host \
  -e DB_PORT=5432 \
  -e DB_NAME=resticexplorer \
  -e DB_USER=resticexplorer \
  -e DB_PASSWORD=your-secure-password \
  -e RESTIC_ENCRYPTION_KEY="your-base64-key" \
  tmseidel/restic-explorer:latest
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `db` | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `resticexplorer` | Database name |
| `DB_USER` | `resticexplorer` | Database user |
| `DB_PASSWORD` | `resticexplorer` | Database password |
| `RESTIC_ENCRYPTION_KEY` | *(empty)* | Base64-encoded AES key for encrypting sensitive data at rest (see below) |

### Encryption of Sensitive Data

Repository passwords and backend credentials (S3 keys, Azure account key) are encrypted at rest when an encryption key is provided. **Strongly recommended for production.**

Generate a key:

```bash
openssl rand -base64 32
```

Pass it to the container:

```bash
-e RESTIC_ENCRYPTION_KEY="your-generated-base64-key"
```

> ⚠️ Without an encryption key, sensitive data is stored in plain text. Existing plain-text values remain readable after encryption is enabled and will be encrypted on the next save.

### SFTP / SSH Key Mounting

For SFTP repositories, mount the SSH private key into the container and reference it in the repository's **SFTP Command** setting:

```yaml
volumes:
  - /home/youruser/.ssh/id_rsa:/app/ssh/id_rsa:ro
```

Then set the SFTP Command to e.g.:

```
ssh user@host -i /app/ssh/id_rsa -s sftp
```

> The container runs as UID/GID 1000 so bind-mounted keys owned by the default host user are readable without extra steps. Mount as `:ro` for security.

### Rclone Configuration

For Rclone repositories, mount your `rclone.conf` into the container:

```yaml
volumes:
  - /home/youruser/.config/rclone/rclone.conf:/home/appuser/.config/rclone/rclone.conf:ro
```

Rclone is pre-installed in the image. Credentials are managed by rclone's own configuration, not by Restic Explorer.

### Volumes

| Path | Purpose |
|---|---|
| `/app/data` | Application data directory |
| `/app/ssh` | Mount point for SSH private keys (SFTP backend) |
| `/home/appuser/.config/rclone/rclone.conf` | Rclone configuration (Rclone backend) |

### Ports

| Port | Description |
|---|---|
| `8080` | HTTP (application + actuator endpoints) |

## First Launch

1. Open [http://localhost:8080](http://localhost:8080)
2. You will be redirected to the **Setup** page
3. Create an admin password (min. 8 characters) — the username is `admin`
4. Log in and start adding restic repositories

## Health & Monitoring

The image exposes Spring Actuator endpoints:

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health including per-repo scan, integrity check, and retention policy status |
| `GET /actuator/info` | Application name and build version |
| `GET /actuator/metrics` | Application metrics |

The custom `resticMetadata` health indicator reports per-repository scan status, check status, retention policy compliance, and an overall UP/DOWN/UNKNOWN state — suitable for integration with Uptime Kuma, Prometheus, or similar monitoring tools.

## Image Details

- **Base image**: `eclipse-temurin:21-jre-alpine` (multi-stage build)
- **Includes**: `restic` CLI, `rclone`, `openssh-client`, `curl` (installed via apk)
- **Runs as**: Non-root user `appuser` (UID/GID 1000)
- **Spring profile**: `docker` (activated automatically)
- **Database**: Requires external PostgreSQL
- **Healthcheck**: Built-in Docker `HEALTHCHECK` against `/actuator/health`

## Source Code & Documentation

Full documentation, architecture details, and source code: [GitHub](https://github.com/tmseidel/restic-explorer)

## License

[MIT License](https://github.com/tmseidel/restic-explorer/blob/main/LICENSE)

