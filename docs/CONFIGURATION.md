# Configuration

## Application Properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Server port |
| `restic.binary` | `restic` | Path to the restic binary |
| `restic.timeout` | `300` | Timeout in seconds for restic commands |
| `restic.scan.check-interval` | `60000` | Interval in ms to check for due scans/checks |
| `restic.encryption.key` | *(empty)* | Base64-encoded AES key for encrypting sensitive data |
| `restic.auth.mode` | `local` | Authentication mode: `local` (built-in admin) or `oauth2` (external provider) |
| `restic.auth.allowed-providers` | *(empty)* | Comma-separated OAuth2 provider ids allowed to log in. Empty = any configured provider. |

## Authentication

Restic Explorer supports two authentication modes, selected via `restic.auth.mode`.

### Local authentication (default)

On first launch the application redirects to `/setup` where you create a single local admin account. Username is always `admin`. The password can be changed from the Admin panel.

### OAuth2 authentication

When `restic.auth.mode=oauth2`, local login and setup are disabled. Users authenticate through a configured OAuth2/OIDC provider such as Keycloak or Microsoft Entra ID. Every successfully authenticated user receives the `ADMIN` role, so restrict access on the provider side.

Minimal configuration example for Keycloak:

```properties
restic.auth.mode=oauth2

spring.security.oauth2.client.registration.keycloak.client-id=restic-explorer
spring.security.oauth2.client.registration.keycloak.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
spring.security.oauth2.client.provider.keycloak.issuer-uri=https://keycloak.example.com/realms/master
```

Minimal configuration example for Microsoft Entra ID:

```properties
restic.auth.mode=oauth2

spring.security.oauth2.client.registration.entra.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.entra.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.entra.scope=openid,profile,email
spring.security.oauth2.client.provider.entra.issuer-uri=https://login.microsoftonline.com/YOUR_TENANT_ID/v2.0
```

Restricting providers:

```properties
restic.auth.allowed-providers=keycloak
```

> ⚠️ If `restic.auth.mode=oauth2` is set but no OAuth2 client registrations are configured, the application fails to start with a clear error message.

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

Version 0.4 includes a `SchemaFixRunner` that reconciles Hibernate check constraints for PostgreSQL enum columns. This runner is deprecated and will be removed in a future 1.x release. **You must run a version ≥ 0.4 and < 1.0 before upgrading to ≥ 1.0.**

