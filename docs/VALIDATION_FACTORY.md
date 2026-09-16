# RSE Validation Factory

The **RSE Validation Factory** is a local/manual Minecraft validation environment for the current Operations + AMR production chain. It is designed to complement static verification and focused GameTests without requiring the full GameTest server in normal CI.

## What it validates

The v1 factory covers:

`Material release -> persistent queue -> maintenance-aware dispatch -> workcell/output -> Industrial Buffer -> AMR transport -> delivery/readback`

The factory uses real RSE blocks and the real `redstoneengineering:engineering_mobile_robot` entity. Building the factory creates only baseline test fixtures. It does **not** fabricate completed jobs or delivered AMR missions.

## 1. Update your local branch

On macOS:

```bash
cd /Users/jason/Desktop/redstones-engineering

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

java -version

git fetch origin
git switch codex/persistent-plant-runtime-validation-world
git pull --ff-only origin codex/persistent-plant-runtime-validation-world
```

Java should report version 21.

## 2. Generate the reusable structure templates

Preferred command:

```bash
python3 tools/rse_validation_factory.py generate
```

Equivalent compatibility command:

```bash
python3 tools/rse_validation_factory.py --generate-structures
```

This creates:

```text
src/generated/resources/data/redstoneengineering/structure/validation/material_release.nbt
src/generated/resources/data/redstoneengineering/structure/validation/queue_dispatch.nbt
src/generated/resources/data/redstoneengineering/structure/validation/maintenance_hold.nbt
src/generated/resources/data/redstoneengineering/structure/validation/quality_output.nbt
src/generated/resources/data/redstoneengineering/structure/validation/amr_lane.nbt
src/generated/resources/data/redstoneengineering/structure/validation/operations_monitor.nbt
src/generated/resources/data/redstoneengineering/structure/validation/full_factory.nbt
```

Gradle also exposes:

```bash
./gradlew generateValidationStructures
```

Normal resource processing depends on this task, so `runClient`/`build` regenerate the templates when needed.

## 3. Run the lightweight checks

```bash
python3 -m unittest tools.test_rse_validation_factory -v
python3 tools/rse_validation_factory_verify.py
python3 tools/rse_operations_amr_world_verify.py

./gradlew clean compileJava
./gradlew test
./gradlew build
```

Do **not** use the full `runGameTestServer` as the first test. The Validation Factory and focused in-game tests are intended to avoid the huge all-test run while you are iterating.

## 4. Open Minecraft

```bash
./gradlew runClient
```

Create a Creative, cheats-enabled world named exactly:

```text
RSE Validation Factory
```

Stand where you want the northwest/reference corner of the factory and run:

```text
/rsevalidation build
```

Then inspect the baseline without mutating it:

```text
/rsevalidation status
```

The status command reports the validation input/output buffers, persistent queue, READY/MAINTENANCE_DUE resource evidence, and the tagged real AMR state.

## 5. Reset tests

Reset the full validation-owned baseline:

```text
/rsevalidation reset all
```

Reset one physical station:

```text
/rsevalidation reset material_release
/rsevalidation reset queue_dispatch
/rsevalidation reset maintenance_hold
/rsevalidation reset quality_output
/rsevalidation reset amr_lane
/rsevalidation reset operations_monitor
```

Reset fails closed if a validation queue or buffer still contains WIP. It does not clear unrelated player factories or directly wipe `OperationPlantSavedData`.

## 6. Place individual structures manually

The generated templates can also be placed independently:

```text
/place template redstoneengineering:validation/material_release
/place template redstoneengineering:validation/queue_dispatch
/place template redstoneengineering:validation/maintenance_hold
/place template redstoneengineering:validation/quality_output
/place template redstoneengineering:validation/amr_lane
/place template redstoneengineering:validation/operations_monitor
/place template redstoneengineering:validation/full_factory
```

These are physical layouts. Operations state is still created through `/rsevalidation build` or the production world facades; an `.nbt` structure does not fake persistent plant history.

## 7. Persistence acceptance

After exercising a station:

1. Run `/rsevalidation status` and note the queue/buffer/maintenance/AMR state.
2. Save and quit the world normally.
3. Start the client again with `./gradlew runClient`.
4. Re-enter `RSE Validation Factory`.
5. Run `/rsevalidation status` again.

Persistent Operations evidence should survive the real save/reload boundary. Robot entity state is evaluated through the real entity save/runtime path rather than a validation-only cache.

## 8. Package the real world as a ZIP

Quit the world first so Minecraft has flushed it to disk. Then run:

```bash
python3 tools/rse_validation_factory.py package \
  --world "run/saves/RSE Validation Factory"
```

Or use the compatibility form:

```bash
python3 tools/rse_validation_factory.py \
  --package-world "run/saves/RSE Validation Factory"
```

Or Gradle:

```bash
./gradlew packageValidationWorld
```

Output:

```text
build/validation/RSE-Validation-Factory.zip
```

The packager requires a real Minecraft-created `level.dat`; it refuses to fabricate a fake save. It excludes transient files such as `session.lock`, `logs/`, `crash-reports/`, and `.DS_Store`.

To inspect the archive:

```bash
unzip -l build/validation/RSE-Validation-Factory.zip | head -n 40
```

The archive root is always:

```text
RSE Validation Factory/
```

## Recommended daily loop

For normal development, this is enough:

```bash
cd /Users/jason/Desktop/redstones-engineering
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

git pull --ff-only origin codex/persistent-plant-runtime-validation-world
python3 tools/rse_validation_factory.py generate
./gradlew compileJava
./gradlew runClient
```

Then in Minecraft:

```text
/rsevalidation build
/rsevalidation status
```

Use focused GameTests only when you want automated regression coverage for one subsystem; keep the large all-GameTest server out of the ordinary feedback loop.
