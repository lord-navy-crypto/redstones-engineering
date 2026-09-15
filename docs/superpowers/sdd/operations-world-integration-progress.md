# SDD ledger — plan: docs/superpowers/plans/2026-09-15-operations-world-integration.md

## Pre-flight rulings

| Scope | Producer / consumer | Finding |
| --- | --- | --- |
| Task 1 → Task 2 | world resource provider → existing blocks | Compatible; provider must remain evidence-only. |
| Task 2 → Task 4 | device evidence → Operations Monitor | Compatible; monitor remains observer-only. |
| Task 3 → Task 5/6 | machine resource evidence → workcell binding/controller | Compatible; explicit binding only. |
| Task 5 → Task 6 | persisted workcell binding → controller UI | Compatible; identities stay server-side. |
| Task 7 → Task 8 | persisted lot/WIP state → buffer block | Compatible; buffer runtime remains decision authority. |
| Task 9 | all prior tasks → cross-block verification | Compatible; verifier must execute in workflow rather than syntax-check only. |

Ruling: implement Task 1 first on isolated branch `operations-world-adapter-69`; use repository CI as the executable test environment because the local container cannot resolve GitHub.

## Task 1

- RED commit `bd83c0c44dd1d8296f19e20e8052dc631b02ff6b`: added executable verifier expecting missing provider/snapshot/resolver files.
- Awaiting CI red confirmation before production implementation.
