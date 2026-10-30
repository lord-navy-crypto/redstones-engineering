# Complete static function and code-slimming audit — 130 registered blocks

**Audit revision:** `5c7cb32c1558b3e6dff5fca1f06dc3c7fc739cdc`. Source snapshot was captured from CI, then 130/130 registered Java implementation classes and project-owned JSON definitions were inspected with per-block role, behavior, physical analogue and retention/refactor decisions.

## Confirmed coverage

- 122 core + 8 systems blocks, all **130 registered Java sources**, 20,377 Java source lines in the registered classes alone.
- 130/130 blockstates, 130/130 item model JSON, 130/130 matching recipes, 130/130 block loot tables. Blockstate-referenced model JSON files were resolvable, including four per-channel `signal_probe_0..3` models.
- 83 classes declare tick overrides, 96 onRemove, 117 an engineering port snapshot. These static counts **do not establish runtime CPU cost**.
- 70 independently functional retain/default review; 46 code-sharing candidates in 15 groups; 14 niche/product placement review. **These counts are NOT deletion approval.** Zero block removals were proven safe, no production block behavior changed.

## Actionable priorities

### P0 — preserve block IDs and engineering invariants
Do not delete or rename public registry IDs, collapse different media, or change BlockState/codec/loot/recipe/world save formats without save migration. Keep server-authoritative status and invalid/unknown signal quality distinct from numerical zero.

### P1 — genuinely repeated method shapes
Direct source-method comparisons (character-structure similarity, **not** functional equivalence):

- `optical_attenuator` vs `optical_channel_filter`: onRemove **1.00**, engineeringSnapshot **1.00**, onPlace **0.97**, tick **0.89**. Extract safe shared input/snapshot/output invalidation code, preserve attenuation vs channel law.
- `mechanical_exciter` vs `hydroacoustic_exciter`: onPlace **1.00**, neighborChanged **1.00**, useWithoutItem **0.98**, engineeringSnapshot **0.98**. Share validated control/diagnostic primitives; **do not** join mechanical-vibration and hydroacoustic propagation.
- `pneumatic_valve` vs `pneumatic_check_valve`: onRemove **1.00**, onPlace **1.00**, engineeringSnapshot **1.00**; preserve manually commanded bidirectional opening versus automatic one-way check semantics.
- `quartz_oscillator` vs `quartz_lab_oscillator`: share limited clock/port utilities while keeping jitter and timing-evidence contracts.

### P2 — reduce large-class responsibility
`signal_analyzer` (572 LOC), `pid_controller` (517), `workcell_controller` (362), `redstone_cable_junction` (316), `operations_monitor` (293), `range_sensor` (274), `copper_circuit_meter` (275) should be investigated for pure-math/telemetry/event separation rather than removal. Prioritize behavioral test witnesses.

### P3 — small/teaching/niche value review, no automatic deletions
`engineering_compass`, `analog_indicator`, `lapis_noise_source`, `iron_core`, `honey_vibration_damper`, `molecular_cloud_receiver`, `sculk_vibration_interface`, `phonon_conduit`, four soul-flux blocks, `thermal_pulse_encoder` and `thermal_pulse_receiver` need player-workflow/physical-analogy review. This does **not** mean they are dead features: `iron_core` models remanence; `analog_indicator` gives in-world readout; `lapis_noise_source` supports reproducible testing.

## Examples of essential distinctions

- Copper series resistor limits transmission current while terminal resistive load ends a circuit.
- Oscilloscope, logic analyzer and calibrated signal analyzer offer different observation modes and should share UI/chart primitives, not registry identity.
- Pneumatic relief, pressure regulator, check, manual and proportional valves have separate safety/control functions.
- Wired optical carriers and free-space optical LOS are distinct.
- The player-facing `redstone_cable_junction` uses an old registry identifier as a compatible **explicit single-medium junction**. Historical copper/optical junction IDs remain for existing worlds even where they have been removed from the new creative catalog. No implicit cross-domain bridging.

## Verification and real-world caveats

This is a **complete 130/130 static source/resource and architectural review**, **not** 130/130 in-game test completion. RunClient visual acceptance, multiplayer sync, behavior equivalence, world-save migration, GameTests and CPU profiling remain unverified. No code-reduction percentage or safe public block deletion count is currently evidenced.

Treat 0..15/0..100 Minecraft numeric media as discrete proxies, 20 ticks/s as nominal game clock rather than real SI time, and Amethyst/Lapis/Soul-Flux domains as worldbuilding or teaching metaphors. Measurement resolution is not measurement uncertainty. For conceptual grounding see NIST measurement uncertainty and IEC 61131-3:2025 function-block separation, and ISO 1219 fluid-power component distinctions.

**Recommended next change:** one tiny test-backed common helper extraction in the high-similarity P1 pairs, then compare old/new calculations, onRemove cleanup, connection identity, behavior test coverage and old-world loading before any further cleanup.

The full Chinese per-block table and 10-pair method comparison were produced as standalone user-facing audit artifacts from the CI source evidence. Preserve those alongside the reviewed revision for subsequent implementation.
