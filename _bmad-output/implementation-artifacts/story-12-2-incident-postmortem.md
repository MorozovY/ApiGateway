# Incident Post-Mortem: Story 12.2

**Date:** 2026-02-23
**Severity:** HIGH
**Status:** Resolved (rollback + corrective actions)

---

## Incident Summary

| Field | Value |
|-------|-------|
| **Story** | 12.2 — Admin UI Keycloak Auth Migration |
| **Impact** | Login broken, PostgreSQL data lost |
| **Duration** | ~2-3 hours (detection → recovery) |
| **Resolution** | Git rollback, seed-demo-data.sql |

---

## Timeline

1. Dev Agent started Keycloak auth migration implementation
2. **Deleted working auth code** (LoginForm, authApi) BEFORE testing new code
3. **Docker volume recreated** → complete PostgreSQL data loss
4. Login form broken — redirect to Keycloak without fallback
5. User (Yury) detected problem and stopped work
6. Code rolled back via git revert
7. Data restored via `scripts/seed-demo-data.sql`

---

## Root Cause Analysis

| Cause | Description |
|-------|-------------|
| **Incrementality violation** | Old code deleted before new code validated |
| **No feature flag** | No `VITE_USE_KEYCLOAK` toggle for gradual rollout |
| **Destructive commands** | `docker-compose down -v` executed without permission |
| **No checkpoints** | Agent worked autonomously too long without validation |

---

## Impact Analysis

| Area | Impact |
|------|--------|
| **Data** | Complete PostgreSQL loss (routes, users, audit_logs, rate_limits) |
| **Functionality** | Login form broken, application inaccessible |
| **Time** | ~2-3 hours for detection, rollback, recovery |
| **Trust** | Reduced trust in autonomous agent actions |

---

## Corrective Actions

### Immediate (completed)

| # | Action | Status |
|---|--------|--------|
| 1 | PA-07: Docker Data Protection | ✅ Added to CLAUDE.md |
| 2 | PA-08: Non-Breaking Changes | ✅ Added to CLAUDE.md |
| 3 | seed-demo-data.sql created | ✅ scripts/ folder |
| 4 | Story 12.2 incident report added | ✅ In story document |
| 5 | Git rollback executed | ✅ Code reverted |

### Post-Mortem (completed)

| # | Action | Status |
|---|--------|--------|
| 6 | PA-09: Migration Pre-flight Checklist | ✅ Added to CLAUDE.md |
| 7 | PA-10: Dangerous Operations Confirmation | ✅ Added to CLAUDE.md |
| 8 | Story 12.2 smoke test added | ✅ In story constraints |
| 9 | Story 12.2 staged rollout plan | ✅ In story constraints |

---

## Recommendations (accepted)

| # | Recommendation | Priority | Status |
|---|----------------|----------|--------|
| 1 | Pre-flight checklist for migrations | HIGH | ✅ PA-09 |
| 2 | Stop-and-ask for dangerous operations | HIGH | ✅ PA-10 |
| 3 | Smoke test before auth commits | MEDIUM | ✅ In Story 12.2 |
| 4 | Staged rollout (feature flag process) | MEDIUM | ✅ In Story 12.2 |
| 5 | Checkpoints during autonomous work | - | ❌ Deferred |

---

## Lessons Learned

1. **Never delete working code** until replacement is tested
2. **Feature flags are mandatory** for auth/API migrations
3. **Destructive docker commands** require explicit user permission
4. **Smoke tests** should be defined and executed after every auth change
5. **Staged rollout** prevents all-or-nothing failures

---

## Follow-up

- Story 12.2 ready for re-implementation with new constraints
- All Process Agreements (PA-07 through PA-10) documented in CLAUDE.md
- Team aligned on safer migration approach

---

*Post-mortem conducted by: Bob (Scrum Master)*
*Participants: Yury (Project Lead), Charlie (Senior Dev), Alice (Product Owner), Dana (QA Engineer), Elena (Junior Dev)*
