# Contributing to Restic Explorer

Thank you for your interest in contributing to Restic Explorer! Every contribution helps — whether it's a bug report, a feature idea, a documentation fix, or a code change.

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to **tom.seidel@remus-software.org**.

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please check existing [issues](https://github.com/tmseidel/restic-explorer/issues) to avoid duplicates.

When filing a bug, include:

- **Summary** — a clear, concise description of the problem
- **Steps to reproduce** — minimal steps to trigger the issue
- **Expected vs. actual behavior**
- **Environment** — OS, Java version (`java -version`), restic version (`restic version`), deployment method (local / Docker)
- **Logs** — relevant log output from the application console or `docker logs`

### Suggesting Features

Open an issue with the **enhancement** label. Describe the use-case you are trying to solve before proposing a solution — this helps find the best approach.

### Pull Requests

1. **Fork** the repository and create a branch from `main`.
2. **Implement** your change — see [Development Setup](#development-setup) below.
3. **Add or update tests** for your change. All existing tests must pass.
4. **Follow the existing code style** (see [Conventions](#conventions)).
5. **Open a Pull Request** against `main` with a clear description of what changed and why.

Small, focused PRs are easier to review and merge.

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.8+
- [restic](https://restic.readthedocs.io/en/stable/020_installation.html) on PATH

### Build & Run

```bash
# Local dev — H2 file database
mvn spring-boot:run

# Run tests — H2 in-memory (profile 'test')
mvn test

# Package jar
mvn clean package

# Docker — PostgreSQL + restic in container
docker compose up --build -d
```

### Project Structure

The project uses **feature-first packages** under `src/main/java/org/remus/resticexplorer`:

| Package       | Purpose                                      |
|---------------|----------------------------------------------|
| `admin`       | Admin account, error log, setup flow         |
| `repository`  | Repository & group CRUD, encryption          |
| `scanning`    | Scheduled scans, integrity checks, retention |
| `download`    | Snapshot download (tar archive)              |
| `restic`      | restic CLI integration, backend providers    |
| `health`      | Actuator health indicator                    |
| `config`      | Security, crypto, exception handling         |

Each feature package may contain `web/` (controllers, DTOs), `data/` (JPA entities, repositories), and service classes.

## Conventions

- **Controllers** return Thymeleaf template paths matching the folder structure (e.g. `repository/form`, `scanning/dashboard`).
- **User-visible text** comes from message keys in `src/main/resources/messages.properties` — never hard-code English strings in templates or flash messages.
- **Sensitive fields** (passwords, secrets) are encrypted at rest. New backend properties that are sensitive must be marked with `isSensitive() = true` in `RepositoryPropertyKey`.
- **Admin-only actions** are protected in both `SecurityConfig` (route rules) and templates (`sec:authorize`).
- **Tests** use the `test` profile with an in-memory H2 database. Name test classes `*Test.java`.

### Adding a New Backend Type

1. Add a value to the `RepositoryType` enum.
2. Create a `@Component` implementing `ResticRepositoryProvider`.
3. Add the corresponding form fields, controller mapping, template fieldset, and `messages.properties` keys.

## Style Guide

- Follow standard Java / Spring Boot conventions.
- Use Lombok where the existing codebase already uses it (`@Data`, `@RequiredArgsConstructor`, etc.).
- Keep commits atomic — one logical change per commit.
- Write meaningful commit messages: `fix: ...`, `feat: ...`, `docs: ...`, `test: ...`.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

