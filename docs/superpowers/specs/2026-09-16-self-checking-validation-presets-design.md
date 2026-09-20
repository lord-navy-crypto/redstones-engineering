# Self-Checking Validation Presets Design

## Goal
Upgrade the existing `01_basic` and `02_signal` validation presets from layout-only NBT structures into repeatable self-checking test benches with deterministic block states, explicit stimulus/expected behavior, and visible WAIT/PASS/FAIL indicators.

## Scope
- Keep the existing preset generator and `.nbt` delivery model.
- Add block-state properties to generated structure palettes (`facing`, `power`, `mode`, etc.) so tests spawn in known configurations.
- Add a self-test catalog for `01_basic` and `02_signal` only.
- Add `/rsevalidation selftest place <id>`, `/rsevalidation selftest check <id>`, and `/rsevalidation selftest list`.
- A placed self-test records its origin server-side so `check` can evaluate the real world state later.
- Every self-test bench contains a three-lamp status panel: yellow WAIT, green PASS, red FAIL. Exactly one lamp is powered after an evaluation.
- PASS must be derived from authoritative RSE/world evidence; layout presence alone is never PASS.
- Tests that need settling/sampling return WAIT until enough runtime evidence exists.

## Architecture
### Generated structures
Python owns deterministic structure generation. Palette entries support both a block id and optional string block-state properties. Self-test NBT lives under `validation/selftest/01_basic/...` and `validation/selftest/02_signal/...`.

### Runtime evaluation
Java owns runtime truth. `RseValidationSelfTestService` places templates, remembers their origins in world SavedData, reads actual block/runtime state, produces `WAIT`, `PASS`, or `FAIL`, and updates the status panel. The evaluator uses existing RSE APIs such as `EngineeringPortSnapshot`, `SignalAnalyzerBlock.uiSnapshot`, and device block states rather than duplicating device logic.

### Status panel
Each structure reserves three fixed status positions. Yellow/green/red redstone lamps sit above matching concrete labels. Evaluation powers exactly one lamp with a redstone block under/behind the selected lamp and removes power from the other two.

## Initial test series
### 01_basic
- `reference_source`: configured reference source emits the expected 0..15 value on its configured face.
- `signal_probe`: non-invasive probe reads a powered test node and reports VALID with the expected value.
- `signal_analyzer_tap`: analyzer TAP reports VALID raw input and maintains sampling evidence without acting as an inline conductor.
- `signal_analyzer_inline`: analyzer INLINE passes the expected value through to its output.
- `analog_indicator`: indicator reports VALID and displays the expected input value.
- `signal_conditioner_gain`: conditioner GAIN x2 maps 6 -> 12 without saturation.
- `directional_io`: correct face transmits expected value; wrong face does not create a valid input.
- `instrument_bus`: probe plus instrument cable exposes at least one valid instrument channel.

### 02_signal
- `conditioner_saturation`: GAIN x2 with input 10 clamps to 15 and reports SATURATED; that expected saturation is PASS.
- `precision_filter`: configured precision source/filter/meter path produces valid downstream evidence.
- `sample_hold`: waits until sampled, then verifies held output remains coherent with its configured trigger/sample state.
- `edge_detector`: waits for edge evidence, then verifies edge output behavior.
- `pulse_shaper`: waits for a detected pulse, then verifies output pulse evidence.
- `pwm_control`: waits for a sufficient runtime window, then checks bounded toggling/duty evidence.
- `noise_vs_filter`: waits for sufficient samples and passes only when filtered peak-to-peak is lower than raw peak-to-peak.
- `quantizer_scaler`: verifies redstone->lapis->redstone round-trip within the quantization tolerance.

## Failure behavior
- Missing expected block at a test coordinate: FAIL.
- `NO_SIGNAL`, `STALE`, invalid topology, wrong direction, unexpected saturation, or value outside tolerance: FAIL unless the test explicitly expects that condition.
- Insufficient runtime samples/settling time: WAIT.
- No evaluator is allowed to mutate the DUT into the expected state; mutation is limited to test placement, stimulus setup, and the status panel.

## Non-goals
- No general teaching/lesson engine.
- No CI GameTestServer expansion.
- No fake PASS based on block presence.
- No rewrite of existing signal/device physics.
