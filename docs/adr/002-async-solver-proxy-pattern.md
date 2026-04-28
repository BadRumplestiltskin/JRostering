# ADR 002 — Three-Bean Async Solver Pattern

**Status**: Accepted  
**Date**: 2026-04-28

## Context

The Timefold solver must run on a background thread so that `POST /api/solver/{id}/submit` returns immediately while the solve runs for minutes or hours. Spring's `@Async` annotation achieves this via proxy interception. However, a Spring bean cannot intercept its own `this` calls — calling an `@Async` method from within the same bean executes it synchronously on the calling thread.

A similar constraint applies to `@Transactional`: self-calls bypass the proxy and run without a transaction.

## Decision

The solver lifecycle is split across three Spring beans, each crossing a proxy boundary:

```
SolverService          (controls submission and cancellation, @Transactional)
  └─ calls ──▶ SolverExecutor.executeSolveAsync()    (@Async entry point)
                  └─ calls ──▶ SolverTransactionHelper  (@Transactional for DB writes)
```

- `SolverService` validates the request, persists the `SolverJob`, and transitions the `RosterPeriod` to `SOLVING` within a single transaction. The commit happens before the async call, ensuring the background thread sees the persisted row.
- `SolverExecutor` holds the `@Async` method, manages the `activeSolvers` registry, and delegates all database writes back to `SolverTransactionHelper` (each write gets its own transaction).
- `SolverTransactionHelper` owns all transaction boundaries for the background thread.

## Consequences

**Good**: `@Async` and `@Transactional` both work correctly. The design is explicit and auditable.

**Bad**: Three separate beans for one logical operation is verbose. Future developers unfamiliar with the Spring proxy constraint may attempt to consolidate them and reintroduce the bug. This ADR exists to explain why the split is mandatory.
