# RSE Pioneer Showcase Standard

RSE uses a small set of **Pioneer / Showcase blocks** as reference instruments. They are not
special exceptions and they are not the only blocks allowed to be deep. Their purpose is to prove
an engineering-HMI pattern first, then drive a broad rollout across the rest of the mod.

## Pioneer references

### Lapis Low-Pass Filter — model transparency pioneer
Reference qualities:
- governing equation before prose
- explicit MEASURED / SOLVER / ADJUSTABLE / PROFILE / DERIVED roles
- live substitution with server-synchronized values
- derived tau / cutoff meaning instead of a magic alpha knob
- quality/evidence separated from numeric zero

### Oscilloscope — experiment and sampling pioneer
Reference qualities:
- explicit sample interval, sample rate and Nyquist model
- server-owned capture evidence
- baseline -> change -> reacquire -> candidate -> compare workflow
- PASS / MARGINAL / FAIL as an explicitly scoped RSE acceptance rule
- warnings where evidence cannot establish source ground truth (for example aliasing)

### PID Controller — control and acceptance-evidence pioneer
Reference qualities:
- bounded authoritative controls
- live controller state and saturation/protection evidence
- retained acceptance captures and trends
- clear separation between current runtime state and retained evidence

## Rollout rule

A normal engineering block should inherit the *reasoning pattern*, not copy the same screen:
1. MODEL — expose the real implemented transfer/model/constraint.
2. VARIABLES — identify adjustable, measured, solver-owned, profile-owned and derived values.
3. LIVE STATE — show current server-authoritative values and physical route.
4. EVIDENCE — show quality, coverage, saturation, topology, uncertainty or commissioning state.
5. ACTION / EXPERIMENT — only when the backend supports a meaningful intervention or comparison.

Do not invent knobs, formulas, history, uncertainty, experiments or hidden physics merely to make a
screen look advanced. A one-parameter block should remain a one-parameter block and deepen its
evidence/model explanation instead.

## Shared HMI expectations

EngineeringScreen-based devices should use the responsive workspace, vertical scrolling and local
horizontal scrolling foundation. Formula/model pages should prefer shared helpers such as
formulaCard, variableRole, evidenceRow and wrappedText instead of returning to cramped 320x270
chest-style UI or ellipsis-heavy prose.

The client is presentation/control intent only. Physics, topology resolution, measurements,
acceptance verdicts and retained experiment evidence remain server-authoritative.

## Current rollout waves

Wave 1:
- Signal Conditioner
- Quartz Timing
- Media Conversion
- Range Sensor

Wave 2:
- Digital Communication
- Pneumatic Systems
- Optical Systems
- Magnetic Systems

Wave 3:
- Amethyst Systems
- Reliability Systems
- Radio Link
- Signal Analyzer
- Industrial Buffer

Wave 3 deliberately broadens the pioneer pattern beyond pure transfer-function devices:
- resonance uses discrete-index model transparency without inventing Hz
- reliability exposes safe-state logic, thresholds, tolerance and maintenance actions
- radio exposes decode margin and availability without recomputing propagation on the client
- metrology exposes calibration and retained statistics while preserving TAP/INLINE semantics
- operations exposes WIP/capacity relationships while keeping lot/job identity out of analog Redstone

Wave 4:
- Logic Analyzer
- Copper Circuit Meter
- Operations Monitor
- Workcell Controller
- Universal Field Device

Wave 4 extends the pioneer pattern into timing, commissioning, plant-state and authority-contract HMIs:
- logic analysis exposes thresholding, sample cadence, cursor timing and retained capture evidence
- Copper metrology exposes V / R_eq / I / P while retaining observer-only commissioning semantics
- Operations monitoring exposes trustworthy RUN/QUEUE evidence, queue pressure and server-classified plant state
- workcell control exposes explicit admission gating, finite-capacity evidence and HOLD/PERMIT reasons
- the universal HMI exposes per-device authority contracts instead of pretending every field device shares one physics model

Wave 5:
- Enhanced Field Device shared inspector
- Signal Processor family

Wave 5 shifts from one-screen-per-device rollout to shared leverage:
- FIELD_DEVICE is registered to EnhancedFieldDeviceScreen, so fallback devices inherit one Pioneer contract layer
- the shared inspector selects a truthful contract by role: passive medium, observer, converter, processor, communications, reliability, pneumatic, magnetic or optical
- the 79-kind FieldDeviceMenu taxonomy remains supported, while dedicated Pioneer screens still take precedence for devices with richer specialized HMIs
- Precision Filter, Edge Detector and Pulse Shaper expose their actual server equations, runtime evidence and observer-neutral chronology

Shared rollout must improve breadth without flattening semantics. A common inspector may standardize
MODEL / ROLE / EVIDENCE / TOPOLOGY / AUTHORITY, but it must not invent one universal physics model.

Wave 6:
- Mechanical vibration transport
- Hydroacoustic packet transport
- Sculk event-code bridge
- Phonon / thermal pulse transport

Wave 6 adds domain-specific model transparency inside the shared Enhanced Field Device inspector:
- mechanical Slime and Honey paths distinguish per-hop attenuation from retained-packet time decay
- mechanical receivers expose amplitude-to-Redstone conversion and retained decay
- hydroacoustic tubes expose medium-dependent RSE hop loss (water / milk-model / lava) while explicitly remaining a discrete game-domain model
- hydroacoustic sources/receivers expose packet amplitude/frequency indices without claiming continuous real-world propagation
- Sculk interface exposes event-code pass-through plus retained event/transition evidence
- phonon/thermal devices expose finite-bandwidth event packets, hop loss and receiver retention without pretending to solve continuous heat transfer
- Slime, Honey, Hydro and Phonon transport blocks are classified as six-way physical media in the shared Ports view

The shared inspector must label these as **RSE discrete models**. Model indices must not be presented
as physical Hz or SI heat/acoustic quantities unless the server model actually provides that mapping.

Wave 7:
- Range Sensor
- Signal Conditioner
- Quartz Timing
- Media Conversion

Wave 7 closes verification debt in the original Wave-1 instrument chain:
- Range Sensor response equations are cross-checked against the server scan model and swept across every supported range/response mode
- Signal Conditioner gain/offset/clamp/threshold/deadband transfers are cross-checked against server behavior and swept across their bounded parameter domains
- Quartz timing exposes the server's 4096-tick divider saturation instead of reporting an unclamped ideal product, preventing false period-mismatch diagnostics
- Media Conversion preserves exact Redstone-derived codes while explicitly measuring Lapis-to-Redstone quantization loss
- all four families are CI-protected as Pioneer contracts rather than relying only on historical UI polish

Wave 8:
- FieldDevice taxonomy coverage closure
- Redstone Cable Terminal / Reference Source / Lapis Precision Source
- Redstone / Lapis / Quartz source-ownership evidence
- Instrument / Data / Optical / Amethyst passive-medium integrity

Wave 8 closes the shared-inspector coverage gap:
- all 78 real FieldDevice kinds (plus KIND_UNKNOWN) are checked automatically against dedicated routes or explicit Enhanced contracts
- PortQuality is synchronized as a first-class server state instead of collapsing STALE / TOPOLOGY_ERROR / NO_SIGNAL into a generic zero-percent display
- Redstone cable/terminal views retain sourceCount and valid-zero semantics
- Lapis and Quartz traces expose source conflict vs truncated-scan evidence
- Instrument buses expose authoritative channel/topology quality and deterministic interference confidence without double-scanning the network
- Data Bus, Optical fiber/junction and Amethyst dust retain their existing conflict/stale classifications
- Lapis Precision Source receives real server-side ±5 controls for its 0..100 value and a synchronized output face

The coverage verifier must fail when a new real FieldDevice KIND is added without either a dedicated
route or an explicit shared Pioneer classification. Generic fallback is no longer considered
sufficient evidence of engineering coverage.

Wave 9:
- PID closed-loop Commissioning Trial

Wave 9 moves Pioneer quality from block-by-block evidence into an explicit system experiment:
- BASELINE and CANDIDATE are deliberate operator captures, not merely the latest two history rows
- captures are accepted only when the authoritative commissioning state has complete PASS / MARGINAL / FAIL dynamic evidence
- the frozen comparison reports score, settling, overshoot, saturation and topology-issue deltas
- the established ClosedLoopCommissioning robustness thresholds remain the single source of truth
- starting a new baseline clears the previous candidate; the transient store is bounded to 256 controllers per level
- trial capture/clear actions never reset controller runtime, tuning, plant state, topology, or ordinary acceptance history

The trial follows BASELINE → intervention/change → wait for trustworthy response → CANDIDATE → compare.
It is an evidence workflow, not a second control solver.

Wave 10:
- Autonomous Mobile Robot mission commissioning
- Diagnostic Tablet entity evidence

Wave 10 extends the explicit experiment pattern from PID control into world-running robotics:
- AMR mission telemetry records only existing lifecycle boundaries: duration, motion/stationary time, obstacle waits, degraded entries, safe stops, faults, route rejects, dock/material holds, waypoint count and worst localization
- counters are observer-only and never participate in motion, planning, docking or safety decisions
- mission runs are frozen only after COMPLETE or terminal FAULT evidence
- Shift+right-clicking an AMR with the Diagnostic Tablet captures BASELINE then CANDIDATE; a completed pair rolls over to a new baseline
- baseline/candidate must belong to the same robot, and different start/final-target paths are INCOMPARABLE rather than being ranked by speed
- safety/fault/localization evidence outranks duration in the deterministic comparison
- ordinary right-click captures a live AMR observer snapshot without assigning a new target
- the Diagnostic Tablet is now a responsive, vertically scrollable retained-evidence workspace for both block and robot evidence

The robotics trial is deliberately not a real-world ISO compliance claim. ISO 3691-4 and VDA 5050
inform the separation of safety, mission/status, route and localization evidence, while RSE reports
only the bounded evidence actually implemented by its Minecraft runtime.

Future waves should prioritize remaining EngineeringScreen families that still lack explicit model
or variable-role presentation. The target is broad consistency with specialization, not identical
screens.
