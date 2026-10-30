# RSE Phase 5 — real packet freshness and optical route fail-closed

Date: 2026-10-29. Minecraft 1.21.1 / NeoForge / Java 21.
Branch: `automation/rse-formula-parameter-workbench`.

## Why this change is functional, not cosmetic

Previously the free-space optical receiver wrote its own retained envelope again every five ticks, lowering `qualityPercent` by 20 but renewing `LAST_UPDATE_STAMP`. Thus `ageTicks` described the receiver's housekeeping activity rather than the most recent **transmitter** update. Old packets could temporarily keep the 0..15 Redstone output energized after the TX stopped. Another issue was that changing the RX channel preserved its old packet, so rapidly switching back might resurrect the former signal. OpticalSystemMenu used direct `setBlock` for its TX/RX channel buttons instead of a shared safe configuration entry point.

## Changed mechanics

1. **Receiver freshness:** `FreeSpaceOpticalChannelSupport.receiveQuality` accepts packets as VALID only if the latest actual TX write was at most **8 game ticks** old, channel matches, `packet.valid()` is true and quality is positive. If older, it reports STALE, never a fabricated active carrier. The RX's own periodic tick no longer writes a refreshed packet. The RX output is recomputed every 2 ticks; a dead transmitter therefore de-energizes the physical output after the freshness threshold and a bounded 2-tick update delay. Invalid old packets remain inspection-visible for up to 40 ticks, then runtime state is removed. This retention is *not* an extension of the VALID window.
2. **Channel change:** Both the in-world Shift operation and OpticalSystemMenu direct/cycle buttons call the respective class's server-authoritative `setChannel`. The RX immediately discards the retained packet and sets Redstone output to 0 on an actual channel change; going back to the old channel cannot resurrect it without a new received beam. An unchanged channel is an acknowledged no-op. TX sets the new channel and triggers an immediate emission + follow-up tick.
3. **Route change:** RX rotation from OpticalSystemMenu now invalidates the packet/output after its physical input/output route changes. No old beam is assumed valid for the new orientation.
4. **Instrument consistency:** The Field Device HMI now derives the RX quality and validity from `engineeringSnapshot`, which applies the real freshness/channel contract, rather than inspecting raw `InformationRuntime.valid` and the retained quality percentage. The Optical System HMI already read through this port snapshot.
5. **Pure shared helper:** `FreeSpaceOpticalChannelSupport` provides channel bounds, channel cycling, 0..15 payload clamp and packet-quality classification to the TX/RX pair. Neither their physical roles nor their separate ray propagation and redstone equations are merged.

## Compatibility & limits

- All 130 block IDs, recipes, models, loot tables, BlockState property identities, server runtime envelope layout and media topology remain unchanged.
- Existing TX 48-block line-of-sight range, distance-dependent attenuation, direction alignment and 4-channel filtering remain intact.
- The TX does not currently maintain a unique per-source ownership token in an RX packet. Therefore an old RX packet **cannot always be revoked instantly** when an arbitrary transmitter vanishes or changes its channel without risking revocation of another legitimate TX. Instead packets expire after 8 ticks without a new TX write, and are cleared from the Redstone output within the RX's 2-tick update cadence. This is an explicit bounded-freshness guarantee, **not a claim of instant global revocation**.
- Source-static checks and Gradle tests do not mean Minecraft GameTests were run. Run the server suite and perform old-save / chunk-unload / multiplayer acceptance independently.

## Tests & audit

Four registered GameTests (not automatically executed in ordinary CI):
- `receiverTimeoutCannotBeExtendedByOwnTicks`;
- `receiverChannelRoundTripCannotResurrectOldPacket`;
- `receiverRouteChangeInvalidatesOldEvidence`;
- `transmitterNoOpChannelDoesNotScheduleOrChangeBlock`.

The new default CI gate `tools/rse_free_space_optics_freshness_verify.py` verifies the unchanged TX/RX physics ownership and presence of the channel/freshness contracts, HMI routing and GameTest registration. The 46-candidate audit tracks two newly shared helper consumers (`free_space_optical_transmitter`, `free_space_optical_receiver`); after this phase **23 shared-helper users**, **3 other functional evidence upgrades**, **3 previously untouched metering safety upgrades**, and **17 still queued** (46 total, no double counting). Two previously shared candidates also have additional safety hardening, along with the new optical pair.

## Manual engineering verification

Set up an aligned TX/RX with a controlled Redstone source. Observe VALID, then remove TX power and confirm `ageTicks` advances even though the RX is still ticking, `quality` changes to STALE and output goes to 0. Verify unchanged/changed channel, cycling back, HMI channel buttons and route rotation. Test obstructions, line-of-sight gap/unloaded chunks, a second transmitter and channel mismatch. Compare the Field Device and Optical System HMI. Verify old worlds and client/server synchronization before stable release.
