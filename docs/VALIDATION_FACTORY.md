# RSE Validation Factory

The **RSE Validation Factory** is a local/manual Minecraft validation environment for the current Operations + AMR production chain. It complements static verification and focused GameTests without requiring the full GameTest server in normal CI.

The repository also contains a **Self-Checking Validation Series** for signal/instrumentation work. These are small reusable `.nbt` benches with real configured RSE devices plus a physical WAIT/PASS/FAIL lamp panel.

## What it validates

The factory covers:

`Material release -> persistent queue -> maintenance-aware dispatch -> workcell/output -> Industrial Buffer -> AMR transport -> delivery/readback`

The self-test series currently covers 16 focused benches in `01_basic` and `02_signal`, including reference sources, probes, analyzers, indicator readback, conditioning, directionality, instrument bus, saturation, slew filtering, sample/hold, edge detection, pulse shaping, PWM, noise filtering and redstone/lapis quantization.

The factory uses real RSE blocks and the real `redstoneengineering:engineering_mobile_robot` entity. Building fixtures does **not** fabricate completed jobs or delivered AMR missions. Self-test PASS verdicts are derived from actual world/device/runtime evidence.

## 1. Update your local branch

On macOS:

```bash
cd /Users/jason/Desktop/redstones-engineering

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

git fetch origin
git switch codex/persistent-plant-runtime-validation-world
git pull --ff-only origin codex/persistent-plant-runtime-validation-world
```

Java should report version 21.

## 2. Generate reusable structures

Generate everything:

```bash
python3 tools/rse_validation_factory.py generate
```

Generate only the 16 self-checking benches:

```bash
python3 tools/rse_validation_factory.py generate-selftests
```

Generate/package the older free-form preset pack:

```bash
python3 tools/rse_validation_factory.py generate-presets
python3 tools/rse_validation_factory.py package-presets
```

Self-test structures are written under:

```text
src/generated/resources/data/redstoneengineering/structure/validation/selftest/01_basic/
src/generated/resources/data/redstoneengineering/structure/validation/selftest/02_signal/
```

Their machine-readable index is:

```text
build/validation/selftest-index.txt
```

Gradle resource processing runs the validation generator automatically, so `runClient` and normal builds receive generated templates.

## 3. Run the lightweight checks

```bash
python3 -m unittest tools.test_rse_validation_factory -v
python3 tools/rse_validation_factory_verify.py
python3 tools/rse_validation_selftest_verify.py
python3 tools/rse_operations_amr_world_verify.py

./gradlew clean compileJava
./gradlew test
./gradlew build
```

Do **not** use the full `runGameTestServer` as the first test. The Validation Factory and focused in-game tests are intended to avoid the huge all-test run while iterating.

## 4. Open Minecraft

```bash
./gradlew runClient
```

Create a Creative, cheats-enabled world. For the large Operations factory, stand where you want the reference corner and run:

```text
/rsevalidation build
/rsevalidation status
```

The status command reports the validation input/output buffers, persistent queue, READY/MAINTENANCE_DUE resource evidence, and the tagged real AMR state.

## 5. Use the Self-Checking Test Series

List available tests:

```text
/rsevalidation selftest list
```

Place one at your current position:

```text
/rsevalidation selftest place 01_basic/reference_source
```

Then evaluate it:

```text
/rsevalidation selftest check 01_basic/reference_source
```

Every bench has the same physical status convention:

- **Yellow / WAIT** — the bench is settling, collecting samples, or has just injected a required stimulus.
- **Green / PASS** — the measured authoritative behavior matches the test contract.
- **Red / FAIL** — the expected device/evidence is missing, invalid, stale after settling, misrouted, or outside the test contract.

Exactly one status channel is powered after an evaluation.

Some dynamic benches intentionally need repeated checks. For example:

```text
/rsevalidation selftest place 02_signal/edge_detector
/rsevalidation selftest check 02_signal/edge_detector
```

The first eligible check may inject the controlled rising-edge stimulus and remain yellow. After a few ticks, run the same `check` again; the detector must contain real recorded edge evidence before the panel can turn green.

`noise_vs_filter` deliberately accumulates a small validation-only sample window. Run `check` several times while the deterministic noise source changes. PASS requires the observed filtered peak-to-peak window to be lower than the raw window; a single lucky sample cannot produce PASS.

Representative tests:

```text
/rsevalidation selftest place 01_basic/signal_probe
/rsevalidation selftest check 01_basic/signal_probe

/rsevalidation selftest place 01_basic/signal_conditioner_gain
/rsevalidation selftest check 01_basic/signal_conditioner_gain

/rsevalidation selftest place 02_signal/conditioner_saturation
/rsevalidation selftest check 02_signal/conditioner_saturation

/rsevalidation selftest place 02_signal/pwm_control
/rsevalidation selftest check 02_signal/pwm_control

/rsevalidation selftest place 02_signal/quantizer_scaler
/rsevalidation selftest check 02_signal/quantizer_scaler
```

A deliberate engineering fault can still produce **PASS** when the test contract expects it. For example, the conditioner saturation test expects input `10` with gain `x2` to clamp at `15` and report `SATURATED`; correctly detecting that condition is a green PASS.

The evaluator may mutate only test-owned stimulus and status-panel blocks. It does not force the DUT into a passing state and does not write Operations production authority.

## 6. Place raw structures manually

The generated templates can also be placed without the self-test command:

```text
/place template redstoneengineering:validation/selftest/01_basic/reference_source
/place template redstoneengineering:validation/selftest/01_basic/signal_probe
/place template redstoneengineering:validation/selftest/02_signal/pwm_control
```

This places the configured physical bench, but `/place template` does not register the test origin in validation SavedData. Therefore use `/rsevalidation selftest place ...` when you want `/rsevalidation selftest check ...` and automatic status lamps.

The larger Operations structures remain available as:

```text
/place template redstoneengineering:validation/material_release
/place template redstoneengineering:validation/queue_dispatch
/place template redstoneengineering:validation/maintenance_hold
/place template redstoneengineering:validation/quality_output
/place template redstoneengineering:validation/amr_lane
/place template redstoneengineering:validation/operations_monitor
/place template redstoneengineering:validation/full_factory
```

## 7. Reset Operations tests

Reset the full validation-owned Operations baseline:

```text
/rsevalidation reset all
```

Or one station:

```text
/rsevalidation reset material_release
/rsevalidation reset queue_dispatch
/rsevalidation reset maintenance_hold
/rsevalidation reset quality_output
/rsevalidation reset amr_lane
/rsevalidation reset operations_monitor
```

Reset fails closed if a validation queue or buffer still contains WIP. It does not clear unrelated player factories or directly wipe `OperationPlantSavedData`.

## 8. Persistence acceptance

After exercising a factory station or placing self-tests:

1. Note `/rsevalidation status` or the current self-test panel.
2. Save and quit normally.
3. Start again with `./gradlew runClient`.
4. Re-enter the same world.
5. Re-run the relevant status/check command.

Operations evidence uses the real persistent plant path. Self-test placement origins and their bounded validation-only observation windows also persist, while DUT physics remains owned by the real devices.

## 9. Package the real world as a ZIP

Quit the world first so Minecraft has flushed it to disk. Then run:

```bash
python3 tools/rse_validation_factory.py package \
  --world "run/saves/RSE Validation Factory"
```

Or:

```bash
./gradlew packageValidationWorld
```

Output:

```text
build/validation/RSE-Validation-Factory.zip
```

The packager requires a real Minecraft-created `level.dat`; it refuses to fabricate a fake save. It excludes transient files such as `session.lock`, `logs/`, `crash-reports/`, and `.DS_Store`.

## Recommended daily loop

```bash
cd /Users/jason/Desktop/redstones-engineering
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

git pull --ff-only origin codex/persistent-plant-runtime-validation-world
python3 tools/rse_validation_factory.py generate
./gradlew compileJava
./gradlew runClient
```

Then either use the large factory:

```text
/rsevalidation build
/rsevalidation status
```

or focused self-tests:

```text
/rsevalidation selftest list
/rsevalidation selftest place 01_basic/reference_source
/rsevalidation selftest check 01_basic/reference_source
```

Keep the large all-GameTest server out of the ordinary feedback loop; use focused manual GameTests when you specifically need an automated world regression.
