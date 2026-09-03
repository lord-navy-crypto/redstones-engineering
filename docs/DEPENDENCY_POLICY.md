# RSE Dependency Policy

RSE deliberately uses mature external libraries when they make engineering development faster, more powerful, and easier to maintain. Beginning with Alpha 1.0.9, the project no longer treats the primary ecosystem stack as optional compatibility.

## Required platform

The following five mods/libraries are part of the RSE platform contract.

### JEI — required client dependency

Purpose:
- recipe and use browsing;
- engineering progression discovery;
- future RSE recipe categories, machine information, and transfer helpers.

Pinned development version: `19.27.0.336` for Minecraft 1.21.1 NeoForge.

### Jade — required client/server dependency

Purpose:
- engineering HUD;
- port direction and transmission-domain diagnostics;
- measurement, machine state, network, pneumatic, and control-loop information;
- server-backed data providers when richer diagnostics are needed.

Pinned development version: `15.10.6` for Minecraft 1.21.1 NeoForge.

### GeckoLib — required client/server dependency

Purpose:
- articulated servo, cylinder, valve, relay, mechanism, and machine animation;
- reusable animation controllers instead of custom one-off renderer state machines;
- animated blocks/items/entities where engineering motion matters.

Pinned development version: `4.9.2` for Minecraft 1.21.1 NeoForge.

### Cloth Config — required client dependency

Purpose:
- consistent configuration screens;
- easier tuning of instrumentation, visualization, accessibility, diagnostics, and client engineering preferences;
- avoid hand-building every configuration GUI.

Pinned development version: `15.0.140` for Minecraft 1.21.1 NeoForge.

### Fusion — required client dependency

Purpose:
- connected textures and advanced model/resource behavior;
- visually continuous cables, panels, casings, pipes, buses, and engineering materials;
- reduce custom rendering code for topology-aware visual presentation.

Pinned mod version: `1.3.14` for Minecraft 1.21.1 NeoForge. The reproducible Modrinth Maven artifact is `1.3.14-neoforge-mc1.21.1`.

## Native NeoForge remains foundational

RSE still uses NeoForge facilities directly for capabilities, GameTest, data generation, registries, networking, lifecycle, and core simulation. External dependencies extend the platform; they do not replace the engineering model.

## Hard-dependency rules

1. The five platform dependencies must be declared in Gradle and in generated NeoForge metadata.
2. NeoForge metadata uses `type="required"`; missing required dependencies must stop startup on the relevant side.
3. JEI, Cloth Config, and Fusion are required on the client side.
4. Jade and GeckoLib are required on both client and server because RSE plans to use server-side diagnostics/data and shared animation-aware machine architecture.
5. Versions are pinned in `gradle.properties` for reproducible development and CI.
6. CI must resolve all five artifacts, compile Java, run tests, and produce a clean verified build.
7. Do not shade these mods into the RSE jar. Users/modpacks install the required dependencies normally, keeping licensing, updates, and loader behavior explicit.
8. New dependencies may be added when they materially simplify implementation or unlock a major engineering feature; avoiding dependencies is not a design goal.

## Current architecture

```text
Minecraft 1.21.1
       ↓
NeoForge 21.1.249
       ↓
RSE 1.0.9-alpha
 ├─ JEI          [required client]
 ├─ Jade         [required both]
 ├─ GeckoLib     [required both]
 ├─ Cloth Config [required client]
 └─ Fusion       [required client]
       ↓
measurement → conditioning → sampling → control → actuation → optimization
```
