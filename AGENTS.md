# AGENTS.md

## Purpose
Spring Boot 4 + Thymeleaf app for browsing restic backup repositories. Source: `src/main/java/org/remus/resticexplorer`. Only existing AI guidance: `README.md`.

## Architecture
- **Feature-first packages** (`admin`, `repository`, `scanning`, `download`, `restic`, `health`) each with `web/` (controllers/DTOs), `data/` (JPA entities/repos), and service classes at package root.
- **Data flow**: Controller → Service → JPA / `ResticCommandService` → restic CLI. Example: `DashboardController.triggerScan()` → `ScanService.scanRepository()` → `ResticCommandService.listSnapshots()`.
- **Strategy pattern** for backends: `ResticRepositoryProvider` interface, `ResticS3Provider` impl, selected via `RepositoryType` enum. Adding a type requires: enum value + provider `@Component` + form DTO/controller/template fields.
- **Security**: public read (dashboard, snapshots, actuator), admin-gated writes (`SecurityConfig` routes + `sec:authorize` in templates). First-run gate via `SetupInterceptorConfig` redirects to `/setup` until admin exists.
- **Encryption**: `repositoryPassword` auto-encrypted by JPA converter (`EncryptedStringConverter`). Sensitive map properties (`RepositoryPropertyKey.isSensitive()`) encrypted/decrypted manually in `RepositoryService`. `save()` does `saveAndFlush` + `detach` before decrypting — preserve this pattern. Encryption optional (`restic.encryption.key`); `EncryptionService.decrypt()` has plaintext fallback.

## Workflows
```bash
mvn spring-boot:run          # Local dev – H2 file DB
mvn test                     # Tests – profile 'test', H2 in-memory
mvn clean package            # Package jar
docker compose up --build -d # Docker – PostgreSQL + restic in container
```

## Conventions
- Controllers return template paths matching folder structure: `repository/form`, `scanning/dashboard`, `group/list`.
- All UI strings via message keys in `src/main/resources/messages.properties` — no hardcoded text.
- Admin actions protected in **both** `SecurityConfig` and template (`sec:authorize`).
- Errors handled view-based: `GlobalExceptionHandler` → `error` template + HTTP status.

## Integration Boundaries
- Runtime dep: `restic` CLI on PATH (override via `restic.binary`). S3 creds injected as env vars by provider.
- Health: `ResticMetadataHealthIndicator` at `/actuator/health` reports per-repo scan state.
- Deploy: Docker Compose + optional Ansible (`deploy/ansible/deploy.yml`). No K8s assumptions.
