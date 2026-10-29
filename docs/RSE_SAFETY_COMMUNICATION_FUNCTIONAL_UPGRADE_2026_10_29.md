# RSE Industrial Safety + Communications: phase 4

Date: 2026-10-29. Target: `automation/rse-formula-parameter-workbench` / PR #229.

## Implemented

This phase retains all 130 public block IDs, runtime array layouts, BlockState properties, existing recipes and physical network domains. It **does not claim Minecraft GameTestServer runtime acceptance**.

| Device | Actual strengthening | Inspection gesture |
| --- | --- | --- |
| `safety_interlock` | Uses source-quality-aware, three-channel fail-closed permissive logic: VALID positive A+B+C are required; invalid/unloaded/missing signals cannot imply safety. Live diagnostics show values, quality, fault reasons, asserted/evaluated masks and transition count. | Shift + DOWN = read-only; other Shift faces = original diagnostics reset |
| `watchdog` | Read-only heartbeat commissioning: baseline status, valid source quality, elapsed/remaining timeout, transitions, timeouts and output. Does not reset heartbeat age. | Shift + DOWN = inspect; other Shift faces = original diagnostics reset |
| `fault_latch` | Read-only fault source and reset source evidence, threshold, latch state, trips/resets and reset-held state. | Shift + DOWN = inspect; other Shift faces = original manual reset |
| `alarm_processor` | Source-quality-aware input, ack, reset port observation. Alarm output quality is STALE before first runtime evaluation, FAULT while latched and VALID when authoritatively cleared. Live diagnostic inspection no longer implies acknowledge. | Shift + DOWN = inspect; other Shift faces = original acknowledge |
| `serial_data_line` | Shared observer-only byte port snapshot and full-chat site report: actual network quality, driver count, sample age, packet quality, appropriate next inspection and frame diagnostics. | Shift-right-click = inspect; normal right-click = original HMI |
| `differential_data_pair` | Shared observer-only bit port snapshot and full-chat site report: driver count, topology conflicts, sample age, packet quality, scan-truncation evidence and next action. | Shift-right-click = inspect; normal right-click = original HMI |

## Two new internal support classes

- `SafetyObservationSupport`: read-only Redstone source/quality observer, three independently traceable permissive failure bits, and safety fault annotations. This is not a unified safety solver: event latching, timeout timing, severity/acknowledgement and startup logic remain in their respective devices.
- `CommunicationLineEvidenceSupport`: read-only bounded port snapshots and safe human-readable reports. Serial framing (8-bit words), differential bit signaling, actual topology solving and driver ownership all remain their original independent domains.

**Important:** `STALE`, `NO_SIGNAL`, `TOPOLOGY_ERROR`, and an actual measured zero are not synonymous. A displayed invalid payload is explicitly `UNTRUSTED` in service reports rather than a guessed measurement. This does not add ECC, automatic retries, bandwidth or falsely guaranteed link continuity.

## 46-candidate source refactor ledger

Source-level count after this phase: **21/46** helpers wired (previous 15 plus these six); **3/46** independent functional upgrades; **22/46** remaining candidates. All 46 continue to exist as separate physical blocks in the 130-block registry. Do not interpret this as runtime behavioral-equivalence certification.

## Acceptance gates

- `tools/rse_safety_communication_upgrade_verify.py` runs on every PR build and checks helper read-only scope, fail-closed mask, lossless reset/ack paths, distinct bit/byte media, driver quality and test registration.
- `RseSafetyCommunicationUpgradeGameTests` registers two Minecraft runtime tests: **interlockRequiresThreeValidAssertedRedstoneSources** and **communicationCableInspectionIsObserverNeutralBeforeNetworkSolve**.
- Minecraft GameTestServer execution is a separate, explicitly manual diagnostic workflow and is **NOT_RUN** unless independently verified.
- Before release, verify the safety interlock under all 8 binary A/B/C combinations, missing chunks, real valid zeros, load/unload, operator inspection and multiplayer state updates; verify cable zero-data and two-driver conflict in a live Minecraft world; verify all four original reset/ack shortcuts.
- **World-save identity is preserved**, but backward world-load and multiplayer testing remain outstanding.

This phase is a meaningful reliability and observability improvement, not a claim that every fail-safe engineering requirement has been formally certified.
