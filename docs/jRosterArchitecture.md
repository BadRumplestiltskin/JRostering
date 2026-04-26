# Universal Staff Rostering Application — Architecture & Data Model

**Version:** 1.3  
**Date:** April 2026  
**Status:** Implementation In Progress — Step 3 (Solver Infrastructure) Complete  

---

## Executive Summary

### What We Are Building

The Universal Staff Rostering Application is a web-based workforce scheduling system designed to automate and optimise the process of building staff rosters for organisations with complex scheduling requirements.

The system is built around a constraint optimisation engine — Timefold Solver — that automatically generates the best possible roster given a set of configurable rules. These rules govern everything from mandatory rest periods between shifts, to qualification requirements, to staff incompatibilities. The manager defines the rules once per site, and the solver applies them every time a new roster is generated.

The core design philosophy is that **no two organisations schedule staff the same way**. Rather than hardcoding business rules into the application, the system provides a library of configurable rule types that a roster manager can enable, disable, and tune to match their specific operational requirements — without any developer involvement.

### The Problem Being Solved

Building staff rosters manually is time-consuming, error-prone, and difficult to optimise. A typical roster manager must simultaneously satisfy dozens of competing constraints: ensuring adequate coverage, respecting staff leave, enforcing fatigue management rules, matching qualifications to shifts, and distributing hours fairly. When done by hand, this process can take hours each week and still produce suboptimal outcomes.

Existing commercial rostering software either:
- Locks rules into a fixed, non-configurable set tied to a specific industry, or
- Requires developer customisation to change scheduling logic, or
- Provides no transparency into why a roster was built a certain way, or
- Is prohibitively expensive for small-to-medium organisations.

This application addresses all of these gaps.

### Who Uses It

**Roster Managers** are the primary users. They are responsible for:
- Maintaining staff records, qualifications, and site assignments
- Configuring scheduling rules per site
- Defining shifts and staffing requirements for each roster period
- Submitting rosters to the solver and reviewing the output
- Publishing finalised rosters
- Generating reports

The application is single-user — one roster manager at a time — accessed via a standard web browser. The backend can run on a dedicated server or on a single local machine.

### Key Capabilities

| Capability | Description |
|---|---|
| **Automated Roster Generation** | The solver generates an optimised roster for up to 50 staff across a one or two fortnight planning horizon |
| **Configurable Rules** | Hard and soft scheduling rules are configured per site with adjustable parameters — no coding required |
| **Constraint Optimisation** | Hard rules are never violated; soft rules are weighted and balanced to produce the best achievable outcome |
| **Multi-Site Support** | A single organisation can have multiple sites, each with their own staff, shifts, and rule configurations |
| **Cross-Site Staff** | Staff members can be assigned to multiple sites and rostered across them; confirmed cross-site commitments are enforced as hard blocking constraints |
| **Qualification Enforcement** | Shifts can require a minimum count of staff holding specific qualifications |
| **Staff Incompatibility & Pairing** | Staff who must never share a shift, or who must always share a shift, are modelled as hard constraints |
| **Leave & Availability** | Approved leave is enforced as a hard constraint; recurring availability windows are enforced as a configurable hard or medium constraint |
| **Fatigue Management** | Minimum rest periods between shifts and maximum consecutive working days are configurable hard rules |
| **Asynchronous Solving** | The solver runs in the background; the manager is notified in-app when the result is ready or if no feasible solution exists |
| **Cancellable Solves** | An in-progress solve can be cancelled at any time; the best solution found so far is preserved |
| **Configurable Time Limit** | The maximum solve time is set by the manager per solve run |
| **Sequential Period Planning** | Two sequential fortnights can be planned together; replanning one period automatically triggers replanning of subsequent periods |
| **Pinned Assignments** | Individual shift assignments can be locked so the solver cannot change them |
| **Reporting** | Hours per staff member and rule violation summary reports are exportable to Excel |
| **Solver Transparency** | The final score and rule violation details are recorded and visible to the manager |

### What This Application Is Not

- It is not a payroll system, though hourly rates are stored and can support future payroll integration
- It is not a time and attendance system — actual clock-in/out is out of scope
- It is not a multi-tenant SaaS platform — it serves a single organisation
- It does not send notifications to staff members — roster publication and communication are out of scope for the initial version
- It does not integrate with external HR or payroll systems in the initial version, though the REST API layer is designed to support this

### Planning Horizon

The application plans rosters in fortnightly periods (14 days). A maximum of two sequential fortnights (28 days) can be planned simultaneously. Rosters are always generated for a complete period — partial period planning is not supported.

When a published roster requires replanning due to staff changes or other events, all subsequent roster periods are also replanned to maintain sequential consistency.

---

## Table of Contents

1. [Technology Stack](#1-technology-stack)
2. [Compatibility Findings](#2-compatibility-findings)
3. [System Architecture](#3-system-architecture)
4. [Module Structure](#4-module-structure)
5. [Data Model](#5-data-model)
6. [Rule Configuration — Parameter Schema](#6-rule-configuration--parameter-schema)
7. [Timefold Solver Design](#7-timefold-solver-design)
8. [Key Architecture Decisions](#8-key-architecture-decisions)
9. [Security Model](#9-security-model)
10. [Database Migration](#10-database-migration)
11. [Startup Recovery](#11-startup-recovery)
12. [Reports in Scope](#12-reports-in-scope-initial-version)
13. [Upgrade Path to Spring Boot 4 / Vaadin 25](#13-upgrade-path-to-spring-boot-4--vaadin-25)
14. [Java 25 Language Features & Immutability Strategy](#14-java-25-language-features--immutability-strategy)

---

## 1. Technology Stack

| Component | Version | Role |
|---|---|---|
| Java | 25 LTS | Runtime platform |
| Spring Boot | 3.5.x | Application framework |
| Spring Security | Via Boot BOM | Authentication and access control |
| Vaadin | 24.x | Browser-based UI (pure Java) |
| Timefold Solver | 1.31.0 | Constraint optimisation engine |
| Spring Data JPA | Via Boot BOM | Repository layer |
| Hibernate | 6.x | ORM (via Spring Boot BOM) |
| PostgreSQL | 16.x | Relational database |
| PostgreSQL JDBC Driver | 42.x | Database connectivity |
| Flyway | Via Boot BOM | Database schema migration |
| Apache POI | 5.x | Excel report export |
| Maven | 3.9.x | Build tool |

---

## 2. Compatibility Findings

Spring Boot 3.5.5 and Spring Boot 4.0.0 are both Java 25 ready. However, the following constraint applies at the time of writing (April 2026):

- **Timefold Solver 2.0** targets Spring Boot 4 and is currently in beta — not production ready.
- **Timefold Solver 1.31.0** (current stable) targets Spring Boot 3.x.
- **Vaadin 25** requires Spring Boot 4 / Spring Framework 7.
- **Vaadin 24** is Spring Boot 3.x compatible and remains in free maintenance until June 2026.

The only fully stable, production-ready combination is therefore **Spring Boot 3.5.x + Vaadin 24 + Timefold 1.31.0**.

The architecture is explicitly designed to make the future upgrade to Spring Boot 4 + Vaadin 25 + Timefold 2.0 straightforward, with no data model or constraint changes required.

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Single Spring Boot 3.5.x JVM               │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐ │
│  │  Vaadin 24   │  │  REST API    │  │  Timefold Solver  │ │
│  │  UI Layer    │  │  Layer       │  │  Engine           │ │
│  │  (Browser)   │  │  (Future     │  │  (Async)          │ │
│  │              │  │   clients)   │  │                   │ │
│  └──────┬───────┘  └──────┬───────┘  └────────┬──────────┘ │
│         │                 │                    │            │
│  ┌──────▼─────────────────▼────────────────────▼──────────┐ │
│  │                   Service Layer                         │ │
│  │  StaffService  SiteService  RosterService  ReportService│ │
│  └──────────────────────────┬──────────────────────────────┘ │
│                             │                               │
│  ┌──────────────────────────▼──────────────────────────────┐ │
│  │              Spring Data JPA / Hibernate 6               │ │
│  └──────────────────────────┬──────────────────────────────┘ │
└─────────────────────────────┼───────────────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │    PostgreSQL       │
                    └────────────────────┘
```

### Deployment Modes

| Mode | Description |
|---|---|
| Server | Spring Boot runs on a server; roster managers access via browser |
| Single PC | Spring Boot and PostgreSQL run locally on one machine; browser connects to localhost |

Both modes use the same deployable artifact — no configuration differences in application code.

---

## 4. Module Structure

```
rostering/
├── pom.xml
└── src/main/java/com/rostering/
    ├── RosteringApplication.java
    │
    ├── domain/                        # JPA Entities
    │   ├── Organisation.java
    │   ├── Site.java
    │   ├── Staff.java
    │   ├── Qualification.java
    │   ├── ShiftType.java
    │   ├── StaffQualification.java
    │   ├── StaffSiteAssignment.java
    │   ├── StaffIncompatibility.java
    │   ├── StaffPairing.java
    │   ├── StaffPreference.java
    │   ├── RosterPeriod.java
    │   ├── Shift.java
    │   ├── ShiftQualificationRequirement.java
    │   ├── ShiftAssignment.java
    │   ├── StaffAvailability.java
    │   ├── Leave.java
    │   ├── RuleConfiguration.java
    │   ├── RuleType.java              # Enum
    │   ├── SolverJob.java
    │   └── AppUser.java
    │
    ├── repository/                    # Spring Data JPA
    │   ├── OrganisationRepository.java
    │   ├── SiteRepository.java
    │   ├── StaffRepository.java
    │   ├── QualificationRepository.java
    │   ├── ShiftTypeRepository.java
    │   ├── StaffPreferenceRepository.java
    │   ├── RosterPeriodRepository.java
    │   ├── ShiftRepository.java
    │   ├── ShiftAssignmentRepository.java
    │   ├── LeaveRepository.java
    │   ├── RuleConfigurationRepository.java
    │   ├── SolverJobRepository.java
    │   └── AppUserRepository.java
    │
    ├── service/                       # Business Logic
    │   ├── StaffService.java
    │   ├── SiteService.java
    │   ├── RosterService.java
    │   ├── SolverService.java          # Orchestrator — validates state, creates SolverJob, dispatches async
    │   ├── SolverExecutor.java         # @Async entry point — holds active solvers, manages time limits
    │   ├── SolverTransactionHelper.java# @Transactional writes — all DB mutations for the solver lifecycle
    │   ├── ReportService.java
    │   ├── NotificationService.java
    │   ├── EntityNotFoundException.java
    │   ├── InvalidOperationException.java
    │   └── StartupRecoveryService.java
    │
    ├── solver/                        # Timefold Planning Domain
    │   ├── RosterSolution.java         # @PlanningSolution
    │   ├── CrossSiteBlockingPeriod.java# Java record — immutable problem fact value object
    │   ├── RosterConstraintProvider.java
    │   └── RosterSolutionMapper.java   # Builds solution from DB; persists solver result
    │
    ├── api/                           # REST Controllers
    │   ├── SiteController.java
    │   ├── StaffController.java
    │   ├── RosterController.java
    │   └── SolverController.java
    │
    ├── report/                        # Excel Export
    │   └── ExcelReportGenerator.java
    │
    ├── security/                      # Spring Security
    │   └── SecurityConfig.java
    │
    └── ui/                            # Vaadin Views
        ├── MainLayout.java
        ├── dashboard/
        ├── staff/
        ├── site/
        ├── roster/
        ├── rules/
        └── report/
```

---

## 5. Data Model

### 5.1 Entity Relationship Overview

```
Organisation
    └── Site (many)
            ├── ShiftType (many)
            ├── RuleConfiguration (many — one per rule type)
            ├── RosterPeriod (many)
            │       ├── previous_period_id ──► RosterPeriod (self-referential, nullable)
            │       └── Shift (many)
            │               ├── ShiftQualificationRequirement (many)
            │               └── ShiftAssignment (many) ◄── Planning Entity
            └── StaffSiteAssignment (many)

Staff
    ├── StaffQualification (many)
    ├── StaffAvailability (many)
    ├── StaffPreference (many)
    ├── Leave (many)
    ├── StaffIncompatibility (many) ◄── mutual
    ├── StaffPairing (many) ◄── mutual
    └── StaffSiteAssignment (many)

Qualification (reference data — scoped to Organisation)
ShiftType (reference data — scoped to Site)
AppUser

SolverJob
    └── RosterPeriod (one)
```

---

### 5.2 ORGANISATION

```sql
id               BIGSERIAL        PRIMARY KEY
name             VARCHAR(255)     NOT NULL
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL
```

---

### 5.3 SITE

```sql
id               BIGSERIAL        PRIMARY KEY
organisation_id  BIGINT           NOT NULL  REFERENCES ORGANISATION(id)
name             VARCHAR(255)     NOT NULL
timezone         VARCHAR(100)     NOT NULL   -- e.g. Australia/Adelaide
address          VARCHAR(500)
active           BOOLEAN          NOT NULL   DEFAULT true
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL
```

---

### 5.4 QUALIFICATION

```sql
id               BIGSERIAL        PRIMARY KEY
organisation_id  BIGINT           NOT NULL  REFERENCES ORGANISATION(id)
name             VARCHAR(255)     NOT NULL   -- e.g. First Aid, Fire Warden
description      TEXT
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL

UNIQUE (organisation_id, name)
```

---

### 5.5 SHIFT_TYPE

Shift type reference data scoped to a site. Gives shifts a stable classification (e.g. Morning, Afternoon, Night) that staff preferences can reference. A shift is not required to have a shift type — it is optional.

```sql
id               BIGSERIAL        PRIMARY KEY
site_id          BIGINT           NOT NULL  REFERENCES SITE(id)
name             VARCHAR(100)     NOT NULL
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL

UNIQUE (site_id, name)
```

---

### 5.6 STAFF

```sql
id                         BIGSERIAL       PRIMARY KEY
organisation_id            BIGINT          NOT NULL  REFERENCES ORGANISATION(id)
first_name                 VARCHAR(100)    NOT NULL
last_name                  VARCHAR(100)    NOT NULL
email                      VARCHAR(255)    NOT NULL
phone                      VARCHAR(50)
employment_type            VARCHAR(50)     NOT NULL  -- FULL_TIME | PART_TIME | CASUAL
contracted_hours_per_week  DECIMAL(5,2)              -- NULL = no contracted hours
hourly_rate                DECIMAL(10,2)
active                     BOOLEAN         NOT NULL  DEFAULT true
created_at                 TIMESTAMPTZ     NOT NULL
updated_at                 TIMESTAMPTZ     NOT NULL

UNIQUE (organisation_id, email)
```

---

### 5.7 STAFF_QUALIFICATION

```sql
id               BIGSERIAL        PRIMARY KEY
staff_id         BIGINT           NOT NULL  REFERENCES STAFF(id)
qualification_id BIGINT           NOT NULL  REFERENCES QUALIFICATION(id)
awarded_date     DATE
created_at       TIMESTAMPTZ      NOT NULL

UNIQUE (staff_id, qualification_id)
```

---

### 5.8 STAFF_SITE_ASSIGNMENT

```sql
id               BIGSERIAL        PRIMARY KEY
staff_id         BIGINT           NOT NULL  REFERENCES STAFF(id)
site_id          BIGINT           NOT NULL  REFERENCES SITE(id)
primary_site     BOOLEAN          NOT NULL  DEFAULT false
created_at       TIMESTAMPTZ      NOT NULL

UNIQUE (staff_id, site_id)
```

---

### 5.9 STAFF_INCOMPATIBILITY

Mutual exclusion — staff A and staff B must never be on the same shift.

```sql
id               BIGSERIAL        PRIMARY KEY
staff_a_id       BIGINT           NOT NULL  REFERENCES STAFF(id)
staff_b_id       BIGINT           NOT NULL  REFERENCES STAFF(id)
reason           TEXT
created_at       TIMESTAMPTZ      NOT NULL

UNIQUE (staff_a_id, staff_b_id)
CHECK  (staff_a_id < staff_b_id)   -- canonical ordering prevents duplicate pairs
```

---

### 5.10 STAFF_PAIRING

Must-pair — staff A and staff B must always be on the same shift.

```sql
id               BIGSERIAL        PRIMARY KEY
staff_a_id       BIGINT           NOT NULL  REFERENCES STAFF(id)
staff_b_id       BIGINT           NOT NULL  REFERENCES STAFF(id)
reason           TEXT
created_at       TIMESTAMPTZ      NOT NULL

UNIQUE (staff_a_id, staff_b_id)
CHECK  (staff_a_id < staff_b_id)   -- canonical ordering prevents duplicate pairs
```

---

### 5.11 STAFF_AVAILABILITY

Recurring weekly availability windows per staff member.

```sql
id               BIGSERIAL        PRIMARY KEY
staff_id         BIGINT           NOT NULL  REFERENCES STAFF(id)
day_of_week      VARCHAR(20)      NOT NULL  -- MONDAY | TUESDAY | ... | SUNDAY
start_time       TIME             NOT NULL
end_time         TIME             NOT NULL
available        BOOLEAN          NOT NULL  DEFAULT true
created_at       TIMESTAMPTZ      NOT NULL
```

---

### 5.12 STAFF_PREFERENCE

Stores staff scheduling preferences used by soft constraint rules. A staff member may have multiple preferences of different types. `day_of_week` is populated for `PREFERRED_DAY_OFF` preferences; `shift_type_id` is populated for shift type preferences. The check constraint enforces that exactly one of the two fields is populated per preference type.

```sql
id                BIGSERIAL       PRIMARY KEY
staff_id          BIGINT          NOT NULL  REFERENCES STAFF(id)
preference_type   VARCHAR(50)     NOT NULL  -- PREFERRED_DAY_OFF | PREFERRED_SHIFT_TYPE | AVOID_SHIFT_TYPE
day_of_week       VARCHAR(20)               -- populated for PREFERRED_DAY_OFF; NULL otherwise
shift_type_id     BIGINT                    REFERENCES SHIFT_TYPE(id)
                                            -- populated for PREFERRED_SHIFT_TYPE / AVOID_SHIFT_TYPE; NULL otherwise
created_at        TIMESTAMPTZ     NOT NULL

CHECK (
  (preference_type = 'PREFERRED_DAY_OFF'
      AND day_of_week IS NOT NULL
      AND shift_type_id IS NULL)
  OR
  (preference_type IN ('PREFERRED_SHIFT_TYPE', 'AVOID_SHIFT_TYPE')
      AND shift_type_id IS NOT NULL
      AND day_of_week IS NULL)
)
```

---

### 5.13 LEAVE

Specific date-range leave requests and approvals. The `STAFF_LEAVE_BLOCK` hard rule applies to `APPROVED` status only. The `HONOUR_REQUESTED_LEAVE` soft rule applies to `REQUESTED` status only.

```sql
id               BIGSERIAL        PRIMARY KEY
staff_id         BIGINT           NOT NULL  REFERENCES STAFF(id)
start_date       DATE             NOT NULL
end_date         DATE             NOT NULL
leave_type       VARCHAR(50)      NOT NULL  -- ANNUAL | SICK | PUBLIC_HOLIDAY | OTHER
status           VARCHAR(50)      NOT NULL  -- REQUESTED | APPROVED | REJECTED
notes            TEXT
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL

CHECK (end_date >= start_date)
```

---

### 5.14 RULE_CONFIGURATION

One row per rule type per site. The `constraint_level` column governs whether a rule contributes to the hard, medium, or soft component of the `HardMediumSoftScore`. It replaces the original `is_hard BOOLEAN` to support all three scoring levels. `weight` applies to `SOFT` constraint-level rules only. The `enabled` flag is the sole activation switch across all rules. Rules with no meaningful parameters store `{}` in `parameter_json`.

```sql
id               BIGSERIAL        PRIMARY KEY
site_id          BIGINT           NOT NULL  REFERENCES SITE(id)
rule_type        VARCHAR(100)     NOT NULL  -- maps to RuleType enum
enabled          BOOLEAN          NOT NULL  DEFAULT true
constraint_level VARCHAR(10)      NOT NULL  CHECK (constraint_level IN ('HARD', 'MEDIUM', 'SOFT'))
weight           INTEGER                    -- SOFT constraint_level only; higher = more important
parameter_json   JSONB            NOT NULL  -- rule-specific parameters; {} for parameterless rules
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL

UNIQUE (site_id, rule_type)
```

---

### 5.15 ROSTER_PERIOD

A planning period, always exactly one fortnight (14 days). Up to two sequential periods may be active at once. `previous_period_id` is the authoritative link between sequential periods. `sequence_number` is retained as a display convenience but is derived from the FK chain and is not authoritative.

```sql
id                   BIGSERIAL        PRIMARY KEY
site_id              BIGINT           NOT NULL  REFERENCES SITE(id)
previous_period_id   BIGINT                     REFERENCES ROSTER_PERIOD(id)  -- NULL for the first period in a chain
start_date           DATE             NOT NULL
end_date             DATE             NOT NULL  -- always start_date + 13 days
status               VARCHAR(50)      NOT NULL  -- DRAFT | SOLVING | SOLVED |
                                               --   PUBLISHED | INFEASIBLE | CANCELLED
sequence_number      INTEGER          NOT NULL  -- 1 or 2; display convenience only — previous_period_id is authoritative
published_at         TIMESTAMPTZ
created_at           TIMESTAMPTZ      NOT NULL
updated_at           TIMESTAMPTZ      NOT NULL

CHECK (end_date = start_date + INTERVAL '13 days')
```

---

### 5.16 SHIFT

An individual shift instance within a roster period, with arbitrary start and end times. `shift_type_id` is a nullable FK to `SHIFT_TYPE`. Assigning a shift type enables the `PREFERRED_SHIFT_TYPE` and `AVOID_SHIFT_TYPE` soft constraint rules to evaluate staff preferences for that shift.

```sql
id               BIGSERIAL        PRIMARY KEY
roster_period_id BIGINT           NOT NULL  REFERENCES ROSTER_PERIOD(id)
shift_type_id    BIGINT                     REFERENCES SHIFT_TYPE(id)  -- nullable
name             VARCHAR(255)               -- optional label e.g. "Morning"
start_datetime   TIMESTAMPTZ      NOT NULL
end_datetime     TIMESTAMPTZ      NOT NULL
minimum_staff    INTEGER          NOT NULL  DEFAULT 1
notes            TEXT
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL

CHECK (end_datetime > start_datetime)
```

---

### 5.17 SHIFT_QUALIFICATION_REQUIREMENT

Minimum number of staff holding a given qualification that must be present on a shift.

```sql
id               BIGSERIAL        PRIMARY KEY
shift_id         BIGINT           NOT NULL  REFERENCES SHIFT(id)
qualification_id BIGINT           NOT NULL  REFERENCES QUALIFICATION(id)
minimum_count    INTEGER          NOT NULL  DEFAULT 1

UNIQUE (shift_id, qualification_id)
CHECK  (minimum_count >= 1)
```

---

### 5.18 SHIFT_ASSIGNMENT

**This is the Timefold planning entity.** `ShiftAssignment` rows are created automatically by `RosterService` when a `Shift` is saved. The initial slot count equals `minimum_staff`. The manager may add or remove slots above the minimum before solving; a slot may only be removed if `staff_id IS NULL` and `pinned = false`. The solver assigns `staff_id` to each slot during a solve run.

On an `INFEASIBLE` result, the best partial solution returned by the solver is written to the database — slots the solver could not fill remain `NULL`. On a `CANCELLED` solve, the best solution found before termination is similarly preserved.

```sql
id               BIGSERIAL        PRIMARY KEY
shift_id         BIGINT           NOT NULL  REFERENCES SHIFT(id)
staff_id         BIGINT                     REFERENCES STAFF(id)  -- NULL before solving, or on an unresolvable slot
pinned           BOOLEAN          NOT NULL  DEFAULT false          -- true = solver cannot change this assignment
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL
```

---

### 5.19 SOLVER_JOB

Tracks the lifecycle of each asynchronous solve operation. On an `INFEASIBLE` result, `final_score` records the score of the best partial solution (which will contain negative hard score values). On a `FAILED` result, `error_message` is populated. Orphaned jobs in `RUNNING` or `QUEUED` status at application startup are resolved by `StartupRecoveryService` (see Section 11).

```sql
id                   BIGSERIAL       PRIMARY KEY
roster_period_id     BIGINT          NOT NULL  REFERENCES ROSTER_PERIOD(id)
status               VARCHAR(50)     NOT NULL  -- QUEUED | RUNNING | COMPLETED |
                                              --   CANCELLED | FAILED | INFEASIBLE
started_at           TIMESTAMPTZ
completed_at         TIMESTAMPTZ
time_limit_seconds   INTEGER         NOT NULL
final_score          VARCHAR(100)              -- e.g. "0hard/-3medium/-42soft"
                                              --   or "-2hard/0medium/-10soft" when INFEASIBLE
infeasible_reason    TEXT                      -- populated when status = INFEASIBLE
error_message        TEXT                      -- populated when status = FAILED
created_at           TIMESTAMPTZ     NOT NULL
updated_at           TIMESTAMPTZ     NOT NULL
```

---

### 5.20 APP_USER

One row per roster manager authorised to access the application. Passwords are stored as bcrypt hashes.

```sql
id               BIGSERIAL        PRIMARY KEY
username         VARCHAR(100)     NOT NULL  UNIQUE
password_hash    VARCHAR(255)     NOT NULL  -- bcrypt
active           BOOLEAN          NOT NULL  DEFAULT true
created_at       TIMESTAMPTZ      NOT NULL
updated_at       TIMESTAMPTZ      NOT NULL
```

---

## 6. Rule Configuration — Parameter Schema

Each `RULE_CONFIGURATION` row stores rule-specific parameters in the `parameter_json` JSONB column. The `enabled` column is the sole activation switch for all rules — there is no logic in any other layer that suppresses a rule. Rules with no meaningful parameters store `{}`.

### 6.1 Hard Rules (default constraint_level = 'HARD')

| Rule Type | parameter_json Schema | Notes |
|---|---|---|
| `MIN_REST_BETWEEN_SHIFTS` | `{ "minimumRestHours": 10 }` | |
| `MAX_HOURS_PER_DAY` | `{ "maximumHours": 12 }` | |
| `MAX_HOURS_PER_WEEK` | `{ "maximumHours": 38 }` | |
| `MAX_CONSECUTIVE_DAYS` | `{ "maximumDays": 5 }` | |
| `MIN_STAFF_PER_SHIFT` | `{}` | minimum_staff is on the Shift entity |
| `MIN_QUALIFIED_STAFF_PER_SHIFT` | `{}` | minimum_count is on ShiftQualificationRequirement |
| `QUALIFICATION_REQUIRED_FOR_SHIFT` | `{}` | |
| `STAFF_MUTUAL_EXCLUSION` | `{}` | pairs are in STAFF_INCOMPATIBILITY table |
| `STAFF_MUST_PAIR` | `{}` | pairs are in STAFF_PAIRING table |
| `STAFF_LEAVE_BLOCK` | `{}` | applies to Leave.status = APPROVED only; always hard |
| `STAFF_AVAILABILITY_BLOCK` | `{}` | applies to STAFF_AVAILABILITY table; constraint_level may be set to MEDIUM per site for advisory availability |

### 6.2 Soft Rules (default constraint_level = 'SOFT')

| Rule Type | parameter_json Schema | Notes |
|---|---|---|
| `PREFERRED_DAYS_OFF` | `{ "penaltyPerViolation": 1 }` | evaluated against StaffPreference rows of type PREFERRED_DAY_OFF |
| `PREFERRED_SHIFT_TYPE` | `{ "penaltyPerViolation": 1 }` | evaluated against StaffPreference rows of type PREFERRED_SHIFT_TYPE and AVOID_SHIFT_TYPE |
| `HONOUR_REQUESTED_LEAVE` | `{ "penaltyPerViolation": 5 }` | applies to Leave.status = REQUESTED only |
| `FAIR_HOURS_DISTRIBUTION` | `{ "maximumDeviationHours": 4 }` | |
| `FAIR_WEEKEND_DISTRIBUTION` | `{ "maximumDeviationShifts": 2 }` | |
| `FAIR_NIGHT_SHIFT_DISTRIBUTION` | `{ "maximumDeviationShifts": 2 }` | |
| `MINIMISE_UNDERSTAFFING` | `{ "penaltyPerMissingStaff": 10 }` | |
| `MINIMISE_OVERSTAFFING` | `{ "penaltyPerExtraStaff": 1 }` | |
| `AVOID_EXCESSIVE_CONSECUTIVE_DAYS` | `{ "preferredMaxDays": 4, "penaltyPerExtraDay": 2 }` | |
| `SOFT_MAX_HOURS_PER_PERIOD` | `{ "maximumHours": 76, "penaltyPerExtraHour": 3 }` | |

---

## 7. Timefold Solver Design

### 7.1 Planning Solution Structure

```
RosterSolution  (@PlanningSolution)
│
├── @ProblemFactCollectionProperty
│   ├── List<Shift>
│   ├── List<Staff>
│   ├── List<ShiftType>
│   ├── List<Qualification>
│   ├── List<StaffQualification>
│   ├── List<StaffPreference>
│   ├── List<ShiftQualificationRequirement>
│   ├── List<Leave>                        -- APPROVED and REQUESTED
│   ├── List<StaffAvailability>
│   ├── List<StaffIncompatibility>
│   ├── List<StaffPairing>
│   ├── List<CrossSiteBlockingPeriod>      -- see Section 7.5
│   └── List<RuleConfiguration>
│
├── @PlanningEntityCollectionProperty
│   └── List<ShiftAssignment>    ← solver assigns staff_id on each slot
│
├── @ValueRangeProvider
│   └── List<Staff>              ← pool from which solver selects
│
└── @PlanningScore
    └── HardMediumSoftScore
        ├── hard   — must never be violated (leave blocks, qualifications, rest rules,
        │            availability when constraint_level = HARD, cross-site blocking)
        ├── medium — near-hard; breakable only when no feasible solution exists
        │            (availability when constraint_level = MEDIUM)
        └── soft   — weighted preference optimisation (fairness, preferences, overstaffing)
```

### 7.2 Score Rationale

`HardMediumSoftScore` is used in preference to `HardSoftScore` to allow a three-level hierarchy:

- **Hard** — absolute constraints that a valid roster can never violate. Approved leave (`STAFF_LEAVE_BLOCK`) is always hard. Cross-site blocking is always hard.
- **Medium** — constraints treated as near-absolute but breakable only when no feasible solution exists. `STAFF_AVAILABILITY_BLOCK` may be configured at this level per site, for organisations where availability windows are advisory rather than absolute.
- **Soft** — weighted preference optimisation.

### 7.3 Async Solve Lifecycle

The solver lifecycle is split across three Spring beans to satisfy two independent Spring AOP constraints:

- **`@Async` requires a cross-proxy call** — if `submitSolve` called an `@Async` method on itself (`this`), the annotation would be bypassed and the solve would block the web thread. `SolverExecutor` is a separate bean so the `@Async` proxy fires correctly.
- **`@Transactional` requires a cross-proxy call** — same root cause. `SolverTransactionHelper` is a separate bean so each write to the database runs in its own proper transaction. All methods accept Long IDs and load fresh managed entities internally to prevent stale-state merges.

```
Manager clicks Solve
        │
        ▼
SolverService.submitSolve()                          ← @Transactional (web thread)
        │  Validates period status (DRAFT or INFEASIBLE)
        │  Creates SolverJob (status=QUEUED)
        │  Sets RosterPeriod (status=SOLVING)
        │  Calls solverExecutor.executeSolveAsync()  ← crosses proxy boundary → @Async fires
        │  Transaction commits; job row now visible to background thread
        ▼ returns SolverJob (QUEUED) to caller immediately

SolverExecutor.executeSolveAsync()                   ← @Async background thread
        │
        ├── txHelper.markJobRunning(jobId)           ← own @Transactional
        │       If fails → txHelper.revertPeriodToDraft(periodId); return
        │
        ├── solver = solverFactory.buildSolver()
        ├── activeSolvers.put(periodId, solver)
        ├── Schedule terminateEarly() after timeLimitSeconds (daemon ScheduledExecutorService)
        │
        ├── solutionMapper.buildSolution(periodId)   ← own @Transactional(readOnly=true)
        │       Loads all 14 solution collections; builds CrossSiteBlockingPeriods
        │
        ├── solver.solve(problem)                    ← blocks until done for any reason
        │
        ├── On exception during build or solve:
        │       txHelper.persistFailed(jobId, periodId, e)
        │       return
        │
        ├── Check cancelRequested.remove(periodId)
        │
        ├── On cancel ──────► txHelper.persistCancelled(jobId, periodId, result)
        │                         └── solutionMapper.persistSolution(result)
        │                             SolverJob → CANCELLED
        │                             RosterPeriod → DRAFT
        │                             NotificationService.notifySolveCancelled()
        │
        └── On natural termination — check HardMediumSoftScore:
                │
                ├── hard==0 AND medium==0
                │       txHelper.persistCompleted(jobId, periodId, result)
                │           └── solutionMapper.persistSolution(result)
                │               SolverJob → COMPLETED, final_score recorded
                │               RosterPeriod → SOLVED
                │               NotificationService.notifySolveCompleted()
                │
                └── hard<0 OR medium<0
                        txHelper.persistInfeasible(jobId, periodId, result, reason)
                            └── solutionMapper.persistSolution(result)
                                (unresolvable slots retain staff_id = NULL)
                                SolverJob → INFEASIBLE, score and reason recorded
                                RosterPeriod → INFEASIBLE
                                NotificationService.notifySolveInfeasible()
```

#### Cancellation

`SolverService.cancelSolve()` validates that the period is in SOLVING status, then calls `SolverExecutor.requestCancel(periodId)`. This adds the period ID to a `ConcurrentHashMap` key set (`cancelRequested`) and calls `solver.terminateEarly()` on the active solver instance. When `executeSolveAsync` returns from `solver.solve()`, it checks `cancelRequested` to distinguish a manager-cancelled solve from natural termination.

There is an accepted TOCTOU window between the status check and the `terminateEarly()` signal: the solver may finish between the two calls. In that case the solve has already completed successfully, the period transitions out of SOLVING, and the cancel silently becomes a no-op. Eliminating this race would require a distributed lock that adds complexity disproportionate to the edge case.

### 7.4 Multi-Period Replanning

When two sequential roster periods are active, `previous_period_id` on `RosterPeriod` is the authoritative link between them.

1. Period 1 is solved first and published.
2. Period 1 `ShiftAssignment` rows are marked `pinned = true`.
3. Period 2 is solved using Period 1 pinned assignments as fixed problem facts.
4. If Period 1 requires replanning (e.g. staff changes), Period 2 `ShiftAssignment` rows are cleared (`staff_id = NULL`, `pinned = false`) and both periods are re-solved sequentially.

### 7.5 Cross-Site Blocking

Staff members assigned to multiple sites may have confirmed shift commitments at a site other than the one currently being solved. To prevent double-booking, `RosterSolutionMapper` queries for all `ShiftAssignment` rows for cross-site staff where the assignment belongs to a different site, the `RosterPeriod.status` is `PUBLISHED` or `SOLVED`, and the assignment falls within the same planning window as the current solve.

These cross-site assignments are projected into a lightweight `CrossSiteBlockingPeriod` value object (staff reference, start datetime, end datetime) and injected into `RosterSolution` as a `@ProblemFactCollectionProperty`. `RosterConstraintProvider` evaluates them using the same time-overlap logic as `STAFF_LEAVE_BLOCK`. Cross-site blocking is always a hard constraint regardless of any site-level rule configuration — it is not governed by a `RULE_CONFIGURATION` row.

---

## 8. Key Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Spring Boot version | 3.5.x | Only stable version compatible with both Timefold 1.x and Java 25 |
| Vaadin version | 24.x | Spring Boot 3.x compatible; well-defined upgrade path to V25 |
| Score type | HardMediumSoftScore | Three levels give finer constraint priority control than two |
| Planning entity | ShiftAssignment (slot) not Shift | Naturally supports variable staff counts per shift |
| Rule parameters | JSONB column | Flexible per rule type without requiring schema migrations |
| Rule constraint level | constraint_level VARCHAR(10) replacing is_hard BOOLEAN | Supports HARD / MEDIUM / SOFT classification; required for configurable STAFF_AVAILABILITY_BLOCK |
| Parameterless rules | Store `{}` in parameter_json | Consistent representation; enabled is the sole activation switch |
| Incompatibility ordering | CHECK staff_a_id < staff_b_id | Prevents duplicate pairs in both directions; single canonical row |
| Async solving | SolverJob entity + Spring @Async | Decouples UI from solver; state survives page refresh |
| Solver three-bean split | SolverService → SolverExecutor → SolverTransactionHelper | Spring AOP requires inter-bean calls for both @Async and @Transactional to function. A single class cannot self-call @Async or @Transactional methods via `this`. See Section 7.3. |
| SolverTransactionHelper ID-based loading | Accept Long IDs; load fresh entities at start of each @Transactional method | Prevents stale-state merges from detached entities written concurrently across threads. JPA only guarantees correctness when the entity being saved is managed within the current transaction. |
| INFEASIBLE handling | Preserve best partial solution in DB | Gives manager diagnostic visibility into which slots are unresolvable; Timefold always returns the best solution found |
| Cancelled solve | Preserve best solution found so far | Consistent with infeasible handling; manager retains partial work |
| Slot pre-creation | Auto-created by RosterService on Shift save | No separate prepare step; slot count is reconciled when minimum_staff changes |
| Multi-period linking | previous_period_id FK on RosterPeriod | Explicit, queryable relationship; sequence_number retained as display convenience only |
| Staff preferences | STAFF_PREFERENCE table + SHIFT_TYPE reference entity | Provides stable, typed reference for soft constraint evaluation |
| Cross-site blocking | CrossSiteBlockingPeriod injected as hard problem facts | Prevents double-booking across sites without requiring a global solver |
| Approved leave | Hard constraint (STAFF_LEAVE_BLOCK) | Approved leave is never violated under any circumstances |
| Requested leave | Soft constraint (HONOUR_REQUESTED_LEAVE) | Respected where possible; breakable when necessary |
| Availability enforcement | Configurable HARD (default) or MEDIUM per site | Organisations differ on whether declared availability is absolute or advisory |
| Authentication | Spring Security form-based (Vaadin) + HTTP Basic (REST) | Natural fit for single-organisation, low-concurrency deployment |
| User storage | APP_USER table with bcrypt | Self-contained; no credential hardcoding in properties files |
| Database migration | Flyway | SQL-native; auto-runs on startup; minimal overhead for single-developer project |
| Startup recovery | StartupRecoveryService on ApplicationReadyEvent | Clears orphaned RUNNING/QUEUED jobs to FAILED on restart; resets RosterPeriod to DRAFT |
| Single deployable | One Spring Boot JAR | Simplifies deployment to both server and single-PC modes |
| REST API layer included | Yes (alongside Vaadin) | Enables future integration with mobile apps or payroll systems |
| UI framework | Vaadin 24 Flow | Pure Java, no JavaScript required, integrates natively with Spring Boot |

---

## 9. Security Model

### 9.1 Authentication

Spring Security provides authentication for both the Vaadin UI and the REST API layer within the same filter chain.

| Layer | Mechanism |
|---|---|
| Vaadin UI | Form-based login with HTTP session; unauthenticated requests are redirected to the login view |
| REST API | HTTP Basic authentication; stateless |

User credentials are stored in the `APP_USER` table. Passwords are stored as bcrypt hashes. Spring Security's `UserDetailsService` is backed by `AppUserRepository`.

### 9.2 Authorisation

All authenticated users have full access to all application features. Role-based access control is not required in the initial version — all users are roster managers.

### 9.3 Session Handling

Vaadin manages its own server-side session. Spring Security's `HttpSessionSecurityContextRepository` binds the security context to the Vaadin session. The REST API is stateless — each request carries HTTP Basic credentials and no session is created or maintained.

---

## 10. Database Migration

Flyway manages all schema changes. Migration scripts are stored under `src/main/resources/db/migration` and named using Flyway's standard versioned convention (`V1__initial_schema.sql`, `V2__description.sql`, etc.). Spring Boot auto-configures Flyway to execute pending migrations on application startup, before the application context finishes initialising.

The baseline migration (`V1__initial_schema.sql`) captures the complete schema defined in Section 5. All subsequent structural changes are applied as incremental versioned scripts. No manual DDL changes are made directly to the database outside of Flyway.

---

## 11. Startup Recovery

`StartupRecoveryService` implements `ApplicationListener<ApplicationReadyEvent>` and executes once after the application context is fully started. It performs the following recovery procedure before the UI becomes accessible:

1. Query for all `SolverJob` rows where `status IN ('RUNNING', 'QUEUED')`.
2. For each orphaned job, set `status = 'FAILED'`, set `error_message = 'Interrupted by application restart'`, and set `completed_at` to the current timestamp.
3. For each corresponding `RosterPeriod`, set `status = 'DRAFT'`.
4. Commit all changes in a single transaction.

Attempting to resume an interrupted solve is not safe — the `ShiftAssignment` state from a partial run is undefined. The manager must re-submit the solve manually after recovery.

---

## 12. Reports in Scope (Initial Version)

| Report | Description | Export |
|---|---|---|
| Hours Per Staff Member | Total scheduled hours per staff member for the roster period | Excel |
| Rule Violation Summary | List of all soft rule violations in the solved roster with penalty scores | Excel |

All reports are generated for a complete roster period. Date range selection is not supported in the initial version.

---

## 13. Upgrade Path to Spring Boot 4 / Vaadin 25

When Timefold Solver 2.0 reaches stable release, the upgrade requires:

| Step | Action |
|---|---|
| 1 | Bump `spring-boot-starter-parent` to `4.0.x` in `pom.xml` |
| 2 | Bump `vaadin` to `25.x` in `pom.xml` |
| 3 | Bump `timefold-solver-bom` to `2.0.x` in `pom.xml` |
| 4 | Migrate Spring Security configuration (breaking changes in Spring Security 7) |
| 5 | Review Vaadin theming — Lumo theme migration is minor; Material theme requires rework |
| 6 | Run full regression test suite |

**No data model changes are required.**  
**No constraint provider changes are required.**  
**No service layer changes are required.**

---

## 14. Java 25 Language Features & Immutability Strategy

### 14.1 Why JPA Entities Cannot Be Records

Java records are immutable by definition. Every component is `final`; the compiler generates no setters. This is directly incompatible with two frameworks central to this architecture:

**Hibernate (JPA persistence)**

Hibernate requires:
- A no-arg constructor to instantiate entity objects when hydrating rows from a `ResultSet`.
- Mutable fields for dirty-tracking — Hibernate detects changes between load and flush by comparing old and new field values, or via bytecode-enhanced interceptors that intercept field writes.
- Lifecycle callback support (`@PrePersist`, `@EntityListeners`) that operates on a mutable in-flight entity.

None of these are compatible with records. There is no partial workaround — the JPA specification requires mutable, no-arg-constructible classes as entities.

**Timefold Solver (planning entities and solution)**

Timefold's search algorithm mutates planning variables during solving:
- `ShiftAssignment.staff` is a `@PlanningVariable` that Timefold sets and resets thousands of times per second during the solve. A record field is `final` — this mutation is impossible.
- `RosterSolution` carries a `@PlanningScore` that Timefold writes after each score calculation. Same constraint applies.

**Conclusion: all 18 JPA entities, `ShiftAssignment`, and `RosterSolution` must remain mutable Lombok-annotated classes. Records cannot be used for these.**

### 14.2 Where Records Are Used (and Will Be Used)

Records are the right choice for immutable value objects and data carriers with no behaviour.

| Class / Category | Status | Notes |
|---|---|---|
| `CrossSiteBlockingPeriod` | **Already a record** | Immutable problem fact: staff reference + start/end datetime. Constructed once, read by constraints, never mutated. |
| Service command objects (`SiteCreateRequest`, `StaffCreateRequest`, `ShiftCreateRequest`, etc.) | **Already records** | Carry validated input from the UI or REST API into the service layer. Immutable by nature — created once per request. |
| REST API response DTOs | **Record when built** | The API layer has not yet been implemented. All response payloads must be Java records — they are serialised to JSON and never modified after construction. |
| Spring Data JPA projections | **Record when built** | Query result projections (e.g. summary views, report data carriers) must use record interfaces or record classes rather than mutable projection classes. |
| Report row carriers | **Record when built** | `ExcelReportGenerator` row-carrier types must be records. |

### 14.3 Other Java 25 Features Applied

| Feature | Applied Where | Status |
|---|---|---|
| **Text blocks** (`"""..."""`) | All multi-line JPQL `@Query` strings in repositories | ✓ Applied |
| **`Stream.toList()`** (Java 16+) | All terminal `stream().collect(Collectors.toList())` calls in `RosterSolutionMapper` | ✓ Applied |
| **`Objects::nonNull`** method reference | Filter expressions in `RosterSolutionMapper` stream pipelines | ✓ Applied |
| **`var` local type inference** | Recommended for local variable declarations where the type is obvious from context | Adopt on new code |
| **Pattern matching for `instanceof`** | Any `instanceof` check followed by a cast | Adopt on new code |
| **Switch expressions** | Multi-branch logic in constraint provider helpers | Adopt on new code |
| **Sealed interfaces** | Exception hierarchy (`EntityNotFoundException`, `InvalidOperationException` can share a sealed `AppException` parent) | Planned |
| **Virtual threads** | Spring Boot `@Async` executor for solver background threads | See §14.4 |
| **Sequenced collections** (`SequencedCollection<T>`, Java 21+) | Where ordered collections are returned and the first/last element is accessed | Adopt on new code |

### 14.4 Virtual Threads

Java 25 virtual threads (Project Loom, stable since Java 21) are fully supported by Spring Boot 3.2+ via a single property:

```properties
spring.threads.virtual.enabled=true
```

When enabled, Spring Boot mounts the `@Async` executor, the Tomcat/Jetty connector, and Spring Security's filter chain on virtual threads rather than platform threads. This is beneficial for this application because:

- The Timefold solver runs on a `@Async` background thread. Virtual threads have no pinning risk here — the solver is CPU-bound, not IO-blocked.
- The web layer (Vaadin + REST) handles one roster manager at a time. Platform threads are not a scalability bottleneck, but virtual threads carry no cost either.
- Virtual threads eliminate the `ScheduledExecutorService` daemon thread overhead for the time-limit scheduler (though the daemon scheduler is retained because `ScheduledExecutorService` does not natively run on virtual threads in Java 25).

**Status:** `spring.threads.virtual.enabled=true` is set in `application.properties`.

### 14.5 Lombok Retention Policy

Lombok remains on the classpath for all JPA entities and mutable service/solver classes. It is removed from new code in the following cases:

| Case | Replace Lombok With |
|---|---|
| Immutable value object | Java `record` |
| Immutable DTO / API response | Java `record` |
| Immutable command object | Java `record` |
| Spring `@Service` / `@Component` with only constructor injection | `@RequiredArgsConstructor` (Lombok) is retained — manual constructor adds no value for DI-only classes |
| Exception classes | Manual — exception classes are short and cannot be records (records cannot extend `Throwable`) |

---

*End of Document*
