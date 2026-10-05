# RSE Validation Factory Design

## Purpose

Build a repeatable local validation environment for the Operations + AMR production chain so a developer can test the mod in a real Minecraft client without relying on the full `runGameTestServer` suite. The environment must use real registered RSE blocks, the real `engineering_mobile_robot` entity, and the authoritative Operations world facades rather than fake success states.

## Deliverables

1. Reusable validation structure templates for the major Operations/AMR stations.
2. An in-game validation factory builder/reset command that places those templates and creates legitimate initial runtime fixtures through existing world-owned APIs.
3. A local Python asset tool that generates the binary `.nbt` templates deterministically and can package an already-created `RSE Validation Factory` save into `build/validation/RSE-Validation-Factory.zip`.
4. Static verification covering structure names, block/entity IDs, command registration, fixture boundaries, and packaging behavior.
5. A short operator guide with exact terminal commands and in-game commands.

## Scope

Validation Factory v1 covers only the current Operations + AMR line:

`Material release -> persistent queue -> maintenance-aware dispatch -> workcell/output evidence -> Industrial Buffer -> AMR transport -> delivered receipt -> Operations readback`

It does not attempt to become a museum for every RSE electrical, optical, pneumatic, communications, or metrology block. Those can be separate validation wings later.

## Station Layout

The factory is split into independent, resettable stations:

- `validation/material_release`: input Industrial Buffer, queue marker, control signage, pass/hold lamps.
- `validation/queue_dispatch`: queue/workcell area with dispatch observation point.
- `validation/maintenance_hold`: READY and MAINTENANCE_DUE resource positions for a visible dispatch hold comparison.
- `validation/quality_output`: workcell/output buffer area for quality-cleared output and receipt validation.
- `validation/amr_lane`: source dock area, explicit navigation lane, destination dock area, and AMR spawn position.
- `validation/operations_monitor`: Operations Monitor / Workcell Controller observation area.
- `validation/full_factory`: the complete physical shell tying the stations together.

The individual structures are reusable with `/place template redstoneengineering:validation/<name>` and the full builder command places them at fixed offsets from an origin.

## Structure Asset Strategy

Binary `.nbt` files are generated, not hand-edited. `tools/rse_validation_factory.py` owns a small declarative structure description and a minimal deterministic NBT writer using only the Python standard library. Generated templates are written to:

`src/generated/resources/data/redstoneengineering/structure/validation/*.nbt`

`src/generated/resources` is already part of the main resource source set, so generated templates are included by normal Gradle builds.

The generator must be idempotent and deterministic: the same source tree produces byte-identical structure files except where the NBT format itself requires ordering, which the writer controls.

## World Builder Boundary

A new server-side validation module registers `/rsevalidation` commands. The primary commands are:

- `/rsevalidation build` - build the full factory around the command source position.
- `/rsevalidation reset all` - rebuild all physical stations and reset only validation-owned runtime fixture identities.
- `/rsevalidation reset <station>` - replace one station without touching unrelated plant state.
- `/rsevalidation status` - summarize the known validation queue, buffer, maintenance, logistics, and robot state without mutating them.

The command is a test-fixture owner, not a second Operations engine. It may create starting buffers/queues and explicit maintenance observations through `OperationIndustrialBufferState`, `OperationQueueWorldState`, and `OperationMaintenanceWorldState`. It must not write `OperationPlantSavedData` maps directly when an authoritative facade exists, must not synthesize COMPLETED/DELIVERED history, and must not force the robot into a success state.

## Validation Fixture Identities

All factory-owned runtime identities use a reserved prefix so reset operations cannot touch ordinary player factories:

- queue IDs: `validation:*`
- buffer IDs: `validation:*`
- resource IDs: `validation:*`
- process IDs: `validation:*`

Reset is fail-closed. If a validation fixture cannot be cleanly removed because it contains active WIP or another invariant blocks removal, the command reports the reason instead of deleting arbitrary plant data.

## AMR Rules

The validation lane uses the real `redstoneengineering:engineering_mobile_robot` entity. The factory builder may spawn an idle robot at the source staging point, but transport progression must use the production docking/loading/route/unloading APIs. The validation layer must never call `setDeltaMovement` to simulate completion, directly set private robot state, or record DELIVERED without accepted unload/receipt evidence.

Once `OperationRobotTransportWorldState` is implemented, validation transport setup must call that facade for prepared/started/delivered mission state rather than duplicating transport persistence.

## Pass / Hold / Fault Visualization

Physical labels explain each station. Vanilla lamps or other simple visible indicators may be included as observer aids, but their color/state is not an authoritative test result unless driven from the same server-side evidence shown by the status command. The primary truth remains the Operations persistence/readback state and the real robot state.

## Persistence Test

The operator flow explicitly includes save/quit/re-enter. The required persistence check is:

1. Create or build `RSE Validation Factory`.
2. Run at least one queue/maintenance/AMR scenario.
3. Save and quit.
4. Re-open the same save.
5. Run `/rsevalidation status` and inspect the Operations UI.
6. Confirm queue assignments, maintenance evidence, transport runtime state, and delivery history expected to be durable are still present.

No validation code re-seeds completed history automatically on world load.

## World ZIP Delivery

A fully valid Minecraft save contains version-specific `level.dat`, chunks, and mod runtime data, so the repository does not fabricate an unbooted fake save. Instead, after the user creates/boots a real world named `RSE Validation Factory` and runs `/rsevalidation build`, the Python tool packages that real save:

`python3 tools/rse_validation_factory.py --package-world "run/saves/RSE Validation Factory"`

Output:

`build/validation/RSE-Validation-Factory.zip`

The ZIP therefore represents a Minecraft-created save, not a guessed `level.dat`.

## Gradle Entry Point

Add `generateValidationStructures` as an `Exec` task that calls the Python generator and make `processResources` depend on it. Normal Java compilation does not need to launch Minecraft. A convenience `packageValidationWorld` task may call the Python packager only when `run/saves/RSE Validation Factory` exists; otherwise it fails with a clear instruction.

## Verification

`tools/rse_validation_factory_verify.py` statically verifies:

- expected template names are declared;
- generated-resource target path is correct;
- only registered RSE block/entity IDs are referenced;
- `/rsevalidation` command class is present and registered;
- fixture setup uses Operations world facades rather than direct plant-map mutation;
- reset is limited to `validation:` identities;
- AMR setup references `engineering_mobile_robot` and does not force transport completion;
- Gradle includes `generateValidationStructures`;
- packaging writes `RSE-Validation-Factory.zip` and excludes transient lock/log files.

A small local GameTest validates that the full-factory template can be loaded and that fixture creation produces the expected buffer/queue/maintenance baseline. This GameTest remains locally runnable and is not added as a mandatory full Minecraft CI run.

## Non-goals

- No automatic GitHub CI launch of the full Minecraft GameTest server.
- No second SavedData authority.
- No client/UI mutation authority.
- No fabricated AMR success state.
- No world scanning to infer queue/resource bindings.
- No destructive reset of non-validation player state.
- No attempt to cover every RSE subsystem in v1.

## Acceptance Criteria

The feature is ready when a developer can run the structure generator, start `./gradlew runClient`, create/open a Creative world, run `/rsevalidation build`, independently reset stations, observe real Operations state, exercise the real AMR lane, save/reload for persistence testing, and package the real world save into a ZIP. Static verification, Java compilation, Gradle tests, and the Validation Factory local GameTest must compile/pass before the feature is called complete.
