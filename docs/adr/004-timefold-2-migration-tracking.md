# ADR-004: Timefold 2.0 Migration Tracking

**Status:** Active — monitor, do not migrate before June 2026

**Date:** 2026-04-28

## Context

JRostering currently uses **Timefold Solver 1.31.0** (the most recent stable 1.x release).
Timefold 2.0 is in beta. It introduces API-breaking changes that require a coordinated
upgrade with Spring Boot 4 and Java 21+.

The current stack:
| Component | Version | Notes |
|-----------|---------|-------|
| Timefold Solver | 1.31.0 | stable |
| Spring Boot | 3.5.5 | requires Java 17+ |
| Vaadin | 24.7.0 | free maintenance ends June 2026 |
| Java | 25 | |

## Known breaking changes in Timefold 2.0 (as of April 2026)

| Area | Change |
|------|--------|
| Score API | `HardMediumSoftScore.of(hard, medium, soft)` → new factory method |
| `@PlanningPin` | Removed; replaced by `@PlanningListVariable` pin mechanism |
| `ConstraintVerifier` | Package moved from `ai.timefold.solver.test.*` to new module |
| Config XML | `solverConfig.xml` namespace changes |
| Kotlin DSL | First-class support; Java DSL unchanged for now |

Source: [Timefold 2.0 migration guide (beta)](https://timefold.ai/docs/timefold-solver/latest/migration-and-release-notes/migration-guide)

## Consequences of not upgrading

- Timefold 1.x receives security patches but no new features after Timefold 2.0 GA.
- Vaadin 24 free community maintenance ends June 2026 — the upgrade path to Vaadin 25
  requires Spring Boot 4, which requires Java 21+. This timeline is tighter.

## Migration pre-conditions

The upgrade from 1.x → 2.x can only proceed after:

1. **Spring Boot 4** is stable and `spring-boot-starter-*` artifacts are available.
   Spring Boot 4 targets Java 21 as baseline (not yet released as of this writing).
2. **Vaadin 25** is stable (follows Spring Boot 4).
3. **Timefold 2.0 GA** is released (currently beta).
4. **`@PlanningPin` replacement** is mapped to the new API.
   In JRostering, `ShiftAssignment.pinned` is the only usage — the constraint
   `RosterConstraintProvider.respectPinnedAssignments` reads it directly.
   This must be re-implemented using the Timefold 2.0 mechanism before migration.

## Action plan

| Date | Action |
|------|--------|
| Before 2026-06-01 | Evaluate Vaadin 25 + Spring Boot 4 beta compatibility |
| Monthly | Check Timefold 2.0 changelog for GA ETA |
| When Spring Boot 4 GA | Begin upgrade spike in a feature branch |
| After spike passes tests | Schedule full migration |

## Impact areas in this codebase

| File | Change required |
|------|-----------------|
| `pom.xml` | Bump `timefold.version`, `spring-boot.version`, `vaadin.version` |
| `solver/RosterConstraintProvider.java` | Review `@PlanningPin` usage; adapt to new API |
| `solver/RosterSolution.java` | Review `@PlanningSolution` annotations for any API changes |
| `resources/solverConfig.xml` | Update namespace / configuration keys |
| All `@WebMvcTest` controller tests | Spring Boot 4 may change test slice config |
| All Vaadin views | Review Vaadin 25 migration guide for breaking changes |
