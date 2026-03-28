# Testing REST Server Capabilities with restic-rest-server

This tutorial sets up a local REST server test environment using `restic/rest-server`, initializes a restic repository, and connects it to Restic Explorer.

## Prerequisites

- Docker & Docker Compose installed
- `restic` installed on the host (for initial repo setup)

## 1. Start the REST Server

Create `docker-compose-rest.yml` (or use the one at `rest-test/docker-compose-rest.yml`):

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

volumes:
  rest-data:
```

Start it:

```bash
docker compose -f rest-test/docker-compose-rest.yml up -d
```

Verify the server is running:

```bash
curl http://localhost:8500/
```

## 2. Initialize a Restic Repository via REST

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://localhost:8500/ init
```

## 3. Create a Test Backup

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://localhost:8500/ backup /tmp
```

Verify:

```bash
restic -r rest:http://localhost:8500/ snapshots
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
   - Leave **Username** and **Password** empty (no-auth mode)
4. Click **Save**, then trigger a scan from the dashboard

### Running in Docker

Use Docker service names instead of `localhost`:

```yaml
services:
  app:
    # ... existing app config ...

  rest-server:
    image: restic/rest-server:latest
    # ... same config as rest-test/docker-compose-rest.yml ...
```

Configure the repository in Restic Explorer:

- **Repository URL**: `rest:http://rest-server:8000/`

> **Note**: Use the Docker service name `rest-server` as hostname and the internal port `8000` instead of `localhost:8500`.

## 5. Testing with Authentication

To test with HTTP basic authentication, update the docker-compose to enable auth:

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

Create the `.htpasswd` file:

```bash
# Install htpasswd (Apache utils)
# On Debian/Ubuntu: apt-get install apache2-utils
# On macOS: brew install httpd
htpasswd -Bc rest-test/htpasswd admin
```

Then configure the repository in Restic Explorer with:
- **Username**: `admin`
- **Password**: *(the password you set with htpasswd)*

Initialize the repository with credentials:

```bash
export RESTIC_PASSWORD="test1234"
restic -r rest:http://admin:yourpassword@localhost:8500/ init
restic -r rest:http://admin:yourpassword@localhost:8500/ backup /tmp
```

## 6. Cleanup

```bash
docker compose -f rest-test/docker-compose-rest.yml down -v
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` | Ensure the REST server container is running: `docker ps` |
| `401 Unauthorized` | Verify username/password match the `.htpasswd` file |
| `unsupported repository version` | Ensure host and container `restic` versions match |
| `server returned 500` | Check REST server logs: `docker logs rest-server-test` |
