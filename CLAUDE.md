# JRostering — Developer Guide

## Project overview

Universal Staff Rostering Application. A single Spring Boot 3.5.5 JVM hosting:
- **Vaadin 24.7.0** browser UI (no JavaScript required)
- **REST API** (`/api/**`) with HTTP Basic auth
- **Timefold Solver 1.31.0** for constraint-based schedule optimisation

Single organisation, multi-site. One roster manager per installation.

## Prerequisites

- Java 25 (JDK 25+)
- PostgreSQL 16.x running locally
- Maven 3.9+

## Environment setup

Copy `src/main/resources/application.properties.example` and set the required environment variables before running:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/jrostering
export DB_USERNAME=jrostering
export DB_PASSWORD=your-password
```

**Never hardcode `DB_PASSWORD` in `application.properties`.**

## Running locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The app starts on http://localhost:8080. Health check: http://localhost:8080/actuator/health

## Building

```bash
mvn clean package -DskipTests
java -jar target/JRostering-1.0-SNAPSHOT.jar
```

## Database migrations

Flyway runs automatically on startup. Migrations live in `src/main/resources/db/migration/`:
- `V1__baseline_schema.sql` — full baseline schema
- `V2__add_constraints_and_violation_detail.sql` — partial unique indices + violation_detail_json column
- `V3__seed_organisation.sql` — seeds the default organisation row

Add new migrations as `V4__description.sql`, etc. Never modify existing versioned scripts.

## Project structure

```
src/main/java/com/magicsystems/jrostering/
  domain/        JPA entities + enums
  repository/    Spring Data JPA repositories
  security/      Spring Security config, brute-force protection
  service/       Business logic (SolverService, StaffService, SiteService, RosterService)
  solver/        Timefold: RosterSolution, RosterConstraintProvider, RosterSolutionMapper
  report/        Excel report generation (Apache POI)
  api/           REST controllers (/api/**)
```

## Key architectural decisions

### Solver async split (Spring AOP proxy constraint)
`SolverService` → `SolverExecutor` (`@Async`) → `SolverTransactionHelper` (`@Transactional`)

Three separate Spring beans are required because `@Async` and `@Transactional` work via proxy
interception — a bean cannot intercept its own `this` calls. Each bean crosses a proxy boundary.

### In-memory brute-force lockout
`LoginAttemptService` tracks failed logins per username in a `ConcurrentHashMap`.
5 failures → 15-minute lockout. State is lost on restart (intentional for a single-PC deployment).

### Constraint scoring
`HardMediumSoftScore` three-tier hierarchy. Constraint defaults live in `ConstraintDefaults`.
Each `RuleType` has one `RuleConfiguration` row per site that activates exactly one of the
HARD/MEDIUM/SOFT variants via the `constraintLevel` field.

### ShiftAssignment persist strategy
`RosterSolutionMapper.persistSolution()` uses a two-step approach:
1. One bulk `UPDATE ShiftAssignment SET staff_id = NULL WHERE id IN (...)` clears all slots.
2. Only assigned (non-null) slots are loaded as managed entities via `findAllById`; Hibernate's
   dirty-check flushes the non-null UPDATEs in batches of 50 (`hibernate.jdbc.batch_size=50`).
For a fully INFEASIBLE solve, step 2 is skipped entirely. Total round trips: 1 + ⌈N'/50⌉ where
N' is the count of assigned slots.

## Vaadin 24 EOL notice

Vaadin 24 free maintenance ends **June 2026**. Upgrade path requires:
- Vaadin 25 (targets Spring Boot 4)
- Spring Boot 4 (targets Java 21+)
- Timefold 2.0 (currently beta; API-breaking changes from 1.x)

Track: https://vaadin.com/releases — plan upgrade before June 2026.

## Running tests

```bash
mvn test
```

Constraint unit tests use Timefold's `ConstraintVerifier` (no DB required).
Integration tests (when added) use Testcontainers — requires Docker.

## REST API

All REST endpoints are under `/api/**` and require HTTP Basic authentication.

- `POST /api/solver/{periodId}/submit` — start a solve job
- `DELETE /api/solver/{periodId}/cancel` — cancel running solve
- `GET /api/solver/jobs/{jobId}` — poll job status
- `GET /api/sites` — list sites
- `POST /api/sites` — create site
- `GET /api/roster/periods/{periodId}` — get roster period
- `GET /api/staff` — list staff

## Conventions

- No `spring.jpa.hibernate.ddl-auto=create/update` — Flyway owns all DDL.
- No `@Transactional` self-calls — always cross a proxy boundary.
- Service-layer validation is preferred over DB constraints where btree_gist is unavailable.
- Domain entities use Lombok `@Getter @Setter @NoArgsConstructor`.
- Staff and Sites are soft-deleted (set `active=false`), never hard-deleted.
