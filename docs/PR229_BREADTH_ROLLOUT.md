# PR #229 — Broad LDLib2 Parameter Experience Rollout

The goal is breadth without inventing controls or a second simulator. Keep
Minecraft/NeoForge 1.21.1 / 21.1.249 / Java 21. No wholesale physics rewrite.

## One operator contract across block families

- **Editable numeric**: display the live synchronized value, symbol, unit,
  exact device-specific bounds and any discrete legal set; send user intent
  through the existing server-authoritative menu.
- **Mode / toggle**: show current server-backed mode, not just an unlabeled
  button; use one obvious action.
- **Observer / passive / fixed-physics**: read-only governing contract,
  port/link evidence and model units, without fake inputs.
- **Not ready**: neither a pre-sync zero nor absent quality is a valid physical
  reading or permission to edit.
- **Repeat entry**: legal same-value edits succeed without block writes,
  neighbor notifications, gratuitous network recomputes, false evidence events
  or re-triggered excitation. Invalid inputs fail without mutations.
- **Client**: LDLib2 presents inputs and data; all calculations, safety,
  topology, sampling, scheduling and evidence remain server-owned.

## Coverage overview

These are **shared HMI kinds**, not a claim that all 122 audited blocks have
passed end-to-end visual/runtime testing. FieldDeviceMenu defines 78 concrete
block kinds and the UniversalFieldDeviceMenu defines 23 config-kind families
besides CONFIG_NONE. Dedicated instrument HMIs cover additional specialist
devices; the same physical block can appear in both a dispatch taxonomy and
a dedicated route. Do not sum these counts as disjoint block totals.

### Breadth wave A — shared FieldDevice, completed in PR source

| Family | Visible operator affordance | No-op/runtime policy |
| --- | --- | --- |
| Signal Probe | channel 0..3 | no repeated state write |
| Precision Filter | slew 1..4 | no repeated scheduled tick |
| Redstone Reference Source | output 0..15 + four presets | no repeated neighbour notification |
| Lapis Precision Source | precision 0..100 in steps of 5 | no repeated network solve |
| Digital Regenerator | quality profile index 0..2 | no repeated tick |
| Pressure Regulator | pressure {25,50,75,100} | no repeated pneumatic network solve |
| Pneumatic Relief Valve | relief pressure {25,50,75,100} | no repeated pneumatic network solve |
| Permanent Magnet | field strength 1..15 | no repeated state write |
| Induction Coil | turns 1..4 | no repeated tick |
| Optical Emitter | intensity 0..15 | no repeated optical network solve |
| Optical Channel Filter | target channel 0..15 | no repeated tick |
| Optical Attenuator | loss 0..8 | no repeated tick |
| Mechanical Exciter | frequency 1..15 | no repeated excitation |
| Hydroacoustic Exciter | frequency 1..15 | no repeated excitation |
| Redstone Cable Terminal | explicit Vanilla→Cable / Cable→Vanilla mode label and toggle | existing server mutation |
| Pneumatic Valve | explicit OPEN / CLOSED and toggle | existing server mutation |

The numerical FieldDevice text field uses the block-specific *minimum* and
*maximum*, not the previous global 0..127. Discrete-step legality remains
separately enforced by both HMI validation and existing server implementations.

For the rest of the FieldDevice taxonomy (cables, passive media, sensors,
measurement apertures, converters), the shared Configure panel now carries a
read-only model contract, port/link counts and evidence status instead of an
empty fake tuning section. These read-only panels remain observational.

### Breadth wave B — shared Universal HMI, completed in PR source

Across the existing 23 config-kind families, both primary and secondary
numeric entry bindings now use synchronization-readiness gating, and every
Configure panel shows the parameter snapshot and evidence state. Existing
mode-cycle, toggle, action and fixed/read-only authority rules are preserved.
Numeric conversion of visible engineering values to internal discrete indices
remains in the existing menu/channel rules.

### Later coverage, not yet accepted

1. **Basic signal/transport**: inspect actual in-game screen size, scroll and
   authoritative readback for each source, cable, junction, converter and meter.
2. **Common measurement and control**: verify reference source, analyzer,
   signal filter/conditioner, PWM, PID, light/tank/entity sensing and sample/hold.
3. **Cross-domain**: repeat representative cases for copper, Lapis, Quartz,
   pneumatic, magnetic, optical and vibration.
4. **Remaining registered block inventory**: generate a block-by-block audit
   from the live registry, classify every item as editable, mode/action,
   read-only, dedicated HMI or intentionally without an interaction. There
   must be no silent omissions, no invented controls, and no false '100%'
   claim until runtime/visual review is documented.

## Full-registry closure — 122 core + 8 systems

The original Pioneer `122/122` ledger covers the core registry. The
independent `EngineeringSystemsModule` registers eight additional system
blocks, making **130 currently registered RSE blocks**. The build now runs
`tools/rse_130_block_hmi_coverage.py`, generating an itemized JSON, CSV and
Markdown matrix for all 130 block IDs. Its hard gate distinguishes a block's
own UI entry, inherited `super.useWithoutItem` opener, dedicated operations
UI helper, and deliberate read-only exceptions.

The audit uncovered a genuine missing entry on
`SlimeVibrationConduitBlock`: the normal right-click now opens the shared
LDLib2 FieldDevice inspector; sneak-click provides a non-invasive packet
readback. Copper Wire normal right-click also opens its existing Universal
LDLib2 engineering HMI, while the original sneak+top/bottom shortcut and
sneak+side diagnostics remain.

`EngineeringCompassBlock` remains an explicit exception: its contract is
a passive world-direction marker with a right-click coordinate readout, no
mutable coefficients, and no artificial engineering-port simulator. It is
**not** counted as a game-tested LDLib2 menu.

The source audit verifies **declared entry routes only**, not that each
individual GUI is visually correct or that its parameters have passed
in-world multiplayer validation. The physical GameTest and `runClient`
evidence remain separate from this 130-item static coverage gate.

## 2026-10-27 continued — snapshot truth and typed auxiliary telemetry

PR #229 now enforces snapshot readiness across shared **FieldDevice** and
**Universal** LDLib2 surfaces. Before authoritative synchronization:
configuration readback, driver counts, connection masks, route orientation,
Pioneer process values, measurement rows, system headlines and safety
permissives all show explicit **NOT READY / NOT EVALUATED** rather than a
plausible fabricated zero. Unknown Pioneer quality ordinals are mapped to
`NOT_READY`, distinct from an actual observed `NO_SIGNAL`.

A second breadth issue was corrected: `FieldDeviceMenu.driverCount` is a
shared transport slot with **27 different device types**, not universally a
count of sources. The HMI now labels actual data according to the kind,
including outlet pressure, vent events, gradient Z, remaining pulse ticks,
source-counts, active channels, transition counts, protective hits and
control state. Devices that never populate that slot show
**NOT REPORTED**, not a phantom zero. CI checks the 27-way mapping and
ensures the label cannot regress to a generic source count.

### Actual GameTest diagnostic — not an acceptance pass

A one-off attempt was executed on Actions run
[40468504096](https://github.com/lord-navy-crypto/redstones-engineering/actions/runs/40468504096)
from source commit `2903e083`. The GameTest server **started 421
registered tests**. The collected runtime log contained **44 distinct
`LogTestReporter` failed-test entries**, including signal processor,
Pneumatic, Copper and device/UI contracts. No all-required-tests-passed
summary was emitted before the diagnostic's 8-minute timeout. This means
**the suite did not earn runtime acceptance**, even though the overall CI
job remained green because this step is nonblocking. The temporary PR
trigger was reverted: normal PR CI again skips the Minecraft server.

The server emitted ~222 MB, largely repeated GameTest progress bars.
`tools/rse_bounded_gametest_log.py` now streams that output into a
size-bounded artifact with startup context, important failures and final
tail; a synthetic >8MiB flood fixture in
`tools/rse_bounded_gametest_log_verify.py` protects against relevant
errors being evicted by repetitive progress lines. This new capture tool
was **not retroactively used** for the prior 222 MB GameTest attempt.

Remaining work for actual gameplay acceptance: isolate the registered
GameTest failures, run supported tests to completion, and inspect the
individual LDLib2 screens inside a real Minecraft client. Do not promote
static code-entry coverage into in-world functional certification.

## 2026-10-28 visual operations wave — persistent dashboard and process flow

This wave changes **actual LDLib2 component trees**; it does not only add
textual documentation or new visual mockups.

- **Persistent status rail** on both the lightweight FieldDevice HMI and the
  Universal engineering HMI: four responsive cards stay above the page
  selector while the user navigates. They expose snapshot synchronization,
  health, control capability and source-evidence quality, with **NOT READY**
  before server DataSlots have arrived. Read-only devices show read-only /
  routing rather than a fake editable coefficient.
- **Confirmed-value cards** in both parameter workbenches: legal range and
  server-synchronized engineering value are visually separated from the
  editable input. The current direct-entry binding submits legal values as
  typed; no fictional "Apply" button or speculative success notification is
  added. Users should judge the result by the separately labeled confirmed
  server readback.
- **Active tab breadcrumbs**: shared six-page and standalone instrument
  workspaces display the current page number and label, and reset scroll
  position when navigating. Tiles and controls wrap in narrow windows.
- **Three-stage topology diagrams** on both shared surfaces: RX / INPUT →
  PROCESS MODEL → TX / OUTPUT. These show declared endpoints and server
  synchronization status, **not** invented physical measurements or inferred
  "connected" indicators.
- **CI coverage**: `tools/rse_pr229_visual_workflow_verify.py` is part of
  the existing `RSE Build Verification` suite. It asserts shared responsive
  components, visible navigation/readback/flow and preservation of server
  intent entry points. CI also retains the older snapshot and whole-block
  audit gates.

This is a source/build verified UI improvement, not a screenshot or
Minecraft-client visual acceptance. The existing 421-test runtime diagnostic
remains inconclusive/nonpassing (44 distinct failed-test reports), so it must
be resolved separately before claiming the release fully verified.

## Acceptance gates

1. **Static**: `rse_field_device_pioneer_coverage_verify.py` audits taxonomy
   closure, per-family idempotence guards, synchronized UI and no client solver.
2. **Build**: Java 21 compilation, Gradle tests and clean build, with artifact
   checksums, must pass on the final PR head.
3. **Minecraft GameTest**: selectively execute the registered engineering UI
   cases (including both exciter families and conditioner/calibration cases);
   normal PR CI may skip `runGameTestServer` and must not be reported as a
   runtime pass.
4. **runClient**: at several UI window scales, inspect positive/zero/negative
   parameter entry, legal discrete sets, empty/unsynced state, buttons,
   mode readback and server multiplayer reconciliation.

This file is a rollout checklist, not a release certification. Keep PR #229
open until all required runtime/visual gates for its acceptance scope are met.
