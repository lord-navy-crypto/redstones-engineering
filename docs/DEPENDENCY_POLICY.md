# RSE Dependency Policy

RSE uses external libraries only when the current implementation actually depends on them. Historical plans or future feature ideas are not sufficient reason to make a mod a hard prerequisite.

## Current hard runtime dependencies

### Jade — required client/server dependency

RSE directly imports `snownee.jade.api` in its engineering HUD integration. The Jade provider exposes server-backed Engineering Port, topology, acceptance and captured-run evidence. Until that integration is isolated behind an optional loading boundary, Jade is a real runtime requirement.

Pinned development version: `15.10.6` for Minecraft 1.21.1 NeoForge.

### GeckoLib — required client/server dependency

RSE directly imports GeckoLib from the mechatronics block entity, model and renderer layers. Servo Actuator, Pneumatic Cylinder and Pneumatic Proportional Valve use GeckoLib-backed `.geo.json` and animation resources.

Pinned development version: `4.9.2` for Minecraft 1.21.1 NeoForge.

### LDLib2 — required client/server dependency

RSE uses LDLib2 as the modular engineering-HMI infrastructure. The integration entry point directly imports the LDLib2 plugin API, and new HMI work will use LDLib2 layout, reusable components, synchronized data binding/RPC and UI debugging/editor facilities while keeping simulation authority in RSE server state.

Pinned development version: `2.2.26` for Minecraft 1.21.1 NeoForge.

## Not hard runtime dependencies

### JEI

The current tree contains no JEI plugin, recipe category, subtype interpreter or JEI API import. Recipe/use browsing may be useful in the future, but a future idea is not a current runtime dependency.

### Cloth Config

The current tree contains no Cloth Config API use or Cloth-backed configuration screen. RSE's engineering HMIs are native Minecraft/NeoForge screens.

### Fusion

The current tree contains no Fusion API integration or Fusion/CTM resource contract. Connected-looking engineering assets are not sufficient reason to require Fusion.

## Foundational platform and build-only tooling

End-user/runtime foundation:
- Minecraft 1.21.1
- NeoForge 21.1.249+
- Java 21
- Jade
- GeckoLib
- LDLib2

Development/build-only tooling:
- `net.neoforged.moddev` Gradle plugin
- Parchment mappings
- Foojay toolchain resolver
- Gradle Java / Maven Publish / IDEA plugins

Build-only tooling must never be documented as an end-user mod prerequisite.

## Dependency rules

1. A hard dependency must have direct current code/resource evidence.
2. Planned or optional functionality does not justify `type="required"`.
3. Remove unused dependency repositories, version properties and metadata together.
4. Do not shade external mods into the RSE jar.
5. CI must compile and build using only current hard dependencies.
6. Historical manifests may record older policy decisions, but historical policy must not force unused dependencies into the current artifact.
7. If a future JEI, Cloth Config or Fusion integration is implemented, add it back only with executable code/resource evidence and a regression test proving the integration exists.
