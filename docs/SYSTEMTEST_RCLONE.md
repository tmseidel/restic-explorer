# Testing Rclone Capabilities with a Local SFTP Backend

This tutorial sets up a local rclone test environment using an SFTP server as the rclone remote, initializes a restic repository via rclone, and connects it to Restic Explorer.

## Prerequisites

- Docker & Docker Compose installed
- `restic` installed on the host (for initial repo setup)
- `rclone` installed on the host (for configuration and testing)

## Overview

Rclone supports [many cloud storage backends](https://rclone.org/overview/). For local testing, we use an SFTP server as the rclone remote. In production, you would configure rclone with your preferred cloud storage (Google Drive, Dropbox, Backblaze B2, OneDrive, etc.).

## 1. Start the SFTP Server

```bash
docker compose -f systemtest/rclone-test/docker-compose-rclone.yml up -d
```

Verify the server is running:

```bash
docker ps | grep sftp-rclone-test
```

## 2. Configure Rclone

A pre-configured `rclone.conf` is included at `systemtest/rclone-test/rclone.conf` and mounted into the Docker Compose setup automatically. For local (non-Docker) testing, copy it to your rclone config directory:

```bash
mkdir -p ~/.config/rclone
cp systemtest/rclone-test/rclone.conf ~/.config/rclone/rclone.conf
```

The config defines a remote called `sftp-test` pointing to the SFTP server:

```ini
[sftp-test]
type = sftp
host = localhost
port = 2223
user = testuser
pass = 1U-OY86jKVYLIDNcpdX1ihpkMvDzvd-V85oZaw
known_hosts_file = /dev/null
shell_type = unix
```

> **Note**: The `pass` value above is `testpassword` obscured with `rclone obscure testpassword`. Rclone requires obscured passwords in its config file. The `known_hosts_file = /dev/null` disables host key verification for the test environment.

Verify the remote works:

```bash
rclone lsd sftp-test:
```

## 3. Initialize a Restic Repository via Rclone

```bash
# Create the repo directory on the remote
rclone mkdir sftp-test:config/restic-repo

# Initialize the restic repository
export RESTIC_PASSWORD="test1234"
restic -r rclone:sftp-test:config/restic-repo init
```

## 4. Create a Test Backup

```bash
export RESTIC_PASSWORD="test1234"
restic -r rclone:sftp-test:config/restic-repo backup /tmp
```

Verify:

```bash
restic -r rclone:sftp-test:config/restic-repo snapshots
```

## 5. Connect to Restic Explorer

### Running Locally (`mvn spring-boot:run`)

1. Open [http://localhost:8080](http://localhost:8080), complete setup if needed
2. Go to **Repositories → Add Repository**
3. Fill in:
   - **Name**: `Rclone Test`
   - **Repository Type**: `Rclone`
   - **Repository URL**: `rclone:sftp-test:config/restic-repo`
   - **Repository Password**: `test1234`
4. Click **Save**, then trigger a scan from the dashboard

> **Note**: Rclone must be installed on the same machine where Restic Explorer is running, and the rclone remote (`sftp-test`) must be configured in that machine's rclone config. The pre-configured `rclone.conf` is included at `systemtest/rclone-test/rclone.conf`.

### Running in Docker

Rclone is already included in the Docker image. Mount your rclone configuration into the container:

```yaml
services:
  app:
    # ... existing app config ...
    volumes:
      - app-data:/app/data
      - ./rclone.conf:/home/appuser/.config/rclone/rclone.conf:ro
```

The `docker-compose-rclone.yml` in `systemtest/rclone-test/` already mounts the included `rclone.conf`.

Configure the repository in Restic Explorer:

- **Repository URL**: `rclone:sftp-test:config/restic-repo`

> **Note**: When running in Docker, the rclone remote must be accessible from within the container. Use Docker service names instead of `localhost` in the rclone configuration, or use cloud remotes that are accessible from anywhere.

## 6. Advanced: Custom Rclone Program and Arguments

The Rclone connector supports two optional configuration fields:

- **Rclone Program**: Path to the rclone binary (default: `rclone` on PATH). Useful when rclone is installed in a non-standard location, or to run rclone via SSH on a remote host (e.g. `ssh user@remotehost rclone`).
- **Rclone Args**: Custom arguments passed to rclone (default: `serve restic --stdio --b2-hard-delete`). Useful for setting bandwidth limits, verbose logging, etc.

Example with bandwidth limit:

```
serve restic --stdio --bwlimit 1M --b2-hard-delete --verbose
```

## 7. Docker Compose

```yaml
services:
  sftp:
    image: lscr.io/linuxserver/openssh-server:latest
    container_name: sftp-rclone-test
    environment:
      PUID: 1000
      PGID: 1000
      TZ: Europe/Berlin
      USER_NAME: testuser
      USER_PASSWORD: testpassword
      PASSWORD_ACCESS: "true"
      SUDO_ACCESS: "false"
    ports:
      - "2223:2222"
    volumes:
      - sftp-rclone-data:/config
      - ./rclone.conf:/home/appuser/.config/rclone/rclone.conf:ro
    restart: unless-stopped

volumes:
  sftp-rclone-data:
```

The `rclone.conf` file is included in the repository at `systemtest/rclone-test/rclone.conf` with the default credentials above.

## 8. Cleanup

```bash
docker compose -f systemtest/rclone-test/docker-compose-rclone.yml down -v
rclone config delete sftp-test
```

## Troubleshooting

| Problem | Solution |
|---|---|
| `cannot implicitly run relative executable rclone` | Use the **Rclone Program** field to specify the full path to rclone |
| `rclone not found` | Ensure rclone is installed and on the PATH |
| `Connection refused` | Ensure the SFTP container is running: `docker ps` |
| `config not found` | Verify rclone remote is configured: `rclone listremotes` |
| `unsupported repository version` | Ensure host and container `restic` versions match |
