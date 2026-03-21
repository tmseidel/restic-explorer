# Testing SFTP Capabilities with OpenSSH Server

This tutorial sets up a local SFTP test environment using `lscr.io/linuxserver/openssh-server:latest` with SSH key authentication, initializes a restic repository over SFTP, and connects it to Restic Explorer.

## Prerequisites

- Docker & Docker Compose installed
- `restic` installed on the host (for initial repo setup)

## 1. Generate an SSH Key Pair

```bash
ssh-keygen -t ed25519 -f ./test-sftp-key -N ""
```

This creates `test-sftp-key` (private) and `test-sftp-key.pub` (public).

## 2. Start the OpenSSH Server

Create `docker-compose-sftp.yml` (or use the one at `sftp-test/docker-compose-sftp.yml`):

```yaml
services:
  sftp:
    image: lscr.io/linuxserver/openssh-server:latest
    container_name: sftp-test
    environment:
      PUID: 1000
      PGID: 1000
      TZ: Europe/Berlin
      USER_NAME: testuser
      USER_PASSWORD: testpassword
      PASSWORD_ACCESS: "true"
      SUDO_ACCESS: "false"
    ports:
      - "2222:2222"
    volumes:
      - sftp-data:/config
    restart: unless-stopped

volumes:
  sftp-data:
```

Start it, then inject the public key:

```bash
docker compose -f sftp-test/docker-compose-sftp.yml up -d

# Wait a few seconds for the container to initialize, then inject the public key
docker exec sftp-test mkdir -p /config/.ssh
docker cp sftp-test-key.pub sftp-test:/config/.ssh/authorized_keys
docker exec sftp-test chown 1000:1000 /config/.ssh/authorized_keys
docker exec sftp-test chmod 600 /config/.ssh/authorized_keys
```

Verify SSH connectivity:

```bash
ssh -o StrictHostKeyChecking=no -i ./test-sftp-key -p 2222 testuser@localhost echo "SSH OK"
```

## 3. Initialize a Restic Repository over SFTP

```bash
ssh -o StrictHostKeyChecking=no -i ./sftp-test-key -p 2222 testuser@localhost mkdir -p /config/restic-repo

export RESTIC_PASSWORD="test1234"
restic -r sftp:testuser@localhost:/config/restic-repo \
       -o sftp.command="ssh -o StrictHostKeyChecking=no -i ./test-sftp-key -p 2222 testuser@localhost -s sftp" \
       init
```

## 4. Create a Test Backup

```bash
export RESTIC_PASSWORD="test1234"
restic -r sftp:testuser@localhost:/config/restic-repo \
       -o sftp.command="ssh -o StrictHostKeyChecking=no -i ./test-sftp-key -p 2222 testuser@localhost -s sftp" \
       backup /tmp
```

Verify:

```bash
restic -r sftp:testuser@localhost:/config/restic-repo \
       -o sftp.command="ssh -o StrictHostKeyChecking=no -i ./test-sftp-key -p 2222 testuser@localhost -s sftp" \
       snapshots
```

## 5. Connect to Restic Explorer

### Running Locally (`mvn spring-boot:run`)

1. Open [http://localhost:8080](http://localhost:8080), complete setup if needed
2. Go to **Repositories → Add Repository**
3. Fill in:
   - **Name**: `SFTP Test`
   - **Repository Type**: `SFTP`
   - **Repository URL**: `sftp:testuser@localhost:/config/restic-repo`
   - **Repository Password**: `test1234`
   - **SFTP Command**: `ssh -o StrictHostKeyChecking=no -i /absolute/path/to/test-sftp-key -p 2222 testuser@localhost -s sftp`
4. Click **Save**, then trigger a scan from the dashboard

### Running in Docker

Mount the private key into the app container and use Docker service names:

```yaml
services:
  app:
    # ... existing app config ...
    volumes:
      - ./test-sftp-key:/app/ssh/test-sftp-key:ro

  sftp:
    image: lscr.io/linuxserver/openssh-server:latest
    # ... same config as sftp-test/docker-compose-sftp.yml ...
```

Configure the repository in Restic Explorer:

- **Repository URL**: `sftp:testuser@sftp:/config/restic-repo`
- **SFTP Command**: `ssh -o StrictHostKeyChecking=no -i /app/ssh/test-sftp-key -p 2222 testuser@sftp -s sftp`

> **Note**: Use the Docker service name `sftp` as hostname instead of `localhost`. The container user runs as UID 1000, matching the default Linux user, so mounted key permissions work automatically.

## 6. Cleanup

```bash
docker compose -f sftp-test/docker-compose-sftp.yml down -v
rm -f sftp-test-key sftp-test-key.pub
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `Host key verification failed` | Add `-o StrictHostKeyChecking=no` to the SFTP Command |
| `Connection refused` | Ensure the SFTP container is running: `docker ps` |
| `Permission denied (publickey)` | Verify the key is mounted, has correct permissions (`chmod 600`), and `authorized_keys` is in place |
| `unsupported repository version` | Ensure host and container `restic` versions match |
