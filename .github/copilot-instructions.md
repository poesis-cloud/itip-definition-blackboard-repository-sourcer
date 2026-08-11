# Copilot Instructions — ITIP Definition Blackboard Repository Sourcer

This repo is the **client-side sourcer** that reads ITIP governance framework data and posts it as Contributions to the Definition Blackboard Manager REST service. All agents must read `PROJECT_BRIEF.md` before starting any work.

---

## Team Roster

### Remy — Producer
Sprint planning, GitHub Issues, coordination. **Never writes application code.**

**Skills to invoke:** `product-manager`, `create-pr`, `update-pr`, `sync`, `commit`, `chronicle`

**Responsibilities:**
- Create and maintain sprint plans in `docs/sprint-N/plan.md`
- File GitHub Issues for bugs and features
- Coordinate handoffs between Dev and QA
- Merge PRs after QA sign-off
- Update `PROJECT_BRIEF.md` Sections 7 + 8 each sprint

---

### Nova / Sage / Milo — Dev Team
Client REST contract, panel/contribution design, KS-as-HTTP-poster implementation.

**Skills to invoke:** `definition-blackboard-manager`, `gsm-knowledge`, `itip-framework-sourcing`, `agent-customization`, `update-skills`, `commit`, `create-pr`, `context-map`, `refactor-plan`

**Responsibilities:**
- Implement `KnowledgeSource` abstract contract and all 18 KS subclasses
- Implement all 7 Services (ScipService, CpgService, SbomService, LeidenAlgService, MappingService, InferenceService, DefinitionBlackboardService)
- Implement panel declaration and contribution posting via `DefinitionBlackboardService`
- Write unit and integration tests (JaCoCo >=95% instruction coverage)
- Use `context-map` skill before any multi-file refactor
- Use `refactor-plan` skill before executing a multi-file refactor

---

### Ivy — QA Engineer
Test coverage, Blackboard invariant tests, contribution validation tests.

**Skills to invoke:** `code-review`, `commit`

**Responsibilities:**
- Verify JaCoCo instruction coverage >=95% at module level
- Write and validate Blackboard isolation invariant tests (sealed blackboard immutability)
- Write and validate contribution validation tests (confidence score, provenance envelope)
- File bugs as GitHub Issues with `bug` label and severity
- Write QA sign-off docs at `docs/qa/sprint-N-signoff.md`

---

## Domain Rules (mandatory for all agents)

### Blackboard Client Contract
1. **Always declare panels before posting contributions.** Call `DefinitionBlackboardService.declarePanel()` for each of the 3 panels (`itip:Definition`, `itip:Archetype`, `itip:Statement`) before any `contributeToBlackboard()` call.
2. **Promotion DAG is read-up only:** `Definition -> Archetype -> Statement`. A KS at stage N may only read slots from stages <= N.
3. **Core invariant:** `confidence.score` must be `< 1.0` (exclusive). A score of 1.0 is a deterministic fact — never a blackboard contribution.

### Blackboard Isolation Invariant
- **Never attempt to post to a sealed blackboard.** Always check `isContributableBlackboard()` before calling `contributeToBlackboard()`.
- Sealed blackboards are **immutable**. No patches, no retries against a sealed board.
- Byte-stable contributions must be confirmed before seal is requested.

### Contribution Validation
- Every contribution must include the full `confidence-envelope` (`score`, `method`, `factors[]`) and `provenance-envelope` (`ks`, `indexedRevision`, `tools[]`, `ruleSnapshotSha256`, `seeds[]`).
- `subject` field must be set on every contribution. For Interaction slots, also set `sourceSubject`, `targetSubject`, `interactionKind`.
- `evidence[]` is optional but recommended for explainability.

### Repository-Service Exclusivity Rule
Each Repository must be consumed by exactly one Service. Cross-type entity access must go through the owning Service, not directly through the Repository. Use `@Lazy` on constructor parameters to break circular dependencies when needed.

### Engineering Rules
- **Root-cause-first:** Fix root causes before reaching for `@SuppressWarnings` or any suppression annotation. Suppression is last resort; scope it to the narrowest possible element with a one-line explanation comment.
- **JaCoCo >=95%:** Every `src/main` class must have corresponding `src/test` coverage. Run `mvn test jacoco:report` and check `target/site/jacoco/index.html`.
- **Never pipe mvn:** Run `mvn` commands directly — no `| cat`, `| tee`, `| grep`. Piping disrupts WSL stability and hides real-time test progress.
- **Git history preservation:** Always use `git mv` / `git rm` for tracked file operations. Never use plain `mv` or `rm` on tracked files.
- **Archive folders are read-only:** `archives/` and `archive/` folders contain historical content only. Never edit, update, or delete files inside them.
- **No secrets in code or git.** Use environment variables; local secrets in `.env.dev` (never committed).

### Commit Convention
```
<type>: <description> (Fixes #NN)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

Types: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.

### Design-time artifacts
- `def/components.puml` and `def/sourcing-workflow.puml` are the authoritative design models.
- If README and `.puml` disagree, the `.puml` wins.
- Validate PlantUML changes: `java -jar <plantuml.jar> -checkonly <file>`

---

## SE Plugin Agents (global — invoke by name)

These agents are installed globally via the `software-engineering-team` plugin. Invoke them by name in any chat.

| When | Invoke |
|---|---|
| Security review before any merge | `SE: Security` |
| Architecture decision or structurant PR | `SE: Architect` |
| CI/CD pipeline, Helm, deployment debug | `SE: DevOps/CI` |
| Writing/updating API docs, ADRs, README | `SE: Technical Writer` |
| Authoring GitHub Issues or backlog items | `SE: Product Manager Advisor` |
