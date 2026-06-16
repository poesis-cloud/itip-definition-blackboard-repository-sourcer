# PROJECT_BRIEF.md — ITIP Definition Blackboard Repository Sourcer

> Last updated: 2025-07 | Sprint 0 | Status: In Progress

## 1. Project Overview

`itip-definition-blackboard-repository-sourcer` is a **client-side sourcer** that turns a code repository into GSM contributions posted to the Definition Blackboard Manager REST service. It implements the client-side contract of the Definition Blackboard protocol: declare panels, run 18 KnowledgeSource implementations across 3 stages, and post confidence-bearing identifications as contributions. Deterministic tool outputs (SCIP index, CPG, SBOM) are held **in-memory only** — never persisted.

## 2. Concept / Product Description

The sourcer runs as a single-JVM client process. It hosts four packages:

1. **KS contract** — `KnowledgeSource` abstract class (getFqn, getSourceContributionSlots, getTargetContributionSlots, isContributableBlackboard, contributeToBlackboard).
2. **Services** — stateful/stateless services owning all in-memory context: `ScipService`, `CpgService`, `SbomService`, `LeidenAlgService`, `MappingService`, `InferenceService`, `DefinitionBlackboardService`.
3. **KSs** — 18 concrete KnowledgeSource subclasses, 3 stages x 6 GSM subjects (`*IdentificationKs`, `*ArchetypingKs`, `*StatementKs`).
4. **Blackboard** — REST resource hosted by `sie-definition-blackboard-manager`; 3 panels x 6 slots = 18 slots.

**Core invariant:** The blackboard hosts **only** confidence-bearing identifications (`confidence.score < 1.0`). Deterministic outputs are never persisted.

**Blackboard topology:**

| Panel | Stage | Slots |
|-------|-------|-------|
| `itip:Definition` | Candidate identification | `*Identity` x 6 subjects |
| `itip:Archetype` | Archetype match | `*Archetype` x 6 subjects |
| `itip:Statement` | Draft GSM Statement | `*Statement` x 6 subjects |

**Promotion DAG:** `Definition (*Identity) -> Archetype (*Archetype) -> Statement (*Statement)`

## 3. Tech Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 3.5.x (web, validation, actuator, oauth2-client)
- **Build:** Maven
- **Deployment:** Helm + Kubernetes (AKS), namespace `sie`
- **Testing:** JUnit 5, Mockito, JaCoCo (>=95% instruction coverage required)
- **External deps:** `sie-definition-blackboard-manager` REST API

## 4. Architecture

```
+------------------------------------------------------------------------+
|  itip-definition-blackboard-repository-sourcer  (single JVM)           |
|                                                                         |
|  +-------------+   +----------------------------------------------+   |
|  | KS contract |   | Services (in-memory, stateful or stateless)   |   |
|  | KnowledgeSrc|   | ScipService | CpgService | SbomService        |   |
|  +------+------+   | LeidenAlgService | MappingService             |   |
|         |          | InferenceService (LLM) | DefBlackboardService  |   |
|  +------v------+   +-----------------------------+----------------+   |
|  |   18 KSs    |<--------------- uses -----------+                     |
|  | IdentKs x6  |                                                       |
|  | ArchKs  x6  |-- contributeToBlackboard() --> REST POST             |
|  | StmtKs  x6  |                                                       |
|  +-------------+                                                       |
+------------------------------------------+-----------------------------+
                                           | HTTPS REST
+------------------------------------------v-----------------------------+
|  sie-definition-blackboard-manager                                      |
|  /blackboards/{bbId}/panels  /blackboards/{bbId}/contributions          |
|  Blackboard-seal lifecycle: open -> sealed -> byte-stable               |
+-------------------------------------------------------------------------+
```

## 5. Key Files Map

| Area | Path | Contents |
|------|------|----------|
| Design model | `def/components.puml` | CD: 4 packages (KS contract/Services/KSs/Blackboard) |
| Sourcing workflow | `def/sourcing-workflow.puml` | Sequence: 3 phases + Seal |
| Contribution schemas | `def/contributions/` | 18 slot schemas + shared envelopes |
| Framework catalogues | `def/frameworks/` | Sourced catalogues consumed by MappingService |
| Main source | `src/main/java/` | Spring Boot application |
| Tests | `src/test/java/` | Unit + integration tests (JaCoCo >=95%) |
| Helm chart | `ops/helm/` | Chart.yaml + environments (dev/preprod/prod) |
| Sprint docs | `docs/sprint-N/` | Plans, progress, done |

## 6. Team Roles

| Agent | Name | Role |
|-------|------|------|
| Producer | **Remy** | Sprint planning, GitHub Issues, coordination, merging PRs. Never writes code. |
| Dev team | **Nova / Sage / Milo** | Client REST contract, panel/contribution design, KS-as-HTTP-poster implementation |
| QA | **Ivy** | JaCoCo >=95%, Blackboard isolation invariant tests, contribution validation tests |

## 7. Sprint Status

| Sprint | Name | Status | Scope |
|--------|------|--------|-------|
| 0 | Bootstrap & Design | Done | PlantUML design, contribution schemas, repo structure |
| 1 | Core KS Contract & Services | Not started | KnowledgeSource abstract class, Services skeleton |
| 2 | 18 KS Implementations | Not started | IdentificationKs x6, ArchetypingKs x6, StatementKs x6 |
| 3 | Blackboard Client & Integration | Not started | DefinitionBlackboardService, panel declaration, contribution POST |

## 8. Current State

**What works:**
- Design-time artifacts complete: `components.puml`, `sourcing-workflow.puml`, 18 contribution schemas, shared envelopes
- Maven project scaffolded (Spring Boot 3.5, Java 21)
- Helm chart structure in place

**What does not work yet:**
- No Java implementation (KS contract, Services, KSs not yet written)
- No integration with `sie-definition-blackboard-manager`

**What is next:**
- Sprint 1: Implement `KnowledgeSource` abstract contract + all 7 Services skeleton

## 9. Security Rules

1. Secrets (API keys, OAuth credentials) in environment variables only — never in code or git.
2. OAuth2 client credentials flow for `sie-definition-blackboard-manager` REST calls.
3. `.env.dev` for local dev secrets — never committed.
4. Never post `confidence.score >= 1.0` to the blackboard (core invariant violation).
5. Never attempt to post contributions to a sealed blackboard (Blackboard isolation invariant).

## 10. How to Run Locally

```bash
cp .env .env.dev    # fill in local secrets
make dev-up         # Helm-based dev deployment
# Or: mvn spring-boot:run
```

## 11. How to Deploy

```bash
make package-helm
make dev-up
make prod-deploy ENV=prod
```

See `ops/README.md` for full Helm install commands and secrets policy.

## 12. Cross-Chat Handoff Protocol

Every sprint chat must do these before finishing:

1. Write `docs/sprint-N/done.md` — what was built, what is not done, what needs manual setup, files changed/created.
2. Update PROJECT_BRIEF.md: Section 7 (mark sprint done) + Section 8 (rewrite current state).
3. Commit all changes: `sprint-N: <summary>` with Co-authored-by trailer.

**Cold-start recovery prompt:**
```
Read PROJECT_BRIEF.md and docs/sprint-N/progress.md.
Continue from where it left off.
```

## 13. Bug & Fix Tracking

Bugs tracked as GitHub Issues on `poesis-cloud/itip-definition-blackboard-repository-sourcer`.

- **QA:** File bugs with labels (`bug`, `severity:blocker/major/minor`). Include: component, steps to reproduce, expected vs actual.
- **Dev:** Check issues before starting. Use `fix: description (Fixes #NN)` in commits.
- **Feature ideas:** `docs/ideas-backlog.md`.

## 14. Multi-Repo Setup

Each team works in their own separate clone:

```bash
git clone git@github.com:poesis-cloud/itip-definition-blackboard-repository-sourcer.git sourcer-dev
git clone git@github.com:poesis-cloud/itip-definition-blackboard-repository-sourcer.git sourcer-qa
```

**Branch strategy:** `feature/sprint-N` -> PR -> merge to main. Never squash. Never rebase feature branches.
