# ITIP Definition Blackboard Repository Sourcer (`itip/itip-definition-blackboard-repository-sourcer`)

A **client sourcer** that turns a code repository (an IT system's
source tree) into GSM contributions posted to a Definition Blackboard.
Runs as a single-JVM client process and speaks REST to the
[Definition Blackboard Manager](../../sie/sie-definition-blackboard-manager/).

This document is **design-time** only.

The authoritative model lives in [components.puml](def/components.puml).
This README is a textual restatement of that diagram; if the two
disagree, the diagram wins.

## Core invariant

> **The blackboard hosts ONLY confidence-bearing identifications**
> (`confidence.score < 1.0` by construction). Deterministic tool
> outputs (SCIP index, CPG, SBOM, framework inventory, assembled code
> graph) flow **in-memory** through stateful Services co-located with
> their KS clients in a single JVM, and are **NEVER persisted** — not
> on the blackboard, not on disk, not in any cache. Re-derivable on
> demand from each contribution's `provenance.indexedRevision` +
> `provenance.tools[]`.

Two consequences:

- "Identification" replaces the older "fact" terminology. A _fact_
  has 100% confidence by definition; a _blackboard contribution_ has
  `confidence.score < 1.0` by construction. The two are disjoint.
- Each spine KS embeds its **own non-deterministic selection** over
  the deterministic in-memory state owned by the Services
  (e.g. Leiden community detection inside `StructureIdentificationKs`;
  taint / dataflow path selection inside
  `Effector/Receptor/InteractionIdentificationKs`; archetype-fit
  scoring inside `*ArchetypingKs`; LLM completion inside
  `*StatementKs`).

## Components

The sourcer JVM hosts four packages
(see [components.puml](def/components.puml)):

1. **KS contract** — single abstract class `KnowledgeSource`.
2. **Services** — stateful and stateless services that own all
   in-memory context and encapsulate every external dependency
   (substrate REST, LLM, external binaries).
3. **KSs** — 18 concrete `KnowledgeSource` subclasses, organised in
   3 sub-packages of 6 (one per GSM subject).
4. **Blackboard** — REST resource hosted by the Definition Blackboard
   Manager; 3 panels × 6 slots = 18 slots.

Package-level dependencies: `KS ..> SVC` (KSs use Services) and
`SVC ..> BB` (Services post contributions to the Blackboard via the
substrate-facing service).

### KS contract

```java
abstract class KnowledgeSource {
  String                  getFqn();
  Set<ContributionSlot>   getSourceContributionSlots();   // upstream slots read
  Set<ContributionSlot>   getTargetContributionSlots();   // slots this KS writes
  boolean                 isContributableBlackboard();    // gating predicate
  void                    contributeToBlackboard();       // produce + post
}
```

Every one of the 18 spine KSs `extends KnowledgeSource`.

### Services

| Service                       | Stereotype                        | Responsibility                                                                                            |
| ----------------------------- | --------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `ScipService`                 | `<<service,stateful,in-memory>>`  | Owns the parsed SCIP index (documents, symbols, occurrences, external symbols). Lazy-prime.               |
| `CpgService`                  | `<<service,stateful,in-memory>>`  | Owns the loaded CPG (call edges, dataflow). Lazy-prime.                                                   |
| `SbomService`                 | `<<service,stateful,in-memory>>`  | Owns the SBOM (CycloneDX/SPDX components). Lazy-prime.                                                    |
| `LeidenAlgService`            | `<<service,stateless,algorithm>>` | Pure community-detection algorithm over an in-memory graph.                                               |
| `MappingService`              | `<<service,stateless,facade>>`    | Source-node-to-archetype mapping + property extraction; lists supported archetypes and gaps.              |
| `InferenceService`            | `<<service,external>>`            | LLM completion (used by Statement KSs in LLM-driven mode).                                                |
| `DefinitionBlackboardService` | `<<service,external>>`            | REST client to the Definition Blackboard Manager (panel declaration, contribution POST, GET state, seal). |

Stateful services lazy-prime themselves on first call. Their internal
state is never persisted; it is fully reconstructible from the
provenance envelope of any downstream contribution.

### KSs (18, organised in 3 stages × 6 subjects)

For each subject S in
`{Structure, Mechanism, DataArchetype, Effector, Receptor, Interaction}`:

| KS                     | Writes blackboard slot         | Embedded non-deterministic selection                                                                                                       |
| ---------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `S`​`IdentificationKs` | `itip:Definition.S`​`Identity` | Subject-specific (Leiden for Structure; taint-reach for Effector/Receptor/Interaction; structural heuristics for Mechanism/DataArchetype). |
| `S`​`ArchetypingKs`    | `itip:Archetype.S`​`Archetype` | Archetype-fit scoring against the archetype catalogue, via `MappingService`.                                                               |
| `S`​`StatementKs`      | `itip:Statement.S`​`Statement` | Optional LLM-driven shaping via `InferenceService`.                                                                                        |

**What Identification does that Archetyping does not.**
Identification answers _"is there a GSM subject of type S here at
all?"_ against the raw substrate; Archetyping answers _"given this
identified subject, which concrete archetype (and with what
properties) does it conform to?"_ against the archetype catalogue.
Splitting them gives separately-auditable confidences for the two
distinct uncertainty-reduction steps and a clear remediation path
for each (false positive vs. wrong archetype binding).

## Repository layout

```
itip/itip-definition-blackboard-repository-sourcer/
  README.md                  (this file)
  def/
    components.puml          (CD - 4 packages: KS contract / Services / KSs / Blackboard)
    sourcing-workflow.puml   (Sequence - aligned to components: KSs use Services;
                                Services post contributions to the Blackboard;
                                3 phases (Identification, Archetyping, Statement) + Seal)
    contributions/           (per-slot contribution schemas; one JSON Schema file per slot
                                in components.puml, plus shared envelopes at the same level)
      confidence-envelope.schema.json
      provenance-envelope.schema.json
      Definition/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Identity.schema.json
      Archetype/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Archetype.schema.json
      Statement/{Structure,Mechanism,DataArchetype,Effector,Receptor,Interaction}Statement.schema.json
    frameworks/              (sourced framework catalogues consumed by MappingService)
```

## Blackboard topology (3 panels × 6 GSM subjects = 18 slots)

The blackboard carries exactly **3 panels**, each with **6 slots**,
one per GSM subject:

| Panel             | Stage semantics                                                         | Slots (one per subject)                                                                                                                |
| ----------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `itip:Definition` | Candidate identification: "this thing is a candidate `<Subject>`"       | `StructureIdentity`, `MechanismIdentity`, `DataArchetypeIdentity`, `EffectorIdentity`, `ReceptorIdentity`, `InteractionIdentity`       |
| `itip:Archetype`  | Archetype match: "this candidate matches a known `<Subject>` archetype" | `StructureArchetype`, `MechanismArchetype`, `DataArchetypeArchetype`, `EffectorArchetype`, `ReceptorArchetype`, `InteractionArchetype` |
| `itip:Statement`  | Draft GSM Statement: "here is the materialized statement"               | `StructureStatement`, `MechanismStatement`, `DataArchetypeStatement`, `EffectorStatement`, `ReceptorStatement`, `InteractionStatement` |

**Subjects** (6, mapped to GSM primitives):

| Subject         | Maps to GSM primitive | Notes                                                                                                                                                                                                                                       |
| --------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Structure`     | `Structure`           | Structural unit (package, module, class cluster).                                                                                                                                                                                           |
| `Mechanism`     | `Mechanism`           | Behavioural mechanism (algorithm, service, controller).                                                                                                                                                                                     |
| `DataArchetype` | `Archetype`           | Data shape (DTO, entity, schema record). Maps to GSM `Archetype` in the data-shape role; the slot name `DataArchetypeArchetype` (Archetype-stage of the DataArchetype subject) is intentional and reflects the lifecycle pattern uniformly. |
| `Effector`      | `Effector`            | Outbound effector (HTTP client, message producer, DB writer, scheduled task).                                                                                                                                                               |
| `Receptor`      | `Receptor`            | Inbound receptor (HTTP endpoint, message consumer, DB reader, event listener).                                                                                                                                                              |
| `Interaction`   | `Interaction`         | Call / dataflow / message / dependency relation between two subjects.                                                                                                                                                                       |

**DAG** (read-up promotion only):

```
Definition (*Identity)  →  Archetype (*Archetype)  →  Statement (*Statement)
```

## Contribution shape (every slot)

Every contribution `allOf`-merges the two shared envelopes:

- **`confidence-envelope`** — `score` (0 ≤ score < 1, exclusive
  upper bound enforces the core invariant), `method` (scoring scheme
  identifier), `factors[]` (explainability fragments).
- **`provenance-envelope`** — `ks` (`<fqn>@<version>`),
  `indexedRevision` (typically a git sha), `tools[]` (deterministic
  tools used to prime the Services, with versions),
  `ruleSnapshotSha256` (sha of the rule snapshot driving non-
  deterministic selection), `seeds[]` (RNG seeds for reproducibility).

Per-slot properties:

- `subject` — canonical reference to the candidate subject. For code-
  anchored subjects this is typically a SCIP symbol moniker. For
  `Interaction*` slots, additionally `sourceSubject`, `targetSubject`,
  `interactionKind`.
- `evidence[]` — optional explainability fragments (SCIP occurrence
  ranges, framework annotation hits, dataflow path summaries, ...).
  Pure references; raw deterministic graphs are NOT persisted here.

## Reproducibility contract

Any blackboard contribution can be regenerated by:

1. Re-priming the Services at `provenance.indexedRevision` with
   `provenance.tools[]` versions → reconstructs the in-memory state
   byte-for-byte.
2. Re-running the named KS at `provenance.ruleSnapshotSha256` with
   `provenance.seeds[]` → reconstructs the embedded non-deterministic
   selection.

This is what justifies persisting only confidence-bearing
identifications on the blackboard: everything deterministic is
re-derivable from the provenance envelope.

## Coherence with GSM

Mapping rules embedded in `MappingService` (subject + identity key
derivation, consumed by the `*IdentificationKs` of each subject):

| Source-side cue                                       | GSM subject (slot family)        |
| ----------------------------------------------------- | -------------------------------- |
| HTTP service / Kafka producer-consumer / RDBMS façade | `Interaction*`                   |
| Method / module that implements behaviour             | `Mechanism*`                     |
| Sensor / telemetry / config-reader                    | `Receptor*`                      |
| Side-effecting actuator (DB writer, mailer, ...)      | `Effector*`                      |
| Type / data model / schema                            | `DataArchetype*` or `Structure*` |
| Package / module / class cluster                      | `Structure*`                     |

`Norm` and `Directive` GSM primitives are **out of scope** for the
repository sourcer (they are sourced by other ITIP framework sourcers,
not by code-repo analysis).

## Out of scope (explicitly)

The following concerns are **not** modelled in components.puml and
therefore **not** part of this sourcer's design contract:

- Run loop / KS scheduler / orchestration driver
- Repository bootstrap / git clone
- External-tool sandboxing (the diagram does not expose external
  binaries; binary invocation, if any, is an internal detail of the
  stateful Services that lazy-prime themselves)
- Post-seal sealed-stream consumption, client-side reduction,
  archetype-schema validation of reduced statements, Ascription
  registration into the Definition Manager — these are downstream
  client concerns that consume `GET /blackboards/{bbId}/contributions`
  from the sealed stream
- IaC / Terraform / Helm / Kustomize sourcing

The authoritative skill for the REST architecture is
[`definition-blackboard-manager`](../../.github/skills/definition-blackboard-manager/SKILL.md)
(covers both the service and the client sourcer authoring contract).

## See also

- Blackboard service: [sie/sie-definition-blackboard-manager/](../../sie/sie-definition-blackboard-manager/)
- Service component diagram: [component.puml](../../sie/sie-definition-blackboard-manager/def/blackboard/component.puml)
- Components (this sourcer): [components.puml](def/components.puml)
- Sourcing workflow: [sourcing-workflow.puml](def/sourcing-workflow.puml)
