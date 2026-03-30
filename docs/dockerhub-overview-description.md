# Restic Explorer

A web-based dashboard for monitoring and browsing [restic](https://restic.net/) backup repositories. Manage multiple repositories (S3, Azure Blob Storage, SFTP), view snapshots, run integrity checks, and integrate with existing monitoring — all from a clean, responsive UI.

## Features

- **Multi-Backend Repository Management** – S3 / S3-compatible, Azure Blob Storage, and SFTP backends; extensible architecture for additional connectors
- **Repository Groups** – Organize repositories into groups for a structured dashboard
- **Automated Scanning** – Configurable per-repository scan intervals cache restic metadata for fast browsing
- **Integrity Checks** – Scheduled `restic check` with configurable intervals per repository
- **Retention Policies** – Optional per-repository policies (daily/weekly/monthly/yearly/last) with soft warning badges on the dashboard
- **Dashboard** – Overview of all repositories with snapshot counts, scan status, check status, and retention policy compliance
- **Snapshot Browser** – Paginated, sortable snapshot list with a dedicated detail page per snapshot
- **Snapshot Download** – Admin-only download of snapshots as `.tar` archives
- **Single Admin Account** – Password setup on first launch; public read access for dashboards and health endpoints
- **Encrypted at Rest** – Repository passwords and backend credentials encrypted via AES-256-GCM
- **Health & Monitoring** – Spring Actuator endpoints (`/actuator/health`, `/actuator/info`, `/actuator/metrics`) reporting per-repo scan, check, and retention status — ready for Prometheus, Uptime Kuma, etc.
- **Responsive UI** – Bootstrap 5, dark-mode aware logo, mobile-friendly

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

### Volumes

| Path | Purpose |
|---|---|
| `/app/data` | Application data directory |
| `/app/ssh` | Mount point for SSH private keys (SFTP backend) |

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
- **Includes**: `restic` CLI, `openssh-client`, `curl` (installed via apk)
- **Runs as**: Non-root user `appuser` (UID/GID 1000)
- **Spring profile**: `docker` (activated automatically)
- **Database**: Requires external PostgreSQL
- **Healthcheck**: Built-in Docker `HEALTHCHECK` against `/actuator/health`

## Source Code & Documentation

Full documentation, architecture details, and source code: [GitHub](https://github.com/tmseidel/restic-explorer)

## License

[MIT License](https://github.com/tmseidel/restic-explorer/blob/main/LICENSE)

