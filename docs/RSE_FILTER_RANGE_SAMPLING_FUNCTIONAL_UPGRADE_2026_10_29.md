# RSE functional upgrade — filtering, range sensing and sampled-data reliability

**Date:** 2026-10-29. **Target:** PR #229, branch `automation/rse-formula-parameter-workbench`.

## Actual changes

This update preserves **all 130 registered block IDs**, recipes, loot tables, public registry contracts and BlockState/runtime layouts. No new block is introduced; the distinct physical roles and engineering media remain separate.

| Registry ID | Engineering functionality added or corrected | Operator action |
| --- | --- | --- |
| `precision_filter` | Pure bounded slew law reused for authoritative steps; source-quality-aware forecast of next output and number of ticks to target, assuming constant valid input. No fabricated estimate when source is missing. | Shift+UP inspect; Shift another face keeps rate adjustment |
| `lapis_low_pass_filter` | Extracts exact first-order discrete transfer calculation; optional field readout of alpha, current/previous output, input quality, predicted next sample and nominal 90% settling samples; first valid input explicitly seeds its output (no fabricated startup transient). No implicit unloaded-chunk read. | Shift+UP inspect; Shift another face adjusts alpha |
| `signal_conditioner` | Preserves actual RX quality on its port snapshot, distinguishing driven zero, no input and unloaded input; read-only projected output based on mode/parameter and current output, plus limit status. | Shift+UP inspect; Shift another face adjusts parameter; normal click opens existing menu |
| `range_sensor` | Refactors bounded scan into common non-loading ray primitive; BLOCK-only mode skips entity queries; uninitialized/unloaded quality is STALE instead of incorrectly NO_SIGNAL, while a complete CLEAR scan remains a valid measurement. Detailed coverage inspection. | Shift+UP inspect; original Shift side/bottom configuration remains |
| `lapis_precision_range_sensor` | Reuses bounded ray scanner while preserving separate solids/fluid detection and up-to-64-block range, Lapis normalization and transducer profiles. Raw scan coverage and conditioned Lapis output are shown independently; inspector reuses the first ray rather than rescanning twice. | Shift+UP inspect; other Shift faces adjust range |
| `sample_hold` | Server tick only captures when both an actual trigger transition and a valid input sample exist. Missing/unloaded input never creates a fresh **VALID zero**; losing trigger validity drops edge initialization so reappearing HIGH does not fabricate a transition. Intentionally driven zero is still valid. Retains the read-only Shift-BOTTOM inspector from the previous phase. | Existing interactions unchanged |
| `quartz_triggered_lapis_sampler` | Previously enhanced in phase 2. Deliberately unchanged here to preserve existing dedicated Quartz rising-edge and Lapis quality contract. | Original Shift diagnostics and HMI unchanged |

## Intentional non-merges

- Slew is a **rate limit** (`Math.approach`, 0..15/tick) and Lapis low-pass is a **first-order discrete low-pass** (0..100, configurable alpha). Shared `FilterResponseSupport` holds **separate functions** rather than merging their transfer laws.
- Redstone range may detect blocks, living entities, or either. Lapis precision range detects solids/fluids as a different instrument with its own orientation and normalized output. `RangeScanSupport` shares only bounded ray traversal and coverage logic.
- The signal conditioner remains a mode-configured gain/offset/limit/threshold/deadband processor. It is **not** another low-pass; only source-aware diagnostic/readback behavior was strengthened.
- Sample & Hold (Redstone trigger with reset and configurable edge) remains distinct from Quartz-triggered Lapis sampler (always rising edge, separate Lapis medium).

## Compatibility and safety boundaries

- No changes to public IDs, recipes, saved BlockState property names or transducer runtime array sizes.
- Missing chunk coverage is always unknown, never `CLEAR` or an invented measurement; helper does not trigger chunk loading.
- Reports must be read-only. Transient estimates do **not** claim measured latency, stable step response under varying inputs, or physical SI accuracy.
- The LPF 90% sample count uses an idealized geometric response; discrete integer rounding may differ. Nominal 20 TPS is game time, not a real-time performance guarantee.
- Existing normal right-click menus and Shift configuration gestures are retained, except Shift+UP is deliberately repurposed as inspection on the five upgraded devices; UP previously also adjusted certain settings.
- A new registered `RseFilterRangeSamplingUpgradeGameTests` suite has three test witnesses for Redstone range coverage/target, Lapis physical ray direction/measurement and valid-zero versus missing Sample & Hold inputs. **CI's static checks and Gradle tests do not execute these Minecraft GameTests by default.** They remain `NOT_RUN` unless manually dispatched.

## CI and future work

- `tools/rse_filter_range_sampling_upgrade_verify.py` verifies bounded ray use, distinct transfer laws, source-quality semantics and GameTest registration with reference arithmetic.
- `tools/rse_46_shared_code_rollout_verify.py` now tracks **15/46 shared-helper users**, **3/46 independently enhanced devices** and **28 candidates not yet altered**. This measures implemented source changes, not certified runtime equivalence.
- Follow up with Minecraft manual GameTests, an old-world save loaded before/after patch, multiplayer observer/state synchronization, long unchanging vs rapidly changing inputs and game-server profiling of dense sensor fields. Do not claim performance wins before instrumenting runtime.
