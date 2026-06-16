# ITIP Definition Blackboard Repository Sourcer (`itip/itip-definition-blackboard-repository-sourcer`)

A **client sourcer** that turns a code repository (an IT system's
source tree) into GSM Ascriptions registered into the Definition
Manager. Runs as a single-JVM client process and speaks REST to the
[Definition Blackboard Manager](../../sie/sie-definition-blackboard-manager/).

This document is **design-time** only.

## Core invariant (radical re-design)

> **The blackboard hosts ONLY confidence-bearing identifications**
> (`confidence.score < 1.0` by construction). Deterministic tool
> outputs (SCIP index, CPG, SBOM, framework inventory, IaC inventory,
> assembled code graph) flow **in-memory** through `SourcerContext`
> between co-located producer and consumer KSs in a single JVM and
> are **NEVER persisted** — not on the blackboard, not on disk, not
> in any cache. Re-derivable on demand from each contribution's
> `provenance.indexedRevision` + `provenance.tools[]`.

Two consequences:

- "Identification" replaces the older "fact" terminology. A *fact*
  has 100% confidence by definition; a *blackboard contribution* has
  `confidence.score < 1.0` by construction. The two are disjoint.
- Each spine KS embeds its **own non-deterministic selection** over
  the deterministic in-memory graphs supplied by `SourcerContext`
  (e.g. Leiden community detection inside `StructureIdentificationKs`;
  taint / dataflow path selection inside
  `Effector/Receptor/InteractionIdentificationKs`; archetype-fit
  scoring inside `*ArchetypingKs`; LLM completion inside
  `*StatementKs`).

## Architecture in one diagram

```
┌──────────────────────────────────────────────┐ REST  ┌─────────────────────────────┐
│ ITIP Definition Blackboard Repository Sourcer│ ────► │ Definition Blackboard       │
│                  (single JVM)                │       │ Manager                     │
│                                              │ ◄──── │                             │
│  RepoBootstrap → RunDriver                   │       │ • Blackboard resource       │
│                                              │       │ • Panel / Slot registry     │
│  ┌────────────────────────────────────────┐  │       │ • Contribution registry     │
│  │ Producer KSs                           │  │       │ • Blackboard-seal FSM       │
│  │   RepoScanKS   ScipJavaKS   JoernKS    │  │       │ • Audit ledger              │
│  │   SyftKS       SpringScanKS  JpaScanKS │  │       │                             │
│  │   TerraformParseKS  CodeGraphAssemblyKS│  │       │ (NO Run/Phase, NO resolver, │
│  └──────────────────┬─────────────────────┘  │       │  NO reduction, NO           │
│                     ▼                        │       │  cardinality enforcement)   │
│  ┌────────────────────────────────────────┐  │       └─────────────────────────────┘
│  │ SourcerContext (in-memory, never       │  │       ┌─────────────────────────────┐
│  │ persisted): RepoSnapshot, ScipIndex,   │  │ ────► │ Definition Manager          │
│  │ LoadedCpg, Sbom, FrameworkInventory,   │  │ reg.  │ Ascription registry         │
│  │ IacInventory, CodeGraph                │  │       └─────────────────────────────┘
│  └──────────────────┬─────────────────────┘  │
│                     ▼                        │
│  ┌────────────────────────────────────────┐  │
│  │ Consumer KSs (each embeds its own      │  │
│  │ non-deterministic selection):          │  │
│  │   *IdentificationKs  → Definition slot │  │
│  │   *ArchetypingKs     → Archetype  slot │  │
│  │   *StatementKs       → Statement  slot │  │
│  └──────────────────┬─────────────────────┘  │
│                     ▼                        │
│   ClientReducer  →  PostReductionPipeline ───┘
│   (post-seal)       (validate + register)
│
│   External tools (sandboxed execve):
│     scip-java, joern-parse, syft, terraform parse, ...
└──────────────────────────────────────────────┘
```

## Repository layout

```
itip/itip-definition-blackboard-repository-sourcer/
  README.md                                   (this file)
  def/
    components.puml                           (CD - in-memory SourcerContext +
                                                 producer/consumer KSs +
                                                 3-panel x 18-slot blackboard)
    sourcing-workflow.puml                    (Sequence - 4 phases:
                                                 0/ Bb creation + topology declaration,
                                                 1/ in-memory SourcerContext build,
                                                 2/ Identification (Definition panel),
                                                 3/ Archetyping (Archetype panel),
                                                 4/ Statement shaping (Statement panel),
                                                 then seal + reduce + register)
    contributions/                            (per-slot contribution schemas; one JSON
                                                 Schema file per slot in components.puml,
                                                 plus shared envelopes under _common/)
      _common/confidence-envelope.schema.json
      _common/provenance-envelope.schema.json
      Definition/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Identity.schema.json
      Archetype/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Archetype.schema.json
      Statement/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Statement.schema.json
```

## Blackboard topology (3 panels × 6 GSM subjects × 3 stages = 18 slots)

The blackboard carries exactly **3 panels**, each with **6 slots**,
one per GSM subject:

| Panel              | Stage semantics                                                 | Slots (one per subject)                                                                                                                |
| ------------------ | --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `itip:Definition` | Candidate identification: "this thing is a candidate `<Subject>`" | `StructureIdentity`, `MechanismIdentity`, `DataArchetypeIdentity`, `EffectorIdentity`, `ReceptorIdentity`, `InteractionIdentity`         |
| `itip:Archetype`  | Archetype match: "this candidate matches a known `<Subject>` archetype" | `StructureArchetype`, `MechanismArchetype`, `DataArchetypeArchetype`, `EffectorArchetype`, `ReceptorArchetype`, `InteractionArchetype` |
| `itip:Statement`  | Draft GSM Statement: "here is the materialized statement"        | `StructureStatement`, `MechanismStatement`, `DataArchetypeStatement`, `EffectorStatement`, `ReceptorStatement`, `InteractionStatement` |

**Subjects** (6, mapped to GSM primitives):

| Subject          | Maps to GSM primitive | Notes                                                                                |
| ---------------- | --------------------- | ------------------------------------------------------------------------------------ |
| `Structure`      | `Structure`           | Structural unit (package, module, class cluster).                                    |
| `Mechanism`      | `Mechanism`           | Behavioural mechanism (algorithm, service, controller).                              |
| `DataArchetype`  | `Archetype`           | Data shape (DTO, entity, schema record). Maps to GSM `Archetype` in the data-shape role; the slot name `DataArchetypeArchetype` (Archetype-stage of the DataArchetype subject) is intentional and reflects the lifecycle pattern uniformly. |
| `Effector`       | `Effector`            | Outbound effector (HTTP client, message producer, DB writer, scheduled task).        |
| `Receptor`       | `Receptor`            | Inbound receptor (HTTP endpoint, message consumer, DB reader, event listener).       |
| `Interaction`    | `Interaction`         | Call / dataflow / message / dependency relation between two subjects.                |

**DAG** (read-up promotion only):

```
Definition (*Identity)  →  Archetype (*Archetype)  →  Statement (*Statement)
```

## Contribution shape (every slot)

Every contribution `allOf`-merges the two shared envelopes:

- **`_common/confidence-envelope`** — `score` (0 ≤ score < 1, exclusive
  upper bound enforces the core invariant), `method` (scoring scheme
  identifier), `factors[]` (explainability fragments).
- **`_common/provenance-envelope`** — `ks` (`<fqn>@<version>`),
  `indexedRevision` (typically a git sha), `tools[]` (deterministic
  tools used to build SourcerContext, with versions), `ruleSnapshotSha256`
  (sha of the rule snapshot driving non-deterministic selection),
  `seeds[]` (RNG seeds for reproducibility).

Per-slot properties:

- `subject` — canonical reference to the candidate subject. For code-
  anchored subjects this is typically a SCIP symbol moniker. For
  `Interaction*` slots, additionally `sourceSubject`, `targetSubject`,
  `interactionKind`.
- `evidence[]` — optional explainability fragments (SCIP occurrence
  ranges, framework annotation hits, dataflow path summaries, IaC
  resource refs, ...). Pure references; raw deterministic graphs are
  NOT persisted here.

## SourcerContext (in-memory only)

| SourcerContext slice  | Built by                                | Contents                                                                                          |
| --------------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `RepoSnapshot`        | `RepoScanKS`                            | Working-copy file tree, file classifications, sizes, language tags.                                |
| `ScipIndex`           | `ScipJavaKS` (via `scip-java` execve)   | Parsed `.scip` protobuf in JVM heap (documents, symbols, occurrences, external symbols).           |
| `LoadedCpg`           | `JoernKS` (via `joern-parse` execve)    | Loaded Code Property Graph (call edges, dataflow candidates, type info).                           |
| `Sbom`                | `SyftKS` (via `syft` execve)            | CycloneDX/SPDX SBOM components.                                                                    |
| `FrameworkInventory`  | `SpringScanKS`, `JpaScanKS`, ...        | Framework artefacts (Spring beans, JPA entities, controllers, listeners) keyed by SCIP symbol.     |
| `IacInventory`        | `TerraformParseKS`, ...                 | Parsed IaC resources (Terraform / Helm / Kustomize) keyed by stable IaC ids.                       |
| `CodeGraph`           | `CodeGraphAssemblyKS`                   | Joined graph over `ScipIndex` + `LoadedCpg` + `FrameworkInventory` + `IacInventory`, keyed on SCIP. |

**Determinism contract.** For a fixed `indexedRevision` + fixed
`tools[]` versions, every Producer KS produces a byte-identical slice.
This is the basis for not persisting any of it: any consumer can
re-derive what it needs by re-running the producer chain.

## Knowledge Sources

### Producer KSs (build SourcerContext; do NOT post to blackboard)

| KS                    | Builds SourcerContext slice  | External tools                       |
| --------------------- | ---------------------------- | ------------------------------------ |
| `RepoScanKS`          | `RepoSnapshot`               | `tokei`, `git`                       |
| `ScipJavaKS`          | `ScipIndex`                  | `scip-java`                          |
| `JoernKS`             | `LoadedCpg`                  | `joern-parse`, `joern-export`        |
| `SyftKS`              | `Sbom`                       | `syft`                               |
| `SpringScanKS`        | `FrameworkInventory`         | — (in-process, reads `ScipIndex`)    |
| `JpaScanKS`           | `FrameworkInventory`         | — (in-process, reads `ScipIndex`)    |
| `TerraformParseKS`    | `IacInventory`               | terraform parser (read-only)         |
| `CodeGraphAssemblyKS` | `CodeGraph`                  | — (joins other slices in JVM heap)   |

> **v1 supported languages.** Only Java/Scala/Kotlin (via `scip-java`)
> at v1. Future ecosystem modules add `ScipTypescriptKS`,
> `ScipPythonKS`, `ScipGoKS`, `RustAnalyzerKS`, `ScipDotnetKS`, ...
> Files in unsupported languages still appear in `RepoSnapshot` and
> in `Sbom`, but produce no `ScipIndex` entries and therefore no
> code-anchored identifications.

### Consumer KSs (read SourcerContext; post one slot each)

For each of the 6 subjects S in
`{Structure, Mechanism, DataArchetype, Effector, Receptor, Interaction}`:

| KS                  | Writes blackboard slot                  | Embedded non-deterministic selection                                                  |
| ------------------- | --------------------------------------- | ------------------------------------------------------------------------------------- |
| `S`​`IdentificationKs` | `itip:Definition.S`​`Identity`          | Subject-specific (Leiden for Structure; taint-reach for Effector/Receptor/Interaction; structural heuristics for Mechanism/DataArchetype). |
| `S`​`ArchetypingKs`    | `itip:Archetype.S`​`Archetype`          | Archetype-fit scoring against the DefMan archetype catalogue.                         |
| `S`​`StatementKs`      | `itip:Statement.S`​`Statement`          | Optional LLM-driven shaping via `InferenceService`.                                   |

= 18 Consumer KSs total, one per blackboard slot.

## Reduction (controller-side, post-seal)

The Definition Blackboard Manager **does not reduce** contributions.
Reduction happens inside the ITIP sourcer after seal:

1. `RunDriver` calls `GET /blackboards/{bbId}/contributions` (paginated)
   to read the byte-stable sealed stream.
2. The **client reducer** applies per-slot policy (typically
   `highestConfidence` over the `*Statement` slots, keyed by `subject`).
3. Each reducer invocation is logged with `(reducerId, version,
   params, inputContributionURIs[], outputValue)` so the entire
   reduction is replayable as a deterministic process over the
   sealed stream.
4. The post-reduction pipeline validates the reduced statements
   against archetype schemas and registers the resulting Ascriptions
   into the Definition Manager.

The substrate enforces no per-slot cardinality; all cardinality
decisions live client-side.

## Reproducibility contract

Any blackboard contribution can be regenerated by:

1. Re-running the Producer KS chain at
   `provenance.indexedRevision` with `provenance.tools[]` versions
   → reconstructs `SourcerContext` byte-for-byte.
2. Re-running the named Consumer KS at
   `provenance.ruleSnapshotSha256` with `provenance.seeds[]` →
   reconstructs the embedded non-deterministic selection.

This is what justifies persisting only confidence-bearing
identifications on the blackboard: everything deterministic is
re-derivable from the provenance envelope.

## Coherence with GSM

Mapping rules embedded in each `*IdentificationKs` (subject + identity
key derivation):

| Source-side cue                                              | GSM subject (slot family) |
| ------------------------------------------------------------ | -------------------------- |
| HTTP service / Kafka producer-consumer / RDBMS façade        | `Interaction*`             |
| Method / module that implements behaviour                    | `Mechanism*`               |
| Sensor / telemetry / config-reader                           | `Receptor*`                |
| Side-effecting actuator (DB writer, mailer, IaC apply)       | `Effector*`                |
| Type / data model / schema                                   | `DataArchetype*` or `Structure*` |
| Package / module / class cluster                             | `Structure*`               |

`Norm` and `Directive` GSM primitives are **out of scope** for the
repository sourcer (they are sourced by other ITIP framework sourcers,
not by code-repo analysis).

The authoritative skill for the REST architecture is
[`definition-blackboard-manager`](../../.github/skills/definition-blackboard-manager/SKILL.md)
(covers both the service and the client sourcer authoring contract).

## See also

- Blackboard service: [sie/sie-definition-blackboard-manager/](../../sie/sie-definition-blackboard-manager/)
- Service component diagram: [component.puml](../../sie/sie-definition-blackboard-manager/def/blackboard/component.puml)
- Sourcing workflow (in-memory phase + REST phases): [sourcing-workflow.puml](def/sourcing-workflow.puml)
