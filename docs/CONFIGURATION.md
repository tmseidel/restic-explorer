# Configuration

## Application Properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Server port |
| `restic.binary` | `restic` | Path to the restic binary |
| `restic.timeout` | `300` | Timeout in seconds for restic commands |
| `restic.scan.check-interval` | `60000` | Interval in ms to check for due scans/checks |
| `restic.encryption.key` | *(empty)* | Base64-encoded AES key for encrypting sensitive data |

## Docker Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `db` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `resticexplorer` | Database name |
| `DB_USER` | `resticexplorer` | Database user |
| `DB_PASSWORD` | `resticexplorer` | Database password |
| `RESTIC_ENCRYPTION_KEY` | *(empty)* | AES encryption key for sensitive data |

## Encryption of Sensitive Data

Repository passwords and backend credentials are encrypted at rest using AES-256-GCM when an encryption key is configured.

```bash
# Generate a key
openssl rand -base64 32

# Set via environment variable (recommended)
export RESTIC_ENCRYPTION_KEY="your-generated-base64-key"
```

> ⚠️ Without an encryption key, sensitive data is stored in plain text. Always configure encryption in production.
>
> The system handles legacy unencrypted data gracefully — existing plain-text values remain readable and will be encrypted on next save.

## Deployment

### Docker Compose (recommended)

```bash
docker compose up --build -d
```

Runs the application with PostgreSQL. The Docker image includes restic, openssh-client, and curl.

### Docker Hub Image

Pre-built at [`tmseidel/restic-explorer`](https://hub.docker.com/r/tmseidel/restic-explorer):

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
      RESTIC_ENCRYPTION_KEY: # optional: openssl rand -base64 32
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

### Mounting SSH Keys (SFTP)

```yaml
volumes:
  - /home/user/.ssh/id_rsa:/app/ssh/id_rsa:ro
```

The Docker image runs as UID 1000. Ensure the key file is readable.

### Ansible

```bash
cd deploy/ansible
ansible-playbook -i inventory.ini deploy.yml
```

## Migration Notes

### Upgrading from < 0.4 to ≥ 1.0

Version 0.4 includes a `SchemaFixRunner` that reconciles Hibernate check constraints for PostgreSQL enum columns. This runner will be removed in 1.0. **You must run a version ≥ 0.4 and < 1.0 before upgrading to ≥ 1.0.**

