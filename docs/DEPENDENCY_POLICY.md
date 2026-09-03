# RSE Dependency Policy

RSE uses external libraries when they materially improve engineering quality, interoperability, testing, rendering, or player understanding. Dependencies are not avoided for ideological reasons, but they are classified by whether the core simulation truly requires them.

## Tier 0 — core / built in

These are part of the RSE core architecture and require no third-party mod beyond NeoForge:

- NeoForge sided/block capabilities for interoperability and future port abstraction;
- NeoForge GameTest infrastructure for in-game topology and behavior tests;
- NeoForge data generation;
- NeoForge `ModConfigSpec` configuration;
- NeoForge registry and networking facilities;
- RSE transmission domains, port diagnostics, measurement, control, and runtime models.

RSE must remain launchable with **NeoForge alone**.

## Tier 1 — optional ecosystem integrations, available in the dev runtime

### JEI

Purpose:

- recipe browsing;
- recipe/use discovery for the growing engineering progression;
- future RSE engineering-category information.

Policy:

- compile against JEI public APIs with `compileOnly`;
- load the complete JEI mod through `localRuntime` for development/testing;
- never publish JEI as a required transitive dependency of RSE.

Pinned development version for Minecraft 1.21.1 NeoForge: `19.27.0.336`.

### Jade

Purpose:

- future optional engineering HUD for domain, port direction, measurement, network, and state diagnostics;
- make legacy and advanced equipment easier to test without requiring a large RSE GUI.

Policy:

- compile against the Jade artifact with `compileOnly`;
- load Jade through `localRuntime` for development/testing;
- keep all Jade-specific behavior optional;
- RSE core code must not require Jade classes during ordinary startup.

Pinned development artifact: Jade `15.10.6+neoforge` for Minecraft 1.21.1 (`eYz2YBGT` on Modrinth Maven).

## Tier 2 — add only when a feature uses them

Candidates include:

- **GeckoLib** for servo, cylinder, valve, or other articulated engineering animations;
- **Ponder** for interactive engineering tutorials;
- **Fusion** or another rendering/resource solution for connected visual materials where it does not determine simulation topology.

These should not be added merely because they are popular. A PR adding one must include the feature that benefits from it and a clear fallback/compatibility decision.

## Dependency safety rules

1. Do not shade or bundle third-party mod jars into the RSE jar unless a future dependency explicitly supports and requires that model.
2. Do not change JEI/Jade from optional to required without a documented architecture reason.
3. Core simulation, world loading, wiring, measurement, PID, pneumatics, and operations logic must not depend on optional integration mods.
4. Use `localRuntime` for optional mods needed during `runClient` development tests.
5. Pin versions in `gradle.properties` so CI and local development resolve the same artifacts.
6. Prefer creator/official Maven repositories when available; use a narrowly filtered repository for Modrinth artifacts.
7. Any new required dependency must be reviewed for Minecraft/NeoForge version support, maintenance state, license, and release impact.

## Current architecture

```text
Minecraft 1.21.1
       ↓
NeoForge 21.1.249
       ↓
RSE core
 ├─ native capability / GameTest foundation
 ├─ engineering simulation
 ├─ port/domain diagnostics
 └─ optional integration detection
       │
       ├─ JEI (optional dev/runtime integration)
       └─ Jade (optional dev/runtime integration)
```

This keeps the engineering platform interoperable without turning every quality-of-life integration into a mandatory installation requirement.
