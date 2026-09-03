# RSE Alpha 1.0.3 Pneumatic Compile Hotfix

Fixes the compile failure caused by three Alpha 1.0.3 pneumatic block classes referencing missing MapCodec registrations.

## Fixed
- Pneumatic Proportional Valve: codec/block/item/tab/resources; redstone 0–15 command limits downstream pressure.
- Pneumatic Relief Valve: codec/block/item/tab/resources; clamps overpressure and records vent diagnostics.
- Pneumatic Cylinder: codec/block/item/tab/resources; finite-rate pressure-to-position actuator with feedback diagnostics.
- PneumaticNetwork: recognizes all three blocks and preserves bounded network traversal.

This is a delta over Alpha 1.0.2. Real NeoForge compile/build/runClient still must be confirmed locally.
