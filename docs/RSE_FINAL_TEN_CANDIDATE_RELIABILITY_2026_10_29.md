# RSE final 10 candidate reliability patch — 2026-10-29

Minecraft 1.21.1 · NeoForge 21.1.249+ · Java 21. All 130 block IDs, state properties, recipes and distinct physical media are retained.

## Exact review targets

The last 10 previously unaddressed entries in the 46-candidate source audit were:
- `lapis_precision_meter`
- `lapis_optical_transducer`
- `topology_debugger`
- `optical_emitter`
- `optical_receiver`
- `redstone_cable_junction`
- `copper_cable_junction`
- `optical_fiber_junction`
- `instrument_cable`
- `shielded_instrument_cable`

## Actual functionality and extracted internals

**Source-correct measurement evidence (`MeterEvidenceSupport`):** the Lapis precision meter now masks stale/conflicted/unavailable readings to 0 while retaining the exact quality flag; the Lapis optical transducer similarly masks invalid optical intensity before its original 0–15 to 0–100 normalization. The topology debugger reuses the source-quality alarm snapshot helper, but remains a different device: its unseen target chunk asserts a fail-safe alarm (15), the port reports STALE, and inspection prints an unloaded-target notice rather than interrogating the missing block.

**Guided optical terminals (`GuidedOpticalEvidenceSupport`):** the emitter's configured 0 intensity is still a VALID source *setting*, which is not a positive carrier. The receiver rejects simultaneous source-driver claims and never presents retained intensity as valid under TOPOLOGY_ERROR/STALE/NO_SIGNAL. Neighbor-fiber inspection skips unloaded positions. The network's optical graph traversal, attenuation and channel propagation still belong to the existing solver.

**Different kinds of junctions (`JunctionEvidenceSupport`):** only the shared observation/gating code is extracted. The unified vertical multi-medium Junction Point retains one selected medium and explicitly blocks cross-media conversion; the copper branch box retains its copper solver and 6-face topology; the optical service splice retains its two-end isolation switch. Connected ports into unknown/unloaded neighbors report STALE. Invalid copper source authority now clears the numerical voltage claim; information-channel and optical carrier port readouts no longer expose retained values as current when invalid. No new junction connections were introduced.

**Instrument and shielded instrument cables (`InstrumentCableEvidenceSupport`):** both retain their original instrumentation-network scan and shielding physics. Shared diagnostic formatting removes duplicate exposure calculations in the UI; the cable port evidence marks direct connected arms into unloaded chunks STALE rather than presenting the loaded subset as a valid complete reading. This does **not** claim global unknown-coverage detection for all deeper cable paths, and existing scan-budget errors remain distinct. The shielded variant inherits the normal instrument-cable port provider, with its own shielding diagnostic response.

## Verification and outstanding acceptance

- New `tools/rse_final_ten_integrity_verify.py` is in the default CI static verification gate, with exact per-class helper-use checks and explicit evidence rules. The source matrix only counts a class as shared when it genuinely invokes the corresponding helper; inheritance is handled explicitly.
- Four added, registered NeoForge GameTests cover optical receiver driver conflict, invalid copper voltage, valid configured optical zero and maintenance-isolated optical splice.
- The 46-candidate audit is now **40 actual shared-helper users + 6 separately functionally improved candidates + 0 unaddressed source candidates**. These are source-level engineering review categories, **not** confirmation that every device has passed runtime, multiplayer or old-save tests.
- Vanilla/NeoForge in-game GameTests, dynamic loaded/unloaded chunk checks, client-server HMI coherence and old-world save acceptance must be executed separately; default CI compiles their registration but does not run its optional game-test server.

## Operator acceptance matrix

1. **Lapis and optical metrology:** no source -> NO_SIGNAL/0; unloaded source -> STALE/0; valid source -> original numeric transfer; conflict -> not a usable reading.
2. **Optical emitter/receiver:** 0-intensity emitter remains a valid configuration, but a physically valid intensity requires a unique source. Add a second driver and verify TOPOLOGY_ERROR/0 at receiver.
3. **Junctions:** toggle optical service-open and ensure both segments isolate; change multi-medium junction from one medium to another and verify no cross-medium continuity; remove the last copper source and confirm no old voltage.
4. **Instrument bus:** connect multiple probes and inspect duplicate-channel faults; compare shielded vs unshielded local interference confidence; unload an adjacent connected cable arm and check STALE.
5. **Topology debugger:** unload its target chunk and verify the fail-safe alarm, STALE port observation and safe inspection message.
