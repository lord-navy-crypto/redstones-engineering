# RSE Phase 6 — fail-closed digital endpoints and bounded input authority

**Date:** 2026-10-29. **Baseline:** Minecraft 1.21.1, NeoForge 21.1.249+, Java 21. Public block registry, BlockState keys, media domains and save/runtime envelope layouts unchanged.

## Why

Three digital converters can outlive the authority of their previous input data. The serializer and deserializer previously retained a former 8-bit value inside an invalid envelope when the upstream quality became STALE. A correct consumer must check the valid bit, but several human-facing observations still showed a plausible previous value. The differential driver was predominantly neighbor-event-driven and lacked a periodic input-authority watchdog, so some chunk-boundary transitions could leave the former bit in its output envelope until a new neighbor event occurred.

## Actual behavior changes

**Serializer** `DATA_BUS_8 -> SERIAL_DATA`: On invalid, stale, disconnected or conflicted bus input, write `value=0, valid=false, quality=0` instead of retaining an earlier framed byte. Input and output port readouts now show zero unless their corresponding source evidence is VALID. A 16-tick watchdog already existed and remains in place. The serial line is recomputed if its output chunk is loaded; no attempt is made to read an unloaded output chunk.

**Deserializer** `SERIAL_DATA -> DATA_BUS_8`: Validity now requires both `SerialNetwork.quality(input) == VALID` **and** `InformationRuntime.snapshot("serial", input).valid()`. An invalid frame writes zero and revokes its driver authority rather than replaying the earlier byte; its source/output inspection gates also suppress invalid payload readings. The original 16-tick watchdog and bus resolver remain intact, with explicit loaded-output guard.

**Differential Driver** `REDSTONE -> DIFFERENTIAL_DATA`: Added an independent 16-tick server watchdog to repeat input observation even when neighbor change notifications are absent, such as after a chunk boundary transition. A missing/conflicted input now yields an invalid zero bit on the output envelope, and a bit/validity transition triggers the existing differential recompute. Unloaded output chunks are not queried during an update or removal.

A pure `DigitalEndpointEvidenceSupport` helper standardizes **upstream quality + outgoing envelope validity** and **zero-on-invalid payload projection**. It does not drive media, alter physics, mutate BlockState or merge device IDs.

**Important safety semantics:** An invalid numeric zero is not the same as a VALID externally driven zero. Source quality and output valid bit remain separate. Disconnect/reset is bounded by the existing 16-tick watchdog if no earlier neighbor event fires. This is not an instantaneous global driver-revocation guarantee.

## Tests

`RseDigitalEndpointWatchdogGameTests` registers three runtime cases: seed a formerly valid byte or bit, remove any current upstream source, then allow the scheduled watchdog to revoke the old output envelope. Source contracts are gated by `tools/rse_digital_endpoint_fail_closed_verify.py`, added to the default GitHub workflow. Static verification and Gradle compilation do **not** prove these in-game cases have run; the default Minecraft GameTest server step remains manual.

For manual acceptance: test VALID 0/1 and 0..255 payloads with real attached media, disconnect without and with neighbor events, chunk unavailability, conflict between drivers, HMI readout, old save load, and multiplayer client/server output consistency.

## Candidate inventory

All 130 registered blocks remain. Of 46 shared-code review candidates: 26 now use shared internals (this stage adds serializer, deserializer and differential driver), 6 other candidates have discrete function/evidence fixes, and 14 remain queued. Other prior safety upgrades of already-shared candidates are tracked separately without double counting. This metric is **source implementation coverage**, not verified physical equivalence or game acceptance.
