# ITIP Definition Blackboard Repository Sourcer (`itip/itip-definition-blackboard-repository-sourcer`)

A **client sourcer** that turns a code repository (an IT system's
source tree) into GSM Ascriptions registered into the Definition
Manager. Runs as a separate process and speaks REST to the
[Definition Blackboard Manager](../../sie/sie-definition-blackboard-manager/).

This document is **design-time** only.

> **Substrate-simplification note (current).** The Definition Blackboard
> Manager substrate has been simplified and no longer carries the
> following Contribution-level fields: `identityKey`, `idempotencyKey`,
> `contributorId`, `evidence[]` / `EvidenceRef`, `rationale`,
> `confidence`, `cacheKey`, `acceptance` / `ContributionAcceptance`. It
> also no longer enforces `slot.atMostOne` cardinality, no longer
> declares `panel.dependencyPanels`, no longer requires the
> `Idempotency-Key` header, and renames a few attributes
> (`derivedFrom -> derivedContribution`, `postedAt -> timestamp`,
> `value -> post`, `BlackboardState -> BlackboardStatus`,
> `createdAt -> timestamp`, `sealedAt -> sealTimestamp`). Client-side
> conventions in this folder may still mention richer fields; treat
> them as anticipated content of `Contribution.post` (i.e. inside the
> per-slot JSON Schema), not as substrate features. The substrate will
> be enriched only when concrete ITIP needs surface, at which point
> these client-side conventions become the spec.

> Folder name `itip-definition-blackboard-repository-sourcer` is the canonical
> repo name. The legacy term "Pack" is **historical** — there is no
> Pack manifest, no engine-side plug-in loading, no Java SPI. The
> sourcer is a plain client process that holds its own KS catalogue
> and posts contributions over HTTP.

## Architecture in one diagram

The sourcer is a client process; the blackboard service is a separate
process; communication is REST.

```
┌─────────────────────────────────┐    REST     ┌──────────────────────────────┐
│ ITIP Definition Blackboard Repository Sourcer         │ ──────────► │ Definition Blackboard        │
│                                 │             │ Manager                      │
│ • RunDriver (client Run loop)   │ ◄────────── │                              │
│ • RepoBootstrap (clone)         │             │ • Blackboard resource        │
│ • Knowledge Sources (7 panels   │             │ • Panel / Slot registry      │
│   on a single Blackboard) ──┐   │             │ • Contribution registry      │
│ • External tools sandbox    │   │             │ • Blackboard-seal FSM        │
│ • Client reducer            │   │             │ • Audit ledger               │
│ • Post-reduction pipeline   │   │             │                              │
│         ┌───────────────────┘   │             │ (NO Run/Phase, NO resolver,  │
│         ▼                       │             │  NO reduction, NO            │
│  External tools                 │             │  cardinality enforcement)    │
│  (scip-*, joern,                │             └──────────────────────────────┘
│   syft, tokei)                  │             ┌──────────────────────────────┐
│                                 │ ──────────► │ Definition Manager           │
└─────────────────────────────────┘  register   │ Ascription registry          │
                                                └──────────────────────────────┘
```

## Repository layout

```
itip/itip-definition-blackboard-repository-sourcer/
  README.md                                   (this file)
  def/
    components.puml                           (CD - client sourcer components overview)
    sourcing-workflow.puml                    (Sequence - end-to-end sourcing workflow:
                                                 ITIP-specific client-side KS choreography
                                                 + substrate-side REST sequence, single Bb)
    panels.puml                               (CD - all 7 client-declared panels + DAG)
    contributions/                            (per-slot value schemas)
      Definition/{ClaimContribution,MergeContribution,VerdictContribution}.schema.json
      Archetype/ApplicationContribution.schema.json
      Statement/DraftContribution.schema.json
```

## What the sourcer ships (client-internal)

1. **`RepoBootstrap`** (was `GitRepoInstructionsProvider`) — clones the
   git repo, materialises a working copy, derives scope (include/exclude
   globs) and profile. Posts NO contributions to the blackboard service.
2. **Discovery / lift Knowledge Sources** that POST contributions to the
   `itip:Source*` panels (`SourceInventory`, `SourceUnit`,
   `SourceRelation`, `SourceInterface`).
3. **GSM-spine Knowledge Sources** that POST contributions to the
   `itip:Definition` / `itip:Archetype` / `itip:Statement` panels.
4. **Client reducer (controller-side)** that, after the Blackboard
   is sealed, reads the byte-stable sealed contribution stream
   (`GET /blackboards/{bbId}/contributions`) and applies the per-slot
   reduction policy declared in [panels.puml](def/panels.puml) notes
   (`highestConfidence` / `collectAll` / `compose` / ...). Every
   reducer invocation is logged `(reducerId, version, params,
   inputContributionURIs[], outputValue)` so the reduction is
   replayable as a deterministic process over the sealed stream.
5. **Post-reduction pipeline** that validates the reduced statements
   against archetypes and registers the resulting Ascriptions into
   the Definition Manager.

The blackboard service has zero hardcoded knowledge of any of the
above. Panels and slot schemas are **declared by the sourcer** at run
start via a single `POST /blackboards/{bbId}/panels` call.

## Single-Blackboard Run model (client-side)

The client's "Run" is **client-internal state only**. The service has
no `Run` or `Phase` resource. The sourcer models a Run as **one
Blackboard** carrying all 7 panels:

| Stage              | Client action                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------------- |
| Run start          | `POST /blackboards { name: "<repoLabel>" }` ⇒ `bbId` (`runId` is client-side state)                       |
| Topology           | `POST /blackboards/{bbId}/panels` declaring all 7 panels (4 `itip:Source*` + 3 GSM-spine) and their DAG    |
| KS loop            | Discovery / lift KSs and GSM-spine KSs schedule freely on the single DAG; `POST /blackboards/{bbId}/contributions` |
| Seal               | `POST /blackboards/{bbId}/seal` (client-initiated, after client-side quiescence)                            |
| Reduce             | `GET /blackboards/{bbId}/contributions` (paginated; byte-stable sealed stream), then run client reducer per `(panel, slot, client-defined identityKey inside post)` and log the reducer process |
| Pipeline           | Validate reducedView + register Ascriptions to Definition Manager                                          |

This aligns with the substrate's Blackboard-isolation invariant:
each Blackboard is a self-standing resource with no inter-Blackboard
relation. The substrate has no `correlationId`, no Run, no chaining;
the client's `runId` lives entirely in client-side state and the
client's own logs.

The Blackboard-seal state machine is **server-owned** (see the service
README); the client controls only when to call `POST .../seal`.

## Panel summary

See [panels.puml](def/panels.puml) for the authoritative panel topology,
slot maps, and dependency DAG.

| Panel                  | Closed slot set                                                                                                          | DAG dependencies          | Client reduction policy |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------- | ------------------------ |
| `itip:SourceInventory` | `FileTreeNode`, `GitCommit`, `ModuleSpec`, `LanguageClassification`, `FileMetrics`, `SbomComponent`, `ConfigEntry`       | (root)                    | `firstWriter`            |
| `itip:SourceUnit`      | `CodeUnit`, `JpaEntity`, `ProtoMessage`, `GraphqlSchema`, `IacResource`                                                  | `itip:SourceInventory`    | `firstWriter`            |
| `itip:SourceRelation`  | `SymbolRef`, `TypeHierarchyEdge`, `ImplementationEdge`, `ImportEdge`, `CallEdge`, `DataflowEdge`, `DeploymentEdge`       | `itip:SourceUnit`         | `collectAll`             |
| `itip:SourceInterface` | `OpenApiOperation`, `KafkaTopic`, `DbTable`, `TestSpec`                                                                  | `itip:SourceUnit`         | `firstWriter`            |
| `itip:Definition`      | `Claim`, `Merge`, `Verdict`                                                                                              | `itip:SourceInterface`, `itip:SourceRelation`, `itip:SourceUnit`, `itip:SourceInventory` | per-slot (Claim=highestConfidence, Merge=collectAll, Verdict=highestConfidence) |
| `itip:Archetype`       | `Application`                                                                                                            | `itip:Definition`         | `collectAll`             |
| `itip:Statement`       | `Draft`                                                                                                                  | `itip:Archetype`          | `compose`                |

Reduction is **client-controller-side, post-seal**. The blackboard
service neither knows nor enforces these policies — it stores every
contribution and exposes the byte-stable sealed stream. See the
[Reduction (controller-side)](#reduction-controller-side) section
below.

`itip:Definition.Verdict` is written by the top-down `GranularityKS`
that reads `itip:Statement` — this is the one TOP_DOWN edge in the
DAG.

## Knowledge Sources

| KS                       | Family            | Direction    | Writes panel.slot                                                                                                  | External tools                                       |
| ------------------------ | ----------------- | ------------ | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `RepoInventoryKS`        | discovery         | bottom-up    | `itip:SourceInventory.{FileTreeNode, GitCommit, ModuleSpec, LanguageClassification, FileMetrics, SbomComponent}`   | `tokei`, `syft`                                      |
| `ScipIngestKS`           | discovery         | bottom-up    | `itip:SourceUnit.CodeUnit`, `itip:SourceRelation.{SymbolRef, TypeHierarchyEdge, ImplementationEdge, ImportEdge}`   | `scip-java` / `scip-typescript` / `scip-python` / `scip-go` |
| `ConfigInterfaceKS`      | discovery         | bottom-up    | `itip:SourceInventory.ConfigEntry`, `itip:SourceInterface.{OpenApiOperation, KafkaTopic, DbTable}`                 | —                                                    |
| `JoernCallGraphKS`       | relation          | bottom-up    | `itip:SourceRelation.{CallEdge, DataflowEdge}`                                                                     | `joern-parse`, `joern-export`                        |
| `InterfaceLiftKS`        | interface         | bottom-up    | `itip:SourceInterface.{OpenApiOperation, KafkaTopic, DbTable, TestSpec}`                                           | —                                                    |
| `GsmIdentificationKS`    | GSM-identity      | bottom-up    | `itip:Definition.Claim`                                                                                            | —                                                    |
| `GsmReconciliationKS`    | GSM-identity      | bottom-up    | `itip:Definition.Merge`                                                                                            | —                                                    |
| `ArchetypeMatchingKS`    | GSM-class         | bottom-up    | `itip:Archetype.Application`                                                                                       | DefMan exploration (read-only)                       |
| `StatementShapingKS`     | GSM-shape         | bottom-up    | `itip:Statement.Draft` (+ evidence)                                                                                | `InferenceService` (LLM, with `cacheKey`)            |
| `GranularityKS`          | GSM-consolidation | **top-down** | `itip:Definition.Verdict`                                                                                          | —                                                    |
| `ConfidenceSynthesisKS`  | GSM-shape         | bottom-up    | `itip:Statement.Draft` (refined, synth confidence)                                                                 | reads full `itip:Statement` contribution stream      |

All KSs run inside the ITIP repository sourcer process. The driver
schedules them on a single DAG; KSs that consume a panel's view
simply gate their trigger predicate on that panel having
contributions (read live with `GET /blackboards/{bbId}/state`).

## Reduction (controller-side)

The Definition Blackboard Manager **does not reduce** contributions.
There is no `/resolution` resource and no server-side resolver
catalogue. The substrate stores every accepted contribution and, on
seal, freezes the contribution stream into a byte-stable order
(`(panel, slot, timestamp, contributionId)`).

Reduction happens **inside the ITIP sourcer**, after seal:

1. `RunDriver` calls `GET /blackboards/{bbId}/contributions` (paginated)
   to read the sealed stream.
2. The **client reducer** applies the per-slot policy from the table
   above (`highestConfidence` / `collectAll` / `compose`).
3. Each reducer invocation is logged with `(reducerId, version,
   params, inputContributionURIs[], outputValue)` so the entire
   reduction is **replayable** as a deterministic process over the
   sealed stream.
4. (Optional) The reducer MAY post terminal contributions on a
   `<itip:Resolved.*>` panel **before seal** so the reduced output
   is itself a first-class auditable Bb citizen. The substrate
   enforces no cardinality on those slots either; the client
   reducer guarantees it posts at most one contribution per slot.

The substrate enforces **no per-slot cardinality**: it accepts every
well-formed contribution and freezes the byte-stable sealed stream.
All cardinality decisions (single-valued vs many-valued semantics
per slot) are client-controller-side, applied during reduction over
the sealed stream.

## External tools strategy

Client-side. The sourcer reuses well-tested binaries instead of
re-implementing them in-process:

- **SCIP family** (`scip-java`, `scip-typescript`, `scip-python`, `scip-go`)
  — language-specific symbol extractors emitting the SCIP wire format.
- **Joern** (`joern-parse`, `joern-export`) — Code Property Graph for
  call-graph and dataflow analysis.
- **syft** — SBOM extraction (CycloneDX).
- **tokei** — language classification and file metrics.

External tool authorization, sandboxing (`execve` allowlist),
credentials, and output capture are entirely client-side. The
blackboard service sees only the resulting REST contributions.

## Coherence with GSM

Mapping rules embedded in `GsmIdentificationKS` (subjectKind +
`identityKey`):

| Source-side cue                                              | GSM subject              |
| ------------------------------------------------------------ | ------------------------ |
| HTTP service / Kafka producer-consumer / RDBMS façade        | `Interaction`            |
| Method / module that implements behaviour                    | `Mechanism`              |
| Sensor / telemetry / config-reader                           | `Receptor`               |
| Side-effecting actuator (DB writer, mailer, IaC apply)       | `Effector`               |
| Type / data model / schema                                   | `Structure`              |
| Versioned archetype binding declared in code/config          | `Archetype`              |
| Policy / constraint document or annotation                   | `Norm` / `Directive`     |

The authoritative skill for the REST architecture is
[`definition-blackboard-manager`](../../.github/skills/definition-blackboard-manager/SKILL.md)
(covers both the service and the client sourcer authoring contract).

## See also

- Blackboard service: [sie/sie-definition-blackboard-manager/](../../sie/sie-definition-blackboard-manager/)
- Service component diagram: [component.puml](../../sie/sie-definition-blackboard-manager/def/blackboard/component.puml)
- Sourcing workflow (client choreography + substrate REST): [sourcing-workflow.puml](def/sourcing-workflow.puml)
