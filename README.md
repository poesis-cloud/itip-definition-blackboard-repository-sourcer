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
│ • Knowledge Sources (8 panels   │             │ • Panel / Slot registry      │
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
    components.puml                           (CD - merged structural view: client
                                                 sourcer components + 8 panels + 37
                                                 contribution slots + KSs; relations
                                                 are KS->ContributionSlot and
                                                 Panel *-- ContributionSlot)
    sourcing-workflow.puml                    (Sequence - end-to-end sourcing workflow:
                                                 ITIP-specific client-side KS choreography
                                                 + substrate-side REST sequence, single Bb)
    contributions/                            (per-slot value schemas; one JSON Schema
                                                 file per ContributionSlot in components.puml)
      Repository/{FileNode,SbomComponent,ConfigEntry,Project}.schema.json
      Index/{Scip,JoernCpg,Sbom,ScipDocument,ScipSymbol,ScipOccurrence,ScipExternalSymbol}.schema.json
      Framework/{Endpoint,DiComponent,ConfigurationProperty,
                          PersistenceEntity,PersistenceRepository,
                          MessageConsumer,MessageProducer,
                          HttpClient,ScheduledTask}.schema.json
      Iac/{IacResource,IacModule,IacVariable,IacOutput,IacProviderRef}.schema.json
      CodeGraph/{SymbolRef,TypeHierarchyEdge,ImplementationEdge,ImportEdge,
                 CallEdge,DataflowEdge,DeploymentEdge,Community,Process}.schema.json
      Definition/{ClaimContribution,MergeContribution,VerdictContribution}.schema.json
      Archetype/ApplicationContribution.schema.json
      Statement/DraftContribution.schema.json
```

## What the sourcer ships (client-internal)

1. **`RepoBootstrap`** (was `GitRepoInstructionsProvider`) — clones the
   git repo, materialises a working copy, derives scope (include/exclude
   globs) and profile. Posts NO contributions to the blackboard service.
2. **Repository-analysis Knowledge Sources** that POST contributions to the
   `itip:Repository` / `itip:Index` /
   `itip:Framework` / `itip:Iac` / `itip:CodeGraph`
   panels. KSs are grouped by **support module** loaded by the
   client controller (v1: `core`, `java`, `maven`, `gradle`,
   `spring`, `iac`, `graph-analysis`). Adding a new ecosystem (e.g. `js-ts`,
   `python`, `dotnet`, `go`) means adding a support module +
   KSs that contribute slots to the existing repository-analysis
   panels — **no new panels** are needed.
3. **GSM-spine Knowledge Sources** that POST contributions to the
   `itip:Definition` / `itip:Archetype` / `itip:Statement` panels.
4. **Client reducer (controller-side)** that, after the Blackboard
   is sealed, reads the byte-stable sealed contribution stream
   (`GET /blackboards/{bbId}/contributions`) and applies the per-slot
   reduction policy declared on each `ContributionSlot` in
   [components.puml](def/components.puml) (`highestConfidence` /
   `collectAll` / `compose`). Every
   reducer invocation is logged `(reducerId, version, params,
   inputContributionURIs[], outputValue)` so the reduction is
   replayable as a deterministic process over the sealed stream.
5. **Post-reduction pipeline** that validates the reduced statements
   against archetypes and registers the resulting Ascriptions into
   the Definition Manager.
6. **Read-through cache (REST + blob client)** in front of every
   blackboard read. Sealed contributions are immutable and have
   content-derived identityKeys, so the cache is **trivially
   coherent**: cache by contribution URI for payloads, by
   `(bbId, panel, slot)` + ETag for live views, by
   `(panel, slot, reducerVersion, hash(input contribution URIs))`
   for reducer outputs, and by content-addressed `sha256` for
   `Index.*` blobs. Blob hits MUST verify
   `sha256(local-bytes) == nativeAttributes.sha256` and fall back
   to BundleStore on mismatch. **Local paths MUST NOT appear in any
   contribution payload** — the sealed stream's authoritative
   reference for blobs is always `nativeAttributes.blobHandle`.
   The cache is purely a sourcer-internal optimization (deletable
   at any time without breaking correctness); it is NOT part of
   the sealed-stream contract.

The blackboard service has zero hardcoded knowledge of any of the
above. Panels and slot schemas are **declared by the sourcer** at run
start via a single `POST /blackboards/{bbId}/panels` call.

## Single-Blackboard Run model (client-side)

The client's "Run" is **client-internal state only**. The service has
no `Run` or `Phase` resource. The sourcer models a Run as **one
Blackboard** carrying all 8 panels:

| Stage              | Client action                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------------- |
| Run start          | `POST /blackboards { name: "<repoLabel>" }` ⇒ `bbId` (`runId` is client-side state)                       |
| Topology           | `POST /blackboards/{bbId}/panels` declaring all 8 panels (5 repository-analysis + 3 GSM-spine) and their DAG        |
| KS loop            | Repository-analysis KSs and GSM-spine KSs schedule freely on the single DAG; `POST /blackboards/{bbId}/contributions` |
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

See [components.puml](def/components.puml) for the authoritative panel
topology, slot maps (`Panel *-- ContributionSlot`), KS-to-slot
bindings (`KS --> ContributionSlot`), and the panel dependency DAG.
Repository-analysis panels are stratified along an abstraction
layer (`Repository` -> `Index` -> `Framework` ; `Iac`
parallel to `Index` ; `Framework` derived from `Index.Scip*`
mirror slots), with `CodeGraph` cross-cutting; support modules
contribute slots into these stable panels.

| Panel                       | Module(s)        | Closed slot set (v1)                                                                                                                                                  | DAG dependencies                                          | Client reduction policy |
| --------------------------- | ---------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | ----------------------- |
| `itip:Repository`  | core + maven + gradle | `FileNode`, `SbomComponent`, `ConfigEntry`, `Project`                                                                                                                 | (root)                                                    | `collectAll`            |
| `itip:Index`       | core + java           | `Scip`, `JoernCpg`, `Sbom` (raw indexer artefacts: handle + manifest, blob in BundleStore) **+** `ScipDocument`, `ScipSymbol`, `ScipOccurrence`, `ScipExternalSymbol` (SCIP-mirror: row-grain projections of `Scip`, one slot per top-level SCIP record type) | `itip:Repository`                                         | `collectAll`            |
| `itip:Framework`   | spring | `Endpoint`, `DiComponent`, `ConfigurationProperty`, `PersistenceEntity`, `PersistenceRepository`, `MessageConsumer`, `MessageProducer`, `HttpClient`, `ScheduledTask` | `itip:Index` (SCIP-mirror slots)                          | `collectAll`            |
| `itip:Iac`         | iac              | `IacResource`, `IacModule`, `IacVariable`, `IacOutput`, `IacProviderRef`                                                                                              | `itip:Repository` (parallel to Index/Framework)           | `collectAll`            |
| `itip:CodeGraph`            | java + core + graph-analysis | `SymbolRef`, `TypeHierarchyEdge`, `ImplementationEdge`, `ImportEdge`, `CallEdge`, `DataflowEdge`, `DeploymentEdge`, `Community`, `Process`                            | `itip:Index`, `itip:Framework`, `itip:Iac`               | `collectAll`            |
| `itip:Definition`           | core (GSM-spine) | `Claim`, `Merge`, `Verdict`                                                                                                                                           | `itip:Repository` + `itip:Index` + `itip:Framework` + `itip:Iac` + `itip:CodeGraph` | per-slot (Claim=highestConfidence, Merge=collectAll, Verdict=highestConfidence) |
| `itip:Archetype`            | core (GSM-spine) | `Application`                                                                                                                                                         | `itip:Definition`                                         | `collectAll`            |
| `itip:Statement`            | core (GSM-spine) | `Draft`                                                                                                                                                               | `itip:Archetype`                                          | `compose`               |

Reduction is **client-controller-side, post-seal**. The blackboard
service neither knows nor enforces these policies — it stores every
contribution and exposes the byte-stable sealed stream. See the
[Reduction (controller-side)](#reduction-controller-side) section
below.

`itip:Definition.Verdict` is written by the top-down `GranularityKS`
that reads `itip:Statement` — this is the one TOP_DOWN edge in the
DAG.

## Knowledge Sources

KSs are organized below by **primary target panel**; module
membership (which ecosystem module ships the KS) is shown in the
`Module` column. A few KSs cross panel boundaries (most notably
`ScipJavaKS`, whose single SCIP-index artefact populates the
four `itip:Index.Scip*` mirror slots and four `itip:CodeGraph.*` slots);
these are listed under their identity-bearing panel and the
secondary writes are noted inline.

### Repository-analysis KSs

**Writes `itip:Repository`:**

| KS                  | Module | Direction | Writes panel.slot                            | External tools  |
| ------------------- | ------ | --------- | -------------------------------------------- | --------------- |
| `FileKS`        | core   | bottom-up | `itip:Repository.FileNode`          | `tokei`, git    |
| `SbomKS`            | core   | bottom-up | `itip:Index.Sbom` **(primary write)** + `itip:Repository.SbomComponent` (semantic projection) | `syft`          |
| `ConfigKS`          | core   | bottom-up | `itip:Repository.ConfigEntry`       | —               |
| `MavenProjectKS`       | maven  | bottom-up | `itip:Repository.Project` (in-process projection of pom.xml)                                                    | POM parser     |
| `GradleProjectKS`      | gradle | bottom-up | `itip:Repository.Project` (in-process projection of build.gradle(.kts))                                         | Gradle Tooling API / build-script parser |

**Writes `itip:Index`:**

| KS                   | Module | Direction | Writes panel.slot                                                                                                  | External tools |
| -------------------- | ------ | --------- | ------------------------------------------------------------------------------------------------------------------ | -------------- |
| `ScipJavaKS`         | java   | bottom-up | `itip:Index.Scip` **(primary write: raw `.scip` protobuf handle + manifest)** **+** `itip:Index.{ScipDocument, ScipSymbol, ScipOccurrence, ScipExternalSymbol}` (SCIP-mirror projections, one slot per top-level SCIP record type) **+** `itip:CodeGraph.{SymbolRef, TypeHierarchyEdge, ImplementationEdge, ImportEdge}` (one SCIP index, two panels) | `scip-java`    |

> **v1 supported languages.** Code-level indexing (slots `itip:Index.Scip*`
> mirror family and the four structural `itip:CodeGraph.*` edges) is **SCIP-only**:
> v1 ships `ScipJavaKS` (Java/Scala/Kotlin); future ecosystem modules
> add `ScipTypescriptKS` <<module:js-ts>>, `ScipPythonKS` <<module:python>>,
> `ScipGoKS` <<module:go>>, `RustAnalyzerKS` <<module:rust>>,
> `ScipDotnetKS` <<module:dotnet>>, … as their SCIP indexers mature.
> Files in **unsupported languages** are still visible in
> `Repository.FileNode` (classified by `tokei`) and counted in SBOM
> (via `syft`), but produce no `Index.Scip*` mirror /
> `CodeGraph.*` contributions and therefore no `Definition`
> claims rooted in them. SCIP-gap languages (Ruby, PHP, C/C++,
> Swift, Lua, Elixir, …) are **out of scope for v1**; a future
> polyglot fallback can be added as an additional support module
> if/when needed.

> **`itip:Index.*` producer rule.** Each `itip:Index.*` slot has
> **exactly one producer** per language/tool scope: `Scip*KS` for
> `Scip` (one per language), `JoernKS` for `JoernCpg`, `SbomKS` for
> `Sbom`. Other KSs whose outputs *could* technically be expressed
> in these formats (Maven/Gradle declared deps, Spring annotations,
> config keys) **MUST NOT** be implemented as additional indexers
> writing into the same `Index.*` artefact. Instead, each owns its
> own semantic slot; cross-source joins (e.g. *intent* declared in
> a POM vs. *observation* in the SCIP index) happen at reduction
> time using shared SCIP symbol ids as the join key. This
> preserves intent/observation separability, avoids format
> pollution, decouples indexer release cadences from semantic-slot
> evolution, and uses the blackboard's per-slot reduction
> mechanism as designed. Semantic projections of `Index.*`
> artefacts (`Index.Scip*` mirror family, `CodeGraph.{SymbolRef, *Edge}`,
> `Repository.SbomComponent`) remain in their respective panels as
> deterministic materialised views.

> **SCIP as identity backbone.** All KSs that contribute facts about
> code elements (framework projections, call/dataflow edges,
> communities, processes) MUST reference those elements using
> **SCIP symbol identifiers and SCIP source ranges**, reused
> verbatim from the sealed `itip:Index.Scip*` mirror contributions
> (`ScipDocument`/`ScipSymbol`/`ScipOccurrence`/`ScipExternalSymbol`)
> produced by `Scip*KS`. Each panel owns its own semantic schema,
> but symbol identity is shared. This guarantees joinability across
> panels (e.g. an `itip:Framework.Endpoint` joins with
> `itip:CodeGraph.CallEdge` on the same symbol id) and byte-stable
> contributions across re-seals. Framework KSs (`SpringScanKS`,
> future `ExpressScanKS`/`FlaskScanKS`/...) are **SCIP consumers,
> not parsers**.

**Writes `itip:Framework`:**

| KS             | Module  | Direction | Writes panel.slot                                                                                                                                                  | External tools |
| -------------- | ------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- |
| `SpringScanKS` | spring  | bottom-up | `itip:Framework.{Endpoint, DiComponent, ConfigurationProperty, PersistenceEntity, PersistenceRepository, MessageConsumer, MessageProducer, HttpClient, ScheduledTask}` | — (SCIP consumer: reads `itip:Index.Scip` and opens its `nativeAttributes.blobHandle` to stream the raw `.scip` protobuf, filters by Spring annotations, projects to Framework slots; reuses SCIP symbol identity and source ranges verbatim — no own Java parser) |

**Writes `itip:Iac`:**

| KS          | Module | Direction | Writes panel.slot                                                                       | External tools                                       |
| ----------- | ------ | --------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| `IacScanKS` | iac    | bottom-up | `itip:Iac.{IacResource, IacModule, IacVariable, IacOutput, IacProviderRef}`    | terraform/helm/kustomize CLIs (read-only parsing)    |

**Writes `itip:CodeGraph`:**

| KS                    | Module | Direction | Writes panel.slot                                | External tools                  |
| --------------------- | ------ | --------- | ------------------------------------------------ | ------------------------------- |
| `JoernKS`             | java     | bottom-up | `itip:Index.JoernCpg` **(primary write: raw CPG handle + manifest)** **+** `itip:CodeGraph.{CallEdge, DataflowEdge}` (semantic projection) | `joern-parse`, `joern-export`   |
| `DeploymentBindingKS` | iac      | bottom-up | `itip:CodeGraph.DeploymentEdge`                  | —                               |
| `CommunityKS`         | graph-analysis | bottom-up | `itip:CodeGraph.Community` (Leiden clusters over already-published `SymbolRef`/`CallEdge`/`ImportEdge`) | `leidenalg` (in-process Python; GPL-3.0) |
| `ProcessKS`           | graph-analysis | bottom-up | `itip:CodeGraph.Process` (DFS over `CallEdge` rooted at `Framework.{Endpoint, MessageConsumer, ScheduledTask}` entry points) | — (in-process) |

> ⚠️ **Licensing attention point (BLOCKING for commercial ship).**
> `CommunityKS` embeds `leidenalg` **in-process** (Python), and
> `leidenalg` is **GPL-3.0**. In-process embedding can extend
> copyleft to the host process. Resolve before first commercial
> release by picking ONE of:
> 1. **Recommended** — swap `leidenalg` → `python-louvain` (BSD) or
>    `cdlib` (BSD-3-Clause) in `CommunityKS`. Slightly lower
>    modularity quality; permissive throughout.
> 2. Isolate `leidenalg` behind a **separate sandboxed subprocess**
>    so the host process is not "combined work" (legal review
>    required to confirm).
> 3. Accept GPL-3.0 obligations for the distributed binary.

> `DeploymentBindingKS` is a cross-correlator joining
> `itip:Framework.*` (e.g. an `Endpoint` symbol)
> with `itip:Iac.*` (e.g. a Kubernetes `Deployment`,
> a Terraform `aws_lambda_function`). It requires **both** the
> framework module **and** the iac module to be loaded; it ships
> with the iac module since "deployment" is the iac concern.
> Plus the four structural CodeGraph edges contributed by
> `ScipJavaKS` (see Code group above).

### GSM-spine KSs

| KS                      | Module     | Direction    | Writes panel.slot                                          | External tools                                  |
| ----------------------- | ---------- | ------------ | ---------------------------------------------------------- | ----------------------------------------------- |
| `GsmIdentificationKS`   | GSM-spine  | bottom-up    | `itip:Definition.Claim`                                    | —                                               |
| `GsmReconciliationKS`   | GSM-spine  | bottom-up    | `itip:Definition.Merge`                                    | —                                               |
| `ArchetypeMatchingKS`   | GSM-spine  | bottom-up    | `itip:Archetype.Application`                               | DefMan exploration (read-only)                  |
| `StatementShapingKS`    | GSM-spine  | bottom-up    | `itip:Statement.Draft` (+ evidence)                        | `InferenceService` (LLM, with `cacheKey`)       |
| `GranularityKS`         | GSM-spine  | **top-down** | `itip:Definition.Verdict`                                  | —                                               |
| `ConfidenceSynthesisKS` | GSM-spine  | bottom-up    | `itip:Statement.Draft` (refined, synth confidence)         | reads full `itip:Statement` contribution stream |

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
