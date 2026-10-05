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

Future waves should prioritize remaining EngineeringScreen families that still lack explicit model
or variable-role presentation. The target is broad consistency with specialization, not identical
screens.
