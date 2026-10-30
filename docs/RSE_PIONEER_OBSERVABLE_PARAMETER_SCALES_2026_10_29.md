# Pioneer Observable UI — Parameter Position Diagrams (2026-10-29)

This incremental visual change is based on PR #229's current shared LDLib2
workbenches. It does **not** claim to finish the 130-block gameplay acceptance.

## Implemented source changes

- Shared `EnhancedFieldDeviceLdUi`: the 14 exact-entry field-device kinds
  display a bounded horizontal **server-confirmed setpoint position** next to
  the existing formula, legal range, precise editor and server readback.
  Discrete markers are generated from the same `validDirectValue` gate as
  the existing editor.
- `UniversalFieldDeviceLdUi`: all 14 direct numeric primary config kinds and
  all 3 secondary config kinds display the same readback-only visual scale.
  Legal markers use the visible engineering units, including variable-spaced
  configurations (molecular gain, RC response, quartz timing, PWM period).
- **Lapis Precision Range**: the secondary server snapshot stores a range
  index (0–3). The operator-facing readback now converts it to the accepted
  engineering values **8, 16, 32, 64 blocks**. Invalid index does not
  become a plausible range measurement.
- `ParameterScaleElement`: no client physics/world read, no server mutation,
  no pending text-field value, no forecast, no fake sample history. Before
  snapshot synchronization it is expressly NOT READY; invalid/off-scale
  values are not quietly clamped into a plausible plotted reading.
- `tools/rse_observable_parameter_scale_verify.py`: CI source guard checks
  authority/readiness, complete primary-kind marking, and range readback.

## Do not conflate three different graphs

1. **Parameter position** (this patch): a single configured server setting
   plotted against legal engineering choices. This is **not** measured process
   performance or a temporal curve.
2. **Live observed trace** (existing dedicated instrument widgets):
   captured, server-synchronized samples. Missing history stays blank.
3. **Model/response curve** (future scope): permitted only when backed by
   an actual authoritative model and a separately labeled prediction
   protocol. Never derive invented output samples from a client's knob.

## Next UI work

- Keep the shared formula + symbol + units + exact editor + server readback
  + quality + next-action framework. Do not add editable controls to passive
  transport, safety monitoring, or fixed-physics devices.
- Provide a domain-aware plot only when its own menu exposes authoritative
  samples and their quality: Copper V/I/P, Lapis sampling, Quartz interval,
  pneumatic pressure/flow, optical intensity/channel, magnetic field,
  thermal temperature/time, and mechanical/phonon response.
- Every chart must label its x/y units, source, sample age/window, quality,
  invalid intervals and any server-determined thresholds. No invented
  wall-clock timestamps or SI interpretations for discrete RSE proxies.
- Preserve the existing 130 registered blocks, NBT compatibility,
  0..15 Vanilla Redstone boundaries, ports, directions, medium identity,
  routing, delay, safety interlocks and authoritative recomputation.

## Acceptance still pending

- The static checker and build job must run on the integrated PR head.
- `./gradlew compileJava test clean build` must pass with Java 21.
- Explicit engineering UI GameTests and representative `runClient`
  screenshots at small and standard GUI scales must be checked separately.
- Client/server reconciliation must be tested with valid/invalid/discrete
  edits, first-sync NOT READY, and unusual stale/fault evidence.
- This branch is based on PR #229 head `666b7da30f7e84647161bb959ebe120aaff03606`;
  it should be reviewed/merged **into PR #229's feature branch**, not
  independently treated as a release from the old `main` head.
