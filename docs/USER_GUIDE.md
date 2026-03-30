# User Guide

## Initial Setup

On first launch you are redirected to the **Setup** page:
1. Enter and confirm an admin password (minimum 8 characters)
2. Click **Create Admin Account**
3. Log in with username `admin` and the chosen password

## Adding a Repository

1. Log in as admin → **Repositories** → **Add Repository**
2. Fill in name, type, URL, repository password, and backend-specific settings
3. Optionally assign a group, configure retention policy, and set scan/check intervals
4. Click **Save**

> When editing, sensitive fields (passwords, keys) are preserved if left unchanged.

## Supported Repository Connectors

| Connector | Type | URL Format | Additional Settings |
|---|---|---|---|
| **Amazon S3 / S3-Compatible** | `S3` | `s3:https://s3.amazonaws.com/bucket/path` | Access Key, Secret Key, Region |
| **Azure Blob Storage** | `AZURE` | `azure:container:/path` | Account Name, Account Key, Endpoint Suffix |
| **SFTP** | `SFTP` | `sftp:user@host:/path` | SFTP Command (optional) |
| **REST Server** | `REST` | `rest:http://host:8000/` | Username, Password (optional) |
| **Rclone** | `RCLONE` | `rclone:remote:path` | Rclone Program, Rclone Args (optional) |

### SFTP

Authentication is **key-based only**. Mount the private key into the Docker container and set the **SFTP Command**, e.g.:

```yaml
volumes:
  - /home/user/.ssh/id_rsa:/app/ssh/id_rsa:ro
```

SFTP Command: `ssh user@host -i /app/ssh/id_rsa -s sftp`

→ Full tutorial: [SYSTEMTEST_SFTP.md](SYSTEMTEST_SFTP.md)

### REST Server

Credentials are passed via `RESTIC_REST_USERNAME` / `RESTIC_REST_PASSWORD` environment variables — the URL is never modified.

→ Full tutorial: [SYSTEMTEST_REST.md](SYSTEMTEST_REST.md)

### Rclone

Restic starts rclone as a subprocess. Credentials are managed by rclone's own config (`~/.config/rclone/rclone.conf`), not by Restic Explorer.

→ Full tutorial: [SYSTEMTEST_RCLONE.md](SYSTEMTEST_RCLONE.md)

## Dashboard

The dashboard shows:
- **Summary cards**: total repositories, total snapshots, overall status
- **Per-repository**: scan status, check status, retention status, lock warnings
- **Groups** with collapsible sections (state saved in cookie)
- **Quick actions**: view snapshots, trigger scan, run check, unlock

## Browsing Snapshots

- Paginated list (25/page) with sortable columns (time, hostname, ID, size)
- Click a snapshot to open the **Detail Page** with full paths, tags, size, file count
- Admin-only download button (`.tar` via `restic dump`)

## Lock Detection & Unlock

Scans automatically detect stale locks. A warning badge appears on the dashboard; admins can click **Unlock** to release them.

## Retention Policies

Optional advisory rules per repository — violations are **amber warnings**, never deletions.

| Field | Meaning |
|---|---|
| Keep Daily | Recent days that must each have ≥1 snapshot |
| Keep Weekly | Recent weeks that must each have ≥1 snapshot |
| Keep Monthly | Recent months that must each have ≥1 snapshot |
| Keep Yearly | Recent years that must each have ≥1 snapshot |
| Keep Last | Minimum total snapshot count |

Leave all at `0` or empty to disable.

## Integrity Checks

Set **Check Interval** > 0 to enable periodic `restic check --read-data`. Set to `0` to disable.

> ⚠️ `restic check --read-data` reads **all** repository data. Choose intervals carefully for large repos.

## Error Log

Scan/check failures are logged persistently. View in **Admin** → **Error Log** with date filtering and pagination. Entries older than 12 months auto-cleanup daily.

## Health & Monitoring

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Per-repo scan, check, and retention status |
| `GET /actuator/info` | Application name and version |
| `GET /actuator/metrics` | Application metrics |

## Administration

**Admin** → Change password, view version, manage error log, manage repository groups.

