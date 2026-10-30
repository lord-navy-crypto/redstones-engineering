# RSE Phase 7: Amethyst frequency integrity and truthful readouts

**Date:** 2026-10-29. **Target:** Minecraft 1.21.1 / NeoForge 21.1.249+ / Java 21.

## Scope
Three independent blocks retain their existing registered IDs and responsibilities:
- `amethyst_resonator`: timed, configured frequency/amplitude pulse source
- `amethyst_frequency_filter`: exact frequency selector with one-point amplitude loss, no gain
- `amethyst_tuned_resonator`: finite bandwidth, quality-index-dependent resonant gain/loss and saturation

They now reuse one **read-only** `AmethystFrequencySupport` for physical source port evidence, loaded-coverage validation, dust status-to-port quality, and zero-on-invalid display. There is no shared replacement physics solver and no changed save/runtime layout, recipe, model or port direction.

## What actually changes

1. `DomainNetwork.sampleAmethyst` checks `level.hasChunkAt(pos)` before querying a neighbor's block state. Reading an unloaded chunk returns an inactive zero sample; the frequency quality helper labels it **STALE**, not a valid zero or a proven absent carrier.
2. Both processors check coverage of their input/output sampling positions before reading the world. The two former locally duplicated `qualityAt` classifications are replaced with `AmethystFrequencySupport.qualityAt` (ACTIVE/NO_SIGNAL/FREQUENCY_CONFLICT/STALE). Physical status is not inferred from a value alone.
3. For the processors' output **port observations**, a STALE or conflicting input takes precedence. The visible amplitude is suppressed to zero unless the output quality is VALID or SATURATED, so a retained old dust value does not masquerade as a new measurement. Their physical 2-tick output recomputations continue to drive a matching carrier or clear it using the original formulas.
4. The Field Device HMI for both processors now reads the device's formal `engineeringSnapshot` rather than simply asking whether neighboring dust is active. The specialized Amethyst HMI still displays the explicit input evidence and original configuration.
5. The resonator source reports its configured amplitude only during a real pulse; an inactive source remains NO_SIGNAL. Source pulse timing and configured frequency are unchanged.

## Regression evidence

Three new registered `RseAmethystFrequencyIntegrityGameTests` cases exercise a conflict/retained-output scenario, a stale/retained-output scenario, and actual active-vs-inactive pulse evidence. The new `tools/rse_amethyst_frequency_integrity_verify.py` is a required default CI source gate.

**Limitations:** Successful Java compilation, Gradle test, or source assertions do not mean Minecraft GameTests were actually run. The default game server suite, old-save load, chunk-unload in multiplayer, and live timing/visual tests require separate in-game acceptance. No functional equivalence beyond the explicitly unchanged transfer equations is claimed.

## 46-candidate audit

The per-block audit now records **30 shared-helper consumers**, **6 independently enhanced candidates**, and **10 queued candidates**. All 130 registered block IDs remain unchanged. Source-level sharing is not evidence of identical device functions or complete runtime acceptance.

## Manual check sequence
1. Pulse a configured resonator, then let it stop; verify the source observation changes VALID/amplitude → NO_SIGNAL/0.
2. Feed matching and mismatching frequencies into the exact filter and tuned resonator. Confirm that only the tuned variant provides finite-bandwidth resonance and amplification.
3. Trigger competing different-frequency sources; input should be TOPOLOGY_ERROR and no processor should present a formerly sampled amplitude as current.
4. Break and restore a chunk boundary; input should show STALE with zero readable amplitude and recover after real coverage/sampling returns.
5. Compare Field Device and specialized Amethyst screens with actual wire readings; verify legacy save loading and multiplayer synchronization.
