# RSE phase 6: loss-of-source safety for serial and differential endpoints

Date 2026-10-29 · Minecraft 1.21.1 / NeoForge · Java 21

## Goal

No invalid, missing, or conflicted input may be shown as an authoritative byte/bit or keep a retained asserted output indefinitely. Preserve all public IDs, wire/media domains, topology constraints, BlockState schema and existing runtime envelope layout.

## Changes

The serial **Serializer** invalidates a formerly framed byte to zero on upstream bus loss. The **Deserializer** requires both the serial medium's VALID quality **and** a valid serial frame; it withdraws a stale bus driver value rather than replaying old byte data. Both keep the existing 16-tick authority watchdog and normal neighbor-driven updates.

The **Differential Driver** now periodically rechecks its Redstone source every 16 ticks. A vanished/unloaded source is not a valid zero-bit signal: its packet becomes invalid, and an adjacent loaded differential pair is recomputed to withdraw the former driver claim.

The **Differential Receiver** now uses the same pure `DigitalEndpointEvidenceSupport` quality-first bit readouts as the other digital converters. An unconnected/invalid incoming differential bit is displayed as zero with NO_SIGNAL/STALE or the actual conflict quality, not as a valid retained HIGH. Its physical 0/15 Redstone output is recomputed on normal events and at least once every 16 ticks, closing silent chunk-coverage change gaps.

`DigitalEndpointEvidenceSupport` is a pure helper for endpoint observation; the serial frame transmission, byte-bus contention and differential wire physics remain owned by their individual network solvers.

## Cautions

The watchdog interval is **16 game ticks**, not a guarantee of instantaneous revocation after arbitrary chunk unload. Readouts can reject invalid evidence earlier than the next physical-state update. These changes have no migration of saved ID or runtime schema. Underlying network scans and loaded-chunk behavior remain original; exact multiplayer and old-world correctness must be separately tested.

## Tests

The existing newly registered `RseDigitalEndpointWatchdogGameTests` includes serializer, deserializer and differential driver loss-of-source checks. New `RseDifferentialReceiverSafetyGameTests` includes the differential receiver's orphaned-HIGH withdrawal and synthetic valid-zero/invalid packet boundary checks.

Default CI now runs `tools/rse_digital_endpoint_fail_closed_verify.py` and `tools/rse_differential_receiver_safety_verify.py`. These source checks and ordinary Gradle tests **do not execute** Minecraft GameTests unless the GameTest server workflow is separately enabled.

The 46-candidate inventory gains four shared-helper implementations in this phase (Serializer, Deserializer, Differential Driver, Differential Receiver), taking the total to **27 of 46** candidate devices using common logic; 3 independent functional enhancements and 3 previously untouched meter hardenings remain separately tracked, leaving **13** unaddressed candidates (no double-counting).
