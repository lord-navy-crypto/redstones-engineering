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
- **Setpoint-versus-measurement distinction**: shared FieldDevice Overview
  labels the actual synchronized tuning slot as `SETPOINT` and presents its
  configuration without requiring unrelated physical port quality. The other
  measured/derived slots retain evidence qualification. All 14 exact-entry
  kinds use their own actual primary/secondary/tertiary server DataSlot; an
  intentional configured zero is not confused with missing input.
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


## 2026-10-28 continued repair — source-traced GameTest faults and truthful manual gate

This is a **code and verification** continuation, not a claim that the 421-case
NeoForge runtime suite has passed.

- Shared Universal LDLib2 no longer displays PWM inversion as NORMAL, an
  alarm action as already acknowledged, or the Evidence page as synchronized
  before the initial authoritative server DataSlot snapshot. The molecular
  receiver's configured gain comes from its synchronized `SENSITIVITY`
  configuration using `MolecularCloudReceiverBlock.gainFor`, not a possibly
  missing process sample. The shared snapshot-truth verifier checks this.
- The original Oct 27 GameTest archive contains **44 distinct
  `LogTestReporter` failures**. First-pass *triage labels*, not proven root
  causes, group as signal/control/UI 11; sensors/domain transport 9;
  communications 8; Copper/electrical 7; pneumatic/pressure 6;
  topology/test harness 1; other 2.
- One concrete test harness root cause was inspected:
  `topologyRoleProjectionUsesFormalPortContract` instantiated unregistered
  `TestPortBlock` instances after NeoForge froze registries; the log reports
  `Registry is already frozen`. `EngineeringDeviceMenu` now provides a pure
  formal-port classifier overload, while the real `BlockState` adapter still
  supplies junction and directional-domain traits. The GameTest validates
  source/sink/series/passive/junction roles from port lists without constructing
  blocks. A static gate prevents reintroducing the old test objects.
- The **manually dispatched** GameTest task now fails visibly on timeout,
  nonzero server/collector exit, recorded failures, chained-neighbor-update
  cutoff or missing all-required-tests-passed marker. It is still skipped on
  ordinary PR/push builds. It produces a bounded log and an additional
  `gametest-triage.md` artifact with de-duplicated failed tests grouped for
  diagnosis. Synthetic fixture tests cover duplicate reports, conflicting
  success+failure markers and missing conclusions.
- The legacy game test run pre-dates these changes. **No new in-world
  runtime success is asserted.** Build/static checks prove source integrity,
  not that previous individual GameTest failures are resolved.

Next: execute a fresh explicitly dispatched runtime suite and verify the
previous registry-freeze case is gone, then isolate remaining baseline and
model/timing failures. A manual Minecraft `runClient` visual and interaction
review is still required.

## 2026-10-28 operator-guidance UI and narrower viewport wave

Implemented in the **real LDLib2 UI component trees** for the shared
FieldDevice and Universal engineering HMIs, not in a detached mockup.

- **Context-sensitive Overview guidance**: the first panel now explains
  the next *inspection* step. It uses the same synchronized menu state as
  the rest of the HMI; there is no independent physics, client-side hazard
  verdict or unsynchronized default value.
- **FieldDevice triage order**: (1) wait for server sync, (2) inspect
  topology if invalid, (3) inspect invalid/missing process evidence,
  (4) explicitly distinguish missing/unreported port quality from
  `VALID`, and only then suggest supported Configure operations or a
  read-only inspection.
- **Universal guidance**: before synchronization, show only an explicit
  wait-for-sync instruction. Afterward, select exact input, mode cycle,
  explicit action or read-only inspection based on the actual config kind.
  The text is **navigation advice**, never proof that a sample is healthy
  or a server request succeeded.
- **Reading key**: a synchronized **SETPOINT** is stored configuration;
  it does *not* imply a measured signal. **SERVER CONFIRMED** is an
  authoritative configuration readback, not an acknowledgement that
  the physical process is healthy. **NOT REPORTED** is not the number
  zero and must not be converted into `VALID`.
- **Responsive width**: the shared LDLib2 tab scroller and Universal
  viewport now prefer wrapping at 320px instead of forcing a 440px
  content floor. Two-axis scrolling remains available for genuinely
  smaller Minecraft windows or long engineering text.
- **Regression**: the existing
  `tools/rse_pr229_visual_workflow_verify.py` CI gate now checks
  presence of the real guidance component, ordered snapshot/quality
  guards, persistent server-bound confirmation and 320px layout floor.
  It does not replace manual client visual acceptance.

### Manual visual acceptance still required

Use a **real NeoForge 1.21.1 runClient** and at least one multiplayer
session before merging:

1. Confirm that the 4 persistent status tiles remain readable on
   320px, 620px and 740px scaled layouts; tabs and guidance text should
   wrap and the viewport should scroll without hiding controls.
2. Open a shared FieldDevice *before* the first menu snapshot:
   the guide must say to await server data, without displaying a
   plausible live measurement or encouraging a parameter change.
3. Exercise valid/invalid topology and measurement quality separately.
   The next-step guide must point to Topology or Evidence; a
   synchronized configured zero must remain a valid setpoint.
4. Test a direct-entry family, a mode-cycle family, a server-action
   family and an observer-only block. Each must suggest the
   corresponding Configure or inspection workflow.
5. Enter allowed lower/upper bounds and a disallowed step; verify
   exact server readback and no fake success display or spurious
   network action. Also test a signed coefficient using its dedicated
   screen and compare with the shared semantics.
6. Switch pages repeatedly and test multiplayer: no stale
   quality badge, unqualified measurement or locally fabricated
   "success" state should appear.

Ordinary PR CI and the latest installation-kit build are **not**
Minecraft GameTest acceptance. The previously documented 44 failed-test
reports still require a fresh, deliberately dispatched diagnostic.

## 2026-10-28 instrument visualization: legends and first-action guidance

This update changes the **actual LDLib2 screens** for the Oscilloscope,
Logic Analyzer and PID workbench rather than adding screenshots or a parallel
physics model.

- **Color-and-text plot legends:** a shared responsive
  `RsePlotLegendElement` draws labeled swatches for the same colors
  used by live capture traces. Oscilloscope identifies CH-A/CH-B, trigger
  threshold, and both cursors. Logic Analyzer identifies four probe
  channels and both cursors. PID identifies setpoint (SP), process value
  (PV), and output (OUT). Labels remain readable without color perception.
  The legend calculates row wraps from its allocated LDLib2 bounds and
  never reads the world or changes server behavior.
- **Oscilloscope first action:** no samples → choose/arm trigger;
  incomplete/duplicated network → inspect Network; absent channel
  coverage or cursor slots → inspect probe/cursor evidence; alias
  status not GOOD for both channels → inspect Sampling before frequency
  interpretation. GOOD is the actual server ordinal 3, not the default 0.
- **Logic Analyzer first action:** wait for samples, resolve incomplete
  network scans and duplicate assignments, inspect valid channel
  coverage, and require cursor points to exist on the *same channel*.
  Empty digital sample slots are neither HIGH nor LOW.
- **PID first action:** distinguish inhibited, missing commissioning inputs,
  manual mode, no retained AUTO solve, missing trend, and a detected
  but unready pneumatic plant. Retained trends and terms do not
  certify present AUTO feedback or plant acceptance.
- **Regression:** ordinary Build Verification now executes
  `tools/rse_pr229_instrument_ui_verify.py`, checking actual chart
  palette-to-legend agreement and the existence of the supported
  server-evidence navigation branches. Previously established
  compile/unit/static/clean-build/ZIP gates remain in place.

### Real-client acceptance still outstanding

1. Use `runClient` with 320px/640px/690px scaled workspaces:
   check text/swatches wrap, chart/legend alignment, scroll and
   long descriptions on GUI Scale 2, 3 and 4.
2. Test scope two channels, cursor slots without captured data, sampled
   valid zero, alias GOOD/MARGINAL/RISK/NOT_READY, and duplicate probes.
3. Test logic all four digital lanes, empty/invalid slots, short
   captures, trigger edge and valid-cursor channel matching.
4. Test PID AUTO/MANUAL/INHIBITED, missing PV, retained history with no
   current signal, and a detected but unready pneumatic plant.
5. Repeat with a multiplayer server: data, visibility and readbacks
   must continue to originate on the server. No automatic change of
   settings should occur when the help/legend is rendered.

**No in-world GameTest pass or screenshot acceptance is claimed here.**

## 2026-10-28 instrument axes and empty-capture clarity

This changes the real server-backed LDLib2 plot widgets. Existing control
routes, formulas, physics, sampling and test-kit packaging are unchanged.

- The shared render-only `EngineeringPlot` layer now offers labeled
  **0..15 signal-level / sample-slot axes**, **digital lane labels** and an
  explicit **NO CAPTURED SAMPLES / NO RETAINED PID SAMPLES** indication.
  It does not invent values, fill missing samples, read the world, or send
  parameter updates.
- Oscilloscope: the original A/B traces and trigger/cursor markers now sit
  inside a reserved plot rectangle with **Y = Redstone level 0..15** and
  **X = display slots 0..15**. The configured sample period in ticks belongs
  to the server and remains visible on the Sampling page. X values are
  display positions, **not absolute timestamps**.
- Logic Analyzer: all four digital lanes are visually identified A/B/C/D
  and have a synchronized **0..15 slot index** below the chart. HIGH and LOW
  remain distinct from missing data, and the existing same-channel cursor
  validity requirement is unchanged.
- PID: retained SP/PV/OUT traces now use **Y = 0..15** and **X = retained
  0..31 display slots**, with the existing 2-tick sampling contract explained
  next to the chart. A retained trace must not be presented as evidence
  that current commissioning PV is available.
- Rendering safeguards: plots reserve actual pixel gutters for labels and
  skip trace drawing on no-capture/no-history state. The plot layer operates
  only on the allocated UIElement rectangle.
- The ordinary PR static verifier now asserts these source contracts, real
  labels/units, missing-capture states, and plot boundaries. Existing
  Java/Gradle tests and clean build still gate artifact generation.

**Runtime acceptance remains open:** test in Minecraft `runClient` at
multiple GUI scales, with no sample, a valid zero sample, sparse/gapped
capture, PID history with invalid current PV, and both cursor markers.
Graph axes are manually verified only after screenshots and interactions,
not merely because CI compiles.

## 2026-10-28 hard requirement: all registered blocks

**Scope is the complete current registry, not merely the recently improved
oscilloscope, PID, and logic analyzer screens.** The registry consists of
**122 core blocks + 8 EngineeringSystemsModule blocks = 130 blocks.**

### Source-level rollout accomplished

- The shared LDLib2 \`deviceContext(EngineeringDeviceMenu)\` widget now appears
  in **all 23 registered block-facing UI families**. The actual title remains
  the block-specific title; the new panel reports the **block location,
  operational health, formal port role, quality of reported evidence, and
  RX/TX faces**. These values come from the existing authoritative menu
  snapshot and do not change the block physics or controls.
- Before synchronization, HEALTH/ROLE/EVIDENCE/ROUTE remain **NOT READY**;
  client-side initial slots must never become proof of a valid signal.
  The strip does not assume that an unreported port implies a broken device.
- This covers the shared FieldDevice (78-kind taxonomy), shared Universal
  (23 config-kind families), dedicated scope/PID/logic/signal instruments,
  Copper, Lapis, Quartz, radio, digital, optical, pneumatic, magnetic,
  Amethyst, reliability, workcell, operations and industrial buffer menus.
- All **130** registry records are retained in
  \`.rse-audit/rse-130-block-hmi-coverage.{json,csv,md}\`. A second CI
  gate \`tools/rse_130_block_visual_rollout_verify.py\` now binds this
  130-block inventory to **every registered LDLib2 menu/screen/source
  component**, instead of counting a few example blocks. It emits
  \`.rse-audit/rse-130-block-visual-rollout.{json,md}\`.
- **Intentional exception, not an omission:** \`engineering_compass\`
  has a server-side non-mutating right-click world-direction/coordinate
  readout, not a fabricated LDLib2 settings screen. The audit permits
  this exception only and fails if additional non-GUI blocks appear.
- The new CI gate fails if a newly registered block lacks an interaction
  declaration, if a registered UI family loses its common context header,
  or if a menu/screen stops referencing its expected LDLib2 component.
  Additional device-specific controls still depend on their real server
  block type and cannot be mass-invented.

### Acceptance limitations and mandatory in-game checklist

A registered HMI entry plus a verified menu family is **source-level
coverage only**, not proof that each of the 129 HMI blocks opens correctly
in Minecraft. Do not call this a fully game-tested 130/130 release.

1. Execute a creative-world walkthrough across all registered block IDs.
   Record actual GUI entry, device identity, evidence/quality semantics,
   available controls, and read-only behavior.
2. Include 320px/620px/740px window layouts; confirm the new common
   context never occludes tabs, parameter fields, or critical controls.
3. Repeat shared FieldDevice and Universal families with at least one
   sample of each concrete kind; test direct-entry bounds, zero,
   unsigned/signed values, presets and toggles where supported.
4. Confirm specialized instruments and the eight systems blocks,
   especially observer-only operations and industrial buffer screens.
5. Run the nonblocking/manual GameTest suite explicitly and address
   reported failures. Java/Gradle/CI source verification does not
   constitute multiplayer or runClient screenshot acceptance.

**Carry this all-registry requirement forward on every UI change.**

## 2026-10-28 full-registry actionable status & compact inspector
## 2026-10-28 full-registry actionable status & compact inspector

This change is **applied centrally to every one of the 23 block-facing LDLib2 HMI
families**, rather than just the oscilloscope, logic analyzer and PID examples.
Together with the 130-block interaction gate this is the current **all-registry
source coverage**, not an in-game walkthrough pass.

- The compact header now reports the **actual expected block's localized name**,
  block coordinates, operating health, aggregate port evidence and a live
  **NEXT CHECK** suggestion derived from synchronized menu status only. Unlike
  a generic success badge, it does not claim every internal reading is valid.
- The server-state advice handles unsynchronized health, explicit FAULT,
  topology failure, domain mismatch, evidence fault, protective operation,
  degraded state, stale data, saturation, not-ready sampling, no signal,
  and unobserved port evidence with different explanations. Missing evidence
  and NO SIGNAL **do not automatically mean the block is broken**.
- A **Show evidence ▼ / Hide evidence ▲** toggle reveals the detailed role,
  evidence and formal RX/TX faces plus a reading key. It is **client-local UI
  state**; expanding/collapsing sends no server action, modifies no parameters,
  and leaves original device-specific controls intact.
- This is mounted inside the shared `deviceContext(EngineeringDeviceMenu)`
  used by every block-facing UI family (and therefore all registered
  menu-backed blocks). The compass keeps its independent, read-only
  world-datum coordinate behavior.
- The strengthened `tools/rse_130_block_visual_rollout_verify.py`
  hard gate requires the actual context-to-menu linkage across the 23 families,
  the status prioritization, safe wording, collapsed initial state and
  client-only toggle. It emits per-block source coverage and per-family
  capabilities in JSON and Markdown.

### Runtime acceptance remains separate

The audit does **not** prove that all 129 GUI blocks were opened in Minecraft,
that every screen scale is readable or that multiplayer sync/GameTests passed.
The next in-game checklist should verify no new header occludes menus at high
GUI scale, opening/closing detailed evidence does not send packets, and the
health/quality strings follow real server sync after a state change.

## 2026-10-28 all-block role and quality interpretation

**Mandatory scope:** the full current RSE registry, **130 blocks**
(122 core, 8 EngineeringSystemsModule). These changes reach all 23
registered block-facing LDLib2 families by using the existing
\`RseLdUiComponents.deviceContext(EngineeringDeviceMenu)\` call.
The passive \`engineering_compass\` intentionally retains its
non-mutating server-side world-direction/position readout.

The collapsed header still shows authoritative name/location, health,
aggregate evidence and prioritized next action without covering small
workbenches. **Show evidence** is still a client-only display toggle.
Expanding it now adds two deeper, read-only explanations:

- **PORT CONTRACT:** different meanings for SOURCE, SINK, OBSERVER,
  PASSIVE, explicit JUNCTION and CONTROLLED devices. An observer can
  correctly omit TX, a source can omit RX and a sink can omit TX.
  Formally declared receive/transmit faces are **not** tested electrical
  or physical connectivity; junctions must not invent auto-bridging.
- **QUALITY MEANING:** eight supported port quality codes have explicit
  meaning and non-overclaiming warnings. In particular,
  VALID is not a full device acceptance, NO SIGNAL can be expected,
  STALE is not a current sample, SATURATED does not extrapolate
  measurements, and UNOBSERVED does not establish a fault.

Both projections depend only on already synchronized integer DataSlots,
never new world reads, physics solves or hidden control actions.
Health is kept distinct from port evidence; an intended protective
safety state can coexist with valid measurements.

The all-registry CI hard gate now verifies the complete role/quality
branch coverage and that each of the 23 HMI families mounts the
server-backed shared panel. It also generates
\`.rse-audit/rse-130-block-manual-ui-checklist.csv\`: 130 distinct
per-block rows covering actual opening, identity, health/evidence,
port contract, expanded panel, original controls, GUI scales,
multiplayer and GameTests. All execution columns start **NOT_TESTED**
(or NOT_APPLICABLE where the passive compass has no HMI).
The checklist is a *test plan*, not a fabricated successful test report.

**Remaining mandatory real-client work:** test all 129 HMI entries
inside Minecraft (plus the compass actionbar), record screenshot
and scale observations, verify actual server readback/multiplayer
behavior and run the manual NeoForge GameTests. Green static, Java,
unit and packaged build results are not those runtime observations.



## 2026-10-30 fusion branch — first-round visual baseline retained

The integration branch `fusion/rse-oct28-first-round-full-forwardport-20261030`
uses commit `d6ee5db74b92750f2c71cce50231d88c4177934d` as its visual baseline. Later engineering changes are
forward-ported into that tree rather than replacing its visual host/navigation
architecture.

- All subsequent block, block-entity, domain-network, sampling, optical,
  pneumatic, quartz, safety, reliability and evidence-model changes are carried
  forward with their GameTests, source audits, documentation and CI gates.
- Later read-only HMI content is retained additively: the shared device/evidence
  context, server-confirmed parameter scales, operator guidance, plot legends,
  sample-slot axes and explicit empty-capture labels. The shared
  `tabbedWorkspace` method remains identical to the first-round baseline.
- The original screen sizing, tab navigation, and scroll implementation remain
  unchanged. The workspace keeps automatic two-axis scrolling.
- Diagnostic report/snapshot copy shortcuts are added as keyboard actions only;
  the first-round tablet and console layout methods are unchanged.
- The later crisp-backdrop screen replacement and responsive button-row redesign
  are intentionally not imported. The later `minHeight(0)`/flex viewport rewrite
  and injected page-end markers are also excluded.

Build and source-level verification do not certify actual Minecraft client
screenshots, multiplayer synchronization or GameTest runtime acceptance. Those
remain explicit manual acceptance gates.
