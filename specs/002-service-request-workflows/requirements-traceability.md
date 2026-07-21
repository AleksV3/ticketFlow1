# Design Verification and Traceability

T014 review completed on 2026-07-21. Each functional requirement has an
authoritative design location and an implementation/test task.

| Requirement | Design evidence | Implementation/verification |
|---|---|---|
| FR-001 four types/workflows | `workflow-matrix.md` | T043–T051 |
| FR-002 active ordered subtypes | `data-model.md`, `contracts/admin.md` | T015, T019, T026, T033 |
| FR-003 typed dynamic forms | `field-templates.md`, `data-model.md` | T015, T020, T027, T034–T035 |
| FR-004 server definition validation | `contracts/tickets.md`, `threat-model.md` | T035, T041 |
| FR-005 deterministic routing | `data-model.md`, `contracts/admin.md` | T016, T021, T028, T036 |
| FR-006 preserve analysis assignment | `workflow-matrix.md` | T036, T047, T050 |
| FR-007 relationship approval | `workflow-matrix.md`, `threat-model.md` | T047–T050 |
| FR-008 atomic decisions | `data-model.md`, `contracts/tickets.md` | T016, T046–T050 |
| FR-009 required reasons | `workflow-matrix.md` | T046–T050 |
| FR-010 organization availability | `contracts/admin.md` | T018, T029, T032 |
| FR-011 safe parent/child | `data-model.md`, `contracts/tickets.md` | T017, T038, T041 |
| FR-012 enriched authorized detail | `contracts/tickets.md` | T039–T040 |
| FR-013 list/board filters | `contracts/tickets.md` | T039, T059 |
| FR-014 defect capability | `migration-strategy.md` | T018, T044 |
| FR-015 defaults/history preservation | `field-templates.md`, `migration-strategy.md` | T013, T043–T045, T064 |

Constitution principles I–VIII pass at design time. The threat model covers all
named threats from T012, DTOs use explicit allowlists, protected operations are
separate from generic transitions, and migration is additive. Implementation
remains responsible for proving these claims with the tests named above.

