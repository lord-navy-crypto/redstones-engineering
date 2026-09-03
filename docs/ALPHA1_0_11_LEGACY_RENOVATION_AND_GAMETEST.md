# Alpha 1.0.11 — Legacy Renovation Wave II + Topology GameTests

Alpha 1.0.11 continues the post-dependency renovation by moving more early RSE infrastructure onto the shared Engineering Port Contract and by promoting topology behavior from manual observation to executable Minecraft GameTests.

## Why this milestone exists

Older RSE systems were often correct locally but used different code paths for physical connection, runtime transport, and player-facing diagnostics. That made it possible for a block to look connected while a network disagreed, or for a converter to work without exposing a machine-readable input/output contract.

Alpha 1.0.11 makes the contract explicit and testable.

## Legacy migration wave II

### Redstone Cable Terminal

The terminal now exposes exactly two engineering ports. In Vanilla-to-Cable mode the vanilla-facing side is an INPUT and the insulated-cable side is an OUTPUT. In Cable-to-Vanilla mode those directions reverse. Runtime snapshots report the current bounded 0..15 signal without introducing additional BlockState values.

### Redstone and Copper Junctions

Junctions expose ports only on faces that are physically connected in their six-direction topology state. Redstone junctions report the INSULATED_REDSTONE domain; copper junctions report COPPER. Both remain multi-drop branch devices.

### Lapis transducers

The shared AbstractLapisTransducerBlock now exposes a forward LAPIS_PRECISION SENSOR output with a runtime normalized snapshot and VALID / NO_SIGNAL quality. This migrates the whole transducer family at once instead of duplicating adapter code in every sensor.

### Explicit cross-domain converters

Redstone -> Lapis Scaler:

`BACK REDSTONE INPUT -> FRONT LAPIS OUTPUT`

Lapis -> Redstone Quantizer:

`BACK LAPIS INPUT -> FRONT REDSTONE OUTPUT`

Both use the new CONVERTER port kind so integrations can distinguish conversion boundaries from ordinary control or measurement ports.

## Executable topology tests

The GameTest suite uses `data/redstoneengineering/structures/empty5x4x5.nbt` and is registered through NeoForge's 1.21.1 `RegisterGameTestsEvent`.

Current tests verify:

1. Insulated Redstone Signal Cable connects to an Insulated Redstone Junction Box.
2. Insulated Redstone Signal Cable does not connect directly to a Copper Cable Junction Box.
3. Redstone Cable Terminal port direction reverses when its mode reverses.
4. Redstone/Lapis converters expose the correct input/output domains and directions.

Run locally with:

```bash
./gradlew runGameTestServer --no-daemon --stacktrace
```

A non-zero required-test failure count is a release-blocking regression.

## Architecture rule

GameTests observe and exercise the same production blocks and topology logic. They must not introduce a second simulation model just to make tests pass.

Jade remains a read-only observer over EngineeringPortProvider. Fusion visuals, future Cloth configuration, GeckoLib animation, and JEI documentation must consume the same authoritative contract rather than redefining connectivity.
