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

Wave 11:
- Operations → AMR end-to-end material-flow acceptance
- stable cross-domain correlation identities

Wave 11 closes the evidence gap between dispatch and completed robot motion:
- OperationTransportBinding owns one stable mission/output/job correlation key plus derived payload/load/unload evidence ids
- OperationsRobotTransportBridge remains dispatch-only; it still does not select robots, plan routes, admit docks or command material movement
- the end-to-end evaluator reuses existing dispatch, dock and material-transfer assessments instead of introducing a second robotics solver
- acceptance correlates completed Operations output → demand/binding → Robotics mission → source dock/load → secured payload → target dock/unload → finished AMR telemetry
- identity, quantity, source/target and terminal safety evidence fail closed before completion can be accepted
- completed flows with recovered safe-stop/degraded/route/dock/material holds are MARGINAL instead of silently PASS
- obstacle waits alone remain visible telemetry but are not treated as an engineering fault
- this is an internal RSE evidence contract, not an ISO/VDA compliance claim

VDA 5050 3.0 and ISO 21423 reinforce the architectural separation between mission/status
interoperability and robot-local execution; ISO 21423 explicitly excludes AMR safety requirements.
RSE therefore keeps Operations correlation/acceptance separate from Robotics safety and motion authority.

Wave 12:
- Signal Analyzer internal-reference calibration trial
- per-sample measurement-presence evidence in the retained 16-sample window

Wave 12 turns the Analyzer's rolling measurement window into a repeatable metrology experiment:
- an explicit internal reference x_ref in 0..15 is configured independently of the physical signal path
- calibration offset remains display-only; INLINE output continues to reproduce the raw measured value
- each retained sample now records whether a real measurement target was present, so valid numeric zero is no longer confused with an empty aperture
- BASELINE and CANDIDATE require a fresh 16/16 VALID measurement window
- fixed comparison requires the same reference, TAP/INLINE mode and measurement face
- absolute error to the internal reference is ranked first, followed by clipping, span and mean-step stability evidence
- no external traceability claim and no invented absolute pass/fail tolerance are attached to the result
- trial storage remains bounded to one baseline/candidate pair per analyzer and 256 analyzers per level

NIST measurement-process guidance motivates comparison against a stated reference plus repeatability/stability evidence,
but RSE reports only its internal game-domain reference and does not claim SI/NIST traceability.

Wave 13:
- Temperature Sensor
- Engineering Light Sensor
- Tank Level Sensor
- Entity Density Sensor
- Lapis Precision Meter
- Lapis Precision Range Sensor
- Analog Indicator

Wave 13 starts the final 35-block Pioneer completion campaign with a coherent measurement/observer batch:
- all seven use the responsive Universal Field Device workspace as a shared measurement Pioneer surface rather than seven copied desktop-style screens
- each block exposes its actual implemented model first, explicit MEASURED / SOLVER / PROFILE / ADJUSTABLE / DERIVED / EVIDENCE roles where they really exist, synchronized live values and server-owned quality
- Temperature Sensor exposes six-face thermal coverage and cached-vs-target temperature; incomplete coverage retains the last trustworthy cached value
- Light / Tank / Entity Density preserve raw physical evidence separately from conditioned Redstone output and keep valid numeric zero distinct from missing/stale evidence
- Lapis Precision Meter remains observer-only; Lapis Precision Range exposes its real bounded range/profile controls without moving the range scan to the client
- Analog Indicator exposes source quality independently of its 0..15 display and retains stale readout instead of manufacturing a new value
- no experiment tab is invented for these live observers because none of the seven retains a baseline/candidate experiment backend

Pioneer completion ledger after Wave 13: **87 + 7 = 94 / 122 registered blocks processed; 28 remain.**

Remaining 28 for Waves 14-17:
- Calibration Module, Sample & Hold, PWM Controller
- Copper Wire, Copper Voltage Source, Copper Resistive Load, Iron Core, Thermal Mass
- Lapis Noise Source, Quartz Lab Oscillator, Quartz Phase Delay
- Copper Series Resistor, Copper Capacitor, Copper Fuse
- Thermal Heater, Thermal Radiator, Thermal Calorimeter
- Copper Cable Junction
- Lapis Temperature / Magnetic / Optical / Voltage Transducers
- Quartz Triggered Lapis Sampler
- Soul Soil Conduit, Soul Sand Reservoir, Soul Flux Injector, Soul Flux Meter, Molecular Cloud Receiver

Wave 14:
- Calibration Module
- Sample & Hold
- PWM Controller
- Lapis Temperature Transducer
- Lapis Magnetic Transducer
- Lapis Optical Transducer
- Lapis Voltage Transducer

Wave 14 advances a coherent signal-conditioning / sampled-data / transduction chain through the shared Universal Field Device workspace:
- Calibration Module exposes OBSERVED and REFERENCE as separate measured inputs, the profile-owned transfer, calibrated output, signed residual bias and measurement sample count
- Sample & Hold exposes its actual edge-triggered hold law, held output, trigger/reset levels, capture count and sample age without fabricating retained waveform history
- PWM Controller exposes the implemented discrete duty law N_on=round((u/15)·T), realized duty-cycle quantization error, server phase, period and the independent physical INHIBIT path
- all four Lapis transducers preserve their distinct physical input domains while sharing the real SensorModel profile quantities: sample period, resolution step, deterministic noise amplitude and sample latency
- Temperature, magnetic, optical and copper-voltage normalization formulas remain device-specific; the client only renders synchronized server snapshots and never becomes a second sensor/physics solver
- profile changes remain real server actions and invalidate/reacquire transducer output through the existing runtime path; numeric zero remains independent from PortQuality
- no baseline/candidate experiment is invented because these seven devices expose live transformation/conditioning state rather than a retained commissioning-trial backend

Pioneer completion ledger after Wave 14: **94 + 7 = 101 / 122 registered blocks processed; 21 remain.**

Remaining 21 for Waves 15-17:
- Copper Wire, Copper Voltage Source, Copper Resistive Load, Iron Core, Thermal Mass
- Lapis Noise Source, Quartz Lab Oscillator, Quartz Phase Delay
- Copper Series Resistor, Copper Capacitor, Copper Fuse
- Thermal Heater, Thermal Radiator, Thermal Calorimeter
- Copper Cable Junction, Quartz Triggered Lapis Sampler
- Soul Soil Conduit, Soul Sand Reservoir, Soul Flux Injector, Soul Flux Meter, Molecular Cloud Receiver

Future waves should prioritize the remaining ledger in coherent seven-block families. The target is broad
consistency with specialization, not identical screens, and the remaining count must move 21 → 14 → 7 → 0.

Wave 15:
- Copper Wire
- Copper Voltage Source
- Copper Resistive Load
- Copper Series Resistor
- Copper Capacitor
- Copper Fuse
- Copper Cable Junction

Wave 15 completes the core Copper electrical path as one Pioneer family:
- Copper Wire and Copper Cable Junction expose resolved node voltage, active-driver count, physical connected-face count and PortQuality; planar wire remains distinct from explicit branch topology
- Copper Voltage Source exposes the real 0..15 server-owned V_set control and six OUTPUT faces
- Copper Resistive Load exposes I=V/R and P=VI from one legitimate terminal feed; INPUT-only loads never back-drive another sink and multi-feed ambiguity remains TOPOLOGY_ERROR
- Copper Series Resistor exposes V_out=V_in·R_load/(R_s+R_load) and I=V_in/(R_s+R_load), with R_load/current retained from the authoritative server tick rather than recomputed by opening the HMI
- Copper Capacitor exposes its discrete charge law, charge %, V_out and the implemented tau profile 2/4/8/16 ticks without claiming SI capacitance
- Copper Fuse exposes retained R_eq/current evidence, rating, trip latch and protected output; reset never declares READY until a complete server protection re-evaluation
- configurable Copper blocks use real server-bound controls with stated ranges/defaults, while wire/junction remain evidence/topology instruments with no decorative knobs
- sneak+top/bottom opens the Pioneer workspace on legacy click-configured blocks so established normal interaction remains available
- no Copper physical transfer law or vanilla-facing Redstone behavior is changed by this rollout; the work adds retained diagnostics, truthful controls and formula-first presentation

Pioneer completion ledger after Wave 15: **101 + 7 = 108 / 122 registered blocks processed; 14 remain.**

Remaining 14 for Waves 16-17:
- Iron Core, Thermal Mass
- Lapis Noise Source, Quartz Lab Oscillator, Quartz Phase Delay
- Thermal Heater, Thermal Radiator, Thermal Calorimeter
- Quartz Triggered Lapis Sampler
- Soul Soil Conduit, Soul Sand Reservoir, Soul Flux Injector, Soul Flux Meter, Molecular Cloud Receiver

Future waves should prioritize the remaining ledger in coherent seven-block families. The target is broad
consistency with specialization, not identical screens, and the remaining count must move 14 → 7 → 0.

Wave 16:
- Lapis Noise Source
- Quartz Lab Oscillator
- Quartz Phase Delay
- Quartz Triggered Lapis Sampler
- Soul Flux Injector
- Soul Flux Meter
- Molecular Cloud Receiver

Wave 16 advances the active-source / timing / sampled-observer family through the shared Universal Field Device workspace:
- Lapis Noise Source exposes its real deterministic bounded source law, baseline μ, noise bound, current sample, 4-tick cadence and initialization evidence; zero remains a valid generated sample
- Quartz Lab Oscillator separates nominal 2/4/8/16/32-tick period and bounded jitter configuration from the last realized server scheduling interval
- Quartz Phase Delay exposes its 1..16-tick authoritative latency parameter, pending countdown, one-tick output pulse and edge-history readiness; reconnect-high never fabricates a new rising edge
- Quartz Triggered Lapis Sampler preserves distinct BACK Lapis input / LEFT Quartz trigger / FRONT held-output roles and captures only on a real valid Quartz rising edge
- Soul Flux Injector is explicitly Minecraft-fictional: a valid UP redstone command u injects packet=4u into loaded adjacent Soul nodes on its five non-UP faces; absent nodes are not virtual outputs
- Soul Flux Meter keeps Soul node presence/quality separate from charge value and maps valid Q_s to floor(15·Q_s/100) redstone readout
- Molecular Cloud Receiver exposes the implemented radius-8 aperture, gain profile {6,9,12,16}, raw/filtered/peak evidence and one-step-per-5-tick filter without claiming real atmospheric sensing physics
- all configurable controls remain server-bound; opening the HMI never creates a clock edge, captures a Lapis sample, injects Soul Flux or advances molecular filter history
- no new physical model is introduced in this wave; formula-first UI documents and synchronizes the already-implemented game-domain models

Pioneer completion ledger after Wave 16: **108 + 7 = 115 / 122 registered blocks processed; 7 remain.**

Remaining 7 for Wave 17:
- Iron Core
- Thermal Mass
- Thermal Heater
- Thermal Radiator
- Thermal Calorimeter
- Soul Soil Conduit
- Soul Sand Reservoir

The final wave should close this exact seven-block material / storage / thermal family and move the ledger 115 → 122 with zero remaining.
