# Alpha 1.0.12 — Directional I/O Renovation

Alpha 1.0.12 removes another class of early RSE ambiguity: devices that had an engineering meaning but still behaved as six-face vanilla-redstone endpoints.

## Design rule

A physical engineering device should make its electrical interface legible from placement and orientation.

- **FRONT** is the device-facing/output/display side.
- **BACK** is the input side when the device is a sink/display.
- Minecraft's redstone API queries a block from the opposite direction of the physical side; RSE keeps that convention centralized rather than duplicating ad-hoc direction tests.
- Runtime redstone values remain bounded to **0..15**.

## New shared bases

### `DirectionalRedstoneEndpointBlock`

Provides a horizontal `FACING` property plus FRONT/BACK helpers for vanilla-redstone endpoints. It intentionally extends normal `Block`, not `DomainBlock`, because these devices must remain compatible with vanilla redstone.

### `DirectionalRedstoneSensorBlock`

Adds the common sensor contract:

- one FRONT `REDSTONE` / `SENSOR` / `OUTPUT` engineering port;
- one 0..15 `POWER` state;
- FRONT-only vanilla-redstone connectivity and signal emission;
- a shared server-side output update path;
- EngineeringPortSnapshot output for Jade and other read-only integrations.

## Migrated devices

### Redstone Reference Source

The early compressed implementation has been rewritten into maintainable source. The source now emits only through FRONT while preserving its adjustable 0..15 laboratory reference value.

### Engineering Light Sensor

Measures combined local brightness but exposes the measured 0..15 result only through FRONT.

### Tank Level Sensor

Counts contiguous fluid blocks above the probe, capped at 15, and exposes the result only through FRONT.

### Entity Density Sensor

Counts nearby living entities, capped at 15, and exposes the result only through FRONT.

### Analog Process Indicator

The previous `LEGACY_OMNIDIRECTIONAL` behavior is removed. FRONT is the visible display face; BACK is the only redstone input. A signal placed on a side face must not affect the displayed level.

## Why this matters

Directional I/O makes engineering diagrams and builds self-documenting. A player can distinguish source, sensor, processor, and display topology from orientation instead of relying on hidden six-face behavior. It also makes Jade's engineering-port HUD agree with the actual simulation rather than merely describing legacy behavior.

## BlockState discipline

The renovation adds only a four-way horizontal `FACING` property to these endpoints. Existing blockstate resources use unconditional multipart models, so the change does not require enumerating every `FACING × POWER/LEVEL` visual combination. No high-cardinality measurement history is moved into BlockState.

## Verification

Alpha 1.0.12 extends the executable Minecraft GameTest suite to verify:

1. Reference Source exposes only FRONT output.
2. Directional sensors expose only FRONT output.
3. Analog Indicator exposes only BACK input.
4. Minecraft's reversed redstone-query convention matches those physical ports.
5. A real redstone block on Indicator BACK produces level 15.
6. Moving that source to a SIDE leaves the Indicator at level 0.
7. Alpha 1.0.11 cable/junction and cross-domain topology tests remain green.

The complete CI order remains static/reference verification → Java compilation → Gradle tests → Minecraft GameTests → clean build → SHA-256 → verified artifact upload.
