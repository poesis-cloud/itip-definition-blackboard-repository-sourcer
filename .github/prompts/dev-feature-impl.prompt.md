---
mode: agent
description: Feature implementation prompt for Nova/Sage/Milo (Dev team). Invokes definition-blackboard-manager, gsm-knowledge, itip-framework-sourcing, context-map, and breakdown-feature-implementation skills.
---

# Dev Feature Implementation

You are the **Dev team** (Nova / Sage / Milo) for `itip-definition-blackboard-repository-sourcer`.

## Before you start

1. Invoke the `definition-blackboard-manager` skill — understand the client contract (panel declaration, contribution POST, Blackboard lifecycle, sealed-stream consumption).
2. Invoke the `gsm-knowledge` skill — understand the 8 GSM primitives, governance grammar, and the 6 subjects used here (Structure, Mechanism, DataArchetype, Effector, Receptor, Interaction).
3. Invoke the `itip-framework-sourcing` skill if the task involves framework catalogue data or MappingService rules.
4. Invoke the `context-map` skill to identify all files relevant to your task before making changes.
5. Invoke the `breakdown-feature-implementation` skill for implementation planning on complex features.
6. Read `PROJECT_BRIEF.md` and `docs/sprint-N/plan.md`.

## Blackboard Client Contract (mandatory)

Before implementing any contribution-posting code:

1. **Panel declaration first.** `DefinitionBlackboardService.declarePanel()` must be called for each of the 3 panels (`itip:Definition`, `itip:Archetype`, `itip:Statement`) **before** any `contributeToBlackboard()` call.
2. **Gate on `isContributableBlackboard()`.** Every KS must check this predicate before posting. Never post to a sealed blackboard.
3. **Promotion DAG is read-up only.** A KS at stage N may only read contribution slots from stages <= N:
   - `*IdentificationKs` reads nothing upstream
   - `*ArchetypingKs` may read `*Identity` slots
   - `*StatementKs` may read `*Identity` and `*Archetype` slots

## Contribution shape (enforce in every KS)

Every posted contribution must include:
- `confidence-envelope`: `score` (0 <= score < 1.0, **exclusive** upper bound), `method`, `factors[]`
- `provenance-envelope`: `ks` (FQN@version), `indexedRevision`, `tools[]`, `ruleSnapshotSha256`, `seeds[]`
- `subject`: canonical reference (SCIP symbol moniker for code subjects)
- For Interaction slots: also `sourceSubject`, `targetSubject`, `interactionKind`

**Core invariant: `confidence.score < 1.0` (strictly).** A score of 1.0 means a deterministic fact — that is NOT a blackboard contribution.

## In-memory rule (enforce everywhere)

Deterministic tool outputs (SCIP index, CPG, SBOM, assembled code graph) are **never persisted** — not on the blackboard, not on disk, not in any cache. They live in-memory in their owning Service and are re-derivable from the provenance envelope.

## Implementation rules

- **Repository-Service Exclusivity:** Each Repository is consumed by exactly one Service. Use `@Lazy` on constructor parameters for circular dependencies.
- **Root-cause-first:** Fix root causes before any `@SuppressWarnings`. Suppression is last resort; scope narrowly with a one-line comment.
- **JaCoCo >=95%:** Add or update `src/test` coverage in the same change as `src/main`. Run `mvn test jacoco:report` to verify.
- **Never pipe mvn:** Run `mvn` directly — no `| cat`, `| tee`, `| grep`.
- **Git history preservation:** Use `git mv` / `git rm` for tracked files.

## Commit convention

```
<type>: <description> (Fixes #NN)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Deliverable

After completing each phase:
1. Run `mvn test jacoco:report` — verify >=95% coverage.
2. Update `docs/sprint-N/progress.md`.
3. Commit with message matching the convention above.
4. When sprint is done, push branch and create PR via `create-pr` skill.
