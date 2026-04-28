# ADR 001 — Soft-Delete Strategy

**Status**: Accepted  
**Date**: 2026-04-28

## Context

JRostering manages Staff and Sites that may be removed from active rostering but must not be permanently deleted. Historical assignments, leave records, and solver job data reference these entities. Hard-deleting them would require cascading deletes across many tables or leave orphaned FK references.

## Decision

Staff and Sites are never hard-deleted. Instead they carry an `active` boolean column:

- `active = true` — visible in all queries, eligible for scheduling
- `active = false` — excluded from all repository queries that feed the UI, REST API, and solver

The `deactivate()` service method sets `active = false`. No `delete()` method is exposed.

## Consequences

**Good**: Historical data is preserved. Solver job reports remain coherent. Rollback (re-activation) is trivial.

**Bad**: Queries filtering active records must be maintained. A new repository method that forgets the `active = true` filter will silently return deactivated entities. This is mitigated by convention: all list-query methods are named `findByOrganisationAndActiveTrue(...)` to make the filter explicit.

**Scope**: Currently applied to `Staff` and `Site`. Domain objects that are children of a soft-deleted entity (e.g. `StaffQualification`, `ShiftType`) are preserved in the database but become unreachable once their parent is deactivated — this is acceptable since the parent's children are never queried independently without first loading the parent.
