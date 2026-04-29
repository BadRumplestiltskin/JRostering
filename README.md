# JRostering

Universal Staff Rostering Application — constraint-based schedule optimisation with a browser UI.

**Stack:** Spring Boot 3.5.5 · Vaadin 24.7.0 · Timefold Solver 1.31.0 · PostgreSQL 16

---

## Prerequisites

| Tool | Version |
|------|---------|
| JDK  | 25+ (Temurin recommended) |
| Maven | 3.9+ |
| PostgreSQL | 16.x |
| Docker | 24+ (required only for integration tests and Docker Compose deployment) |

---

## Quick start (local development)

### 1. Create the database

```bash
psql -U postgres -c "CREATE USER jrostering WITH PASSWORD 'changeme';"
psql -U postgres -c "CREATE DATABASE jrostering OWNER jrostering;"
```

### 2. Set environment variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/jrostering
export DB_USERNAME=jrostering
export DB_PASSWORD=changeme
```

Or copy the example and edit it:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
# edit application.properties and set DB_PASSWORD
```

### 3. Run the application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts on **http://localhost:8080**. Flyway runs migrations automatically on startup and seeds the default organisation row.

Health check: http://localhost:8080/actuator/health

### Default credentials

On first startup, check the database `app_user` table or the seed migration (`V3__seed_organisation.sql`) for the initial admin account. The UI login form is at the root URL.

---

## Docker Compose deployment

```bash
export DB_PASSWORD=your-secure-password
docker compose up --build
```

The app is available on **http://localhost:8080** with the `prod` profile active (longer session timeout, minimal logging).

To stop and remove containers:

```bash
docker compose down
```

Data persists in the `pgdata` Docker volume. To wipe and reseed:

```bash
docker compose down -v
```

---

## Running tests

### Unit tests (no Docker required)

```bash
mvn test
```

Constraint unit tests use Timefold's `ConstraintVerifier`. Service tests use Mockito. No database needed.

### Integration tests (Docker required)

```bash
mvn test -Dgroups=integration -DexcludedGroups=""
```

Integration tests start a real PostgreSQL container via Testcontainers. Docker must be running. These tests are excluded from the default `mvn test` run to keep CI fast on branches without Docker.

---

## Building a production JAR

```bash
mvn clean package -DskipTests
java -jar target/JRostering-1.0-SNAPSHOT.jar
```

Set the three required environment variables before running (see Quick start §2).

---

## Spring profiles

| Profile | Activated by | Purpose |
|---------|-------------|---------|
| *(none)* | default | Base `application.properties` — production-safe defaults |
| `dev` | `-Dspring-boot.run.profiles=dev` | SQL debug logging, full health details |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Minimal logging, longer session, port 8443 |
| `test` | Testcontainers tests | Flyway enabled, reproducible solver seed |

---

## Database migrations

Flyway runs automatically on every startup. Migration scripts live in `src/main/resources/db/migration/`:

| Script | Purpose |
|--------|---------|
| `V1__baseline_schema.sql` | Full baseline schema |
| `V2__add_constraints_and_violation_detail.sql` | Partial unique indices + violation detail column |
| `V3__seed_organisation.sql` | Seeds the default organisation row |

**Never modify existing versioned scripts.** Add new migrations as `V4__description.sql`, etc.

---

## REST API

All REST endpoints are under `/api/**` and require HTTP Basic authentication.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/solver/{periodId}/submit` | Start a solve job |
| `DELETE` | `/api/solver/{periodId}/cancel` | Cancel a running solve |
| `GET` | `/api/solver/jobs/{jobId}` | Poll job status |
| `GET` | `/api/sites` | List sites |
| `POST` | `/api/sites` | Create a site |
| `GET` | `/api/roster/periods/{periodId}` | Get a roster period |
| `GET` | `/api/staff` | List staff |

Full OpenAPI documentation is available at **http://localhost:8080/swagger-ui.html** when the app is running.

---

## Project structure

```
src/main/java/com/magicsystems/jrostering/
  domain/        JPA entities + enums
  repository/    Spring Data JPA repositories
  security/      Spring Security config, brute-force protection
  service/       Business logic (SolverService, StaffService, SiteService, RosterService, ...)
  solver/        Timefold: RosterSolution, RosterConstraintProvider, RosterSolutionMapper
  report/        Excel report generation (Apache POI)
  api/           REST controllers (/api/**)
  ui/            Vaadin views (browser UI)
```

Key architectural decisions are documented in [docs/adr/](docs/adr/).

---

## Kubernetes deployment

The app exposes Spring Boot Actuator liveness and readiness probes:

| Probe | Path |
|-------|------|
| Liveness | `/actuator/health/liveness` |
| Readiness | `/actuator/health/readiness` |

Example Kubernetes probe configuration:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 15

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
```

Required environment variables for the pod:

```yaml
env:
  - name: DB_URL
    value: jdbc:postgresql://<db-host>:5432/jrostering
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: jrostering-db
        key: username
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: jrostering-db
        key: password
  - name: SPRING_PROFILES_ACTIVE
    value: prod
```

---

## Vaadin 24 EOL notice

Vaadin 24 free maintenance ends **June 2026**. The upgrade path requires Vaadin 25 (targets Spring Boot 4) and Timefold 2.0. See [docs/adr/004-timefold-2-migration-tracking.md](docs/adr/004-timefold-2-migration-tracking.md) for the migration plan.

---

## Contributing

1. Run `mvn test` before committing — unit tests must pass.
2. Add a Flyway migration for any schema change.
3. Never hardcode `DB_PASSWORD` in any committed file.
4. Integration tests (`@Tag("integration")`) require Docker and are opt-in.
