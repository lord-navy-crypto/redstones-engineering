# 130-block feature and implementation slimming audit — 2026-10-28

## Decision

**Do not delete or merge any public block ID on the basis of a similar name or source file.** The current registry is 122 core + 8 EngineeringSystems blocks (130 total). The non-destructive CI analyzer `tools/rse_130_block_slimming_audit.py` produces the complete per-block CSV/JSON/Markdown in `.rse-audit/rse-130-block-slimming-inventory.*`.

The CI-verified static source footprint at the initial audit revision was:

- 130 registered Java block classes, **20,377 source lines**, **982,205 source bytes** (excluding inherited base classes, physics kernels, UI, resources, and tests).
- 83 declared `tick` overrides, 96 `onRemove` overrides, 117 classes declaring `engineeringSnapshot`.
- 15 potential code-sharing review groups covering 46 candidates. **None are proven gameplay duplicates.**
- 23 block-facing LDLib2 UI families already share identity/evidence/operator guidance. The passive engineering compass retains its intentional right-click datum readout.
- No runClient, Minecraft GameTest, multiplayer, existing-world migration, or before/after performance comparisons were completed by this source audit.

## Highest value safe refactoring investigations

| Priority | Areas | Safe direction | Must remain distinct |
| --- | --- | --- | --- |
| P1 | `optical_attenuator`, `optical_channel_filter` | Shared optical sampling/quality/output invalidation helpers | Loss law vs frequency/channel selection |
| P1 | `mechanical_exciter`, `hydroacoustic_exciter` | Shared Redstone drive, frequency parameter guards, status snapshots | Mechanical-vibration vs hydroacoustic media and propagation |
| P1 | `quartz_oscillator`, `quartz_lab_oscillator` | Pure clock tick and cycle-parameter calculations | Lab timing/jitter telemetry and existing registry IDs |
| P1 | `signal_analyzer` (572 LOC), `pid_controller` (517 LOC), `workcell_controller` (362 LOC) | Move pure calculations, snapshots, formatting into focused reusable units | Sampling authority, control laws, process state and persisted keys |
| P2 | `range_sensor`, `lapis_precision_range_sensor` | Reuse bounded scan geometry where equivalent | Output domain, precision/quantization, validity semantics |
| P2 | `precision_filter`, `signal_conditioner`, `lapis_low_pass_filter` | Reuse math/parameter validation where mathematically equivalent | Digital slew, programmable conditioning and higher-resolution low-pass are not the same response |
| P2 | `sample_hold`, `quartz_triggered_lapis_sampler` | Shared edge/hold/invalidity primitives | Clock/media domain, timing and precision |
| P2 | Pneumatic valve family | Shared port/snapshot/lifecycle code | On/off, proportional, check, relief, regulation each serve a distinct control/safety role |

Not recommended as product-level merges: oscilloscope vs logic analyzer vs signal analyzer; copper series resistor vs terminal load; wired optical emitter/receiver vs free-space optical transceiver; all medium-specific junctions; differential driver/receiver/physical pair; check valve vs relief valve. Real instrumentation and physical transmission types have different contracts.

## Evaluate value, not just duplication

Potentially niche or heavily game-specific entries needing a usage/playtest review:

- `engineering_compass`, `analog_indicator`: determine whether their quick, in-world feedback offers value beyond full instruments.
- `lapis_noise_source`, `iron_core`: assess real demand in test workflows/magnetic circuits before changing recipe prominence.
- `molecular_cloud_receiver`, four `soul_*` blocks, `sculk_vibration_interface`: clearly label Minecraft-fictional or teaching analogies, not literal industrial physics.
- `phonon_conduit`, `honey_vibration_damper`, thermal-pulse encoder/receiver: review their modeling assumptions and genuinely distinctive loss/delay behavior.

This is **not a deletion list**. Existing builds, world saves, recipes, block items, creative inventories, JEI-like integrations, and user-built circuits may depend on these registry IDs.

## Hard gates before removing or fusing public blocks

1. Record the **observed** input/output transfer, units, quality states, propagation delay, bandwidth, conflict handling and domain boundaries for each candidate.
2. Add boundary/missing-sample tests and side-by-side reference tests to establish full functional equivalence.
3. Prove no change to world persistence, block states, recipes/loot, topology and client/server authority. If IDs must change, require a migration and save compatibility plan.
4. Measure code size, server tick cost, UI operation cost, and regression results before/after refactoring. **No unmeasured percentage reduction claims.**
5. Prefer internal code deduplication and coherent creative-tab/UI grouping first; only consider consolidating visible blocks when their distinct purpose has demonstrably disappeared.

## Auditing outputs and evidence limits

The automated inventories in `.rse-audit/` cover every registered block's class, source size, property names and declared port-domain constants. The additional Chinese review report groups candidates by corresponding real-world devices. Reality comparisons are functional analogies, especially for fantasy-domain devices; this audit does not certify physical realism or runtime acceptance.

A simpler source tree is desirable only if the user-facing behavior and engineering correctness are preserved. **A larger feature count or fewer block names is not, by itself, a success metric.**
