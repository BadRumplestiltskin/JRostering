# ADR 003 — Cross-Site Blocking Is a Solver-Internal Concept

**Status**: Accepted  
**Date**: 2026-04-28

## Context

Staff may be assigned at multiple sites. When solving a roster for Site A, the solver must not assign a staff member to a shift that overlaps with a shift they are already committed to at Site B (where they are PUBLISHED or SOLVED).

An early design considered exposing `CrossSiteBlockingPeriod` as a JPA entity with its own management UI. 

## Decision

`CrossSiteBlockingPeriod` is a Java `record` (not a JPA entity). It has no database table and is never managed through the UI or REST API. It is derived automatically at solve time by `RosterSolutionMapper.buildCrossSiteBlockingPeriods()` from `ShiftAssignment` rows belonging to other sites where the roster is PUBLISHED or SOLVED.

```java
record CrossSiteBlockingPeriod(Staff staff, OffsetDateTime start, OffsetDateTime end) {}
```

The solver receives these as problem facts and the `CROSS_SITE_DOUBLE_BOOKING` constraint penalises any assignment that overlaps a blocking period.

## Consequences

**Good**: No additional table, no UI, no management overhead. The data is always consistent with the source of truth (published/solved assignments at other sites). Changes to other sites' rosters are automatically reflected at the next solve.

**Bad**: The blocking periods are invisible to the roster manager through the UI. If a staff member is blocked for an unexpected reason, the manager must inspect other sites' published rosters to understand why. A future improvement could surface these blocks as read-only data in the staff detail view.
