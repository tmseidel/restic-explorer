# Testing REST Server Capabilities with restic-rest-server

This tutorial sets up a local REST server test environment using `restic/rest-server`, initializes a restic repository, and connects it to Restic Explorer.

## Prerequisites

- Docker & Docker Compose installed
- `restic` installed on the host (for initial repo setup)

## Test Credentials

The test setup uses pre-configured HTTP basic authentication:

| Field | Value |
|---|---|
| **Username** | `admin` |
| **Password** | `admin` |

These are stored in `systemtest/rest-test/htpasswd` (mounted into the container).

> **Important**: The restic REST server requires **bcrypt**-hashed passwords (`$2b$` / `$2y$` prefix). Apache APR1 hashes (`$apr1$`) will **not** work and result in `401 Unauthorized`.

## 1. Start the REST Server

```bash
docker compose -f systemtest/rest-test/docker-compose-rest.yml up -d
```

Verify the server is running (requires credentials):

```bash
curl -u admin:admin http://localhost:8500/
```

## 2. Initialize a Restic Repository via REST

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://admin:admin@localhost:8500/ init
```

## 3. Create a Test Backup

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://admin:admin@localhost:8500/ backup /tmp
```

Verify:

```bash
restic -r rest:http://admin:admin@localhost:8500/ snapshots
```

## 4. Connect to Restic Explorer

### Running Locally (`mvn spring-boot:run`)

1. Open [http://localhost:8080](http://localhost:8080), complete setup if needed
2. Go to **Repositories → Add Repository**
3. Fill in:
   - **Name**: `REST Test`
   - **Repository Type**: `REST Server`
   - **Repository URL**: `rest:http://localhost:8500/`
   - **Repository Password**: `test1234`
   - **Username**: `admin`
   - **Password**: `admin`
4. Click **Save**, then trigger a scan from the dashboard

### Running in Docker

Use Docker service names instead of `localhost`:

```yaml
services:
  app:
    # ... existing app config ...

  rest-server:
    image: restic/rest-server:latest
    container_name: rest-server-test
    ports:
      - "8500:8000"
    volumes:
      - rest-data:/data
      - ./htpasswd:/data/.htpasswd:ro
    restart: unless-stopped
```

Configure the repository in Restic Explorer:

- **Repository URL**: `rest:http://rest-server:8000/`
- **Username**: `admin`
- **Password**: `admin`

> **Note**: Use the Docker service name `rest-server` as hostname and the internal port `8000` instead of `localhost:8500`.

## 5. Docker Compose

```yaml
services:
  rest-server:
    image: restic/rest-server:latest
    container_name: rest-server-test
    ports:
      - "8500:8000"
    volumes:
      - rest-data:/data
      - ./htpasswd:/data/.htpasswd:ro
    restart: unless-stopped

volumes:
  rest-data:
```

The `htpasswd` file is included in the repository at `systemtest/rest-test/htpasswd` with the default credentials above.

## 6. Testing without Authentication

To disable authentication (open access), override the docker-compose with `--no-auth`:

```yaml
services:
  rest-server:
    image: restic/rest-server:latest
    container_name: rest-server-test
    environment:
      OPTIONS: "--no-auth"
    ports:
      - "8500:8000"
    volumes:
      - rest-data:/data
    restart: unless-stopped
```

Then omit username/password in both restic commands and Restic Explorer configuration:

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://localhost:8500/ init
```

## 7. Cleanup

```bash
docker compose -f systemtest/rest-test/docker-compose-rest.yml down -v
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` | Ensure the REST server container is running: `docker ps` |
| `401 Unauthorized` | Verify username/password: `admin` / `admin` |
| `unsupported repository version` | Ensure host and container `restic` versions match |
| `server returned 500` | Check REST server logs: `docker logs rest-server-test` |
