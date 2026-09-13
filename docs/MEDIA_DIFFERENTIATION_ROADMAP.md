# RSE Media Differentiation Roadmap

RSE media should differ by engineering behavior, not by texture or forced device compatibility.

## Design rule

A medium is justified when it creates a distinct engineering decision that instruments can observe and diagnose.

- Redstone: coarse local control logic, 0..15.
- Lapis: precision analog/reference information, 0..100.
- Instrument Bus: multi-channel measurement transport and probe ownership.
- Shielded Instrument Bus: the same measurement contract plus explicit shielding coverage and commissioning evidence.
- 8-bit Data Bus: parallel payload density with local loading and driver contention.
- Serial Data: framed byte transport with timing, utilization and length-dependent quality.
- Differential Data: one-bit high-integrity discrete signalling with stronger link margin.
- Quartz: clock/period/synchronization transport with clock-source conflict evidence.
- Optical Fiber: channelized guided optical transport with source ownership and explicit splitter topology.
- Amethyst: frequency/amplitude resonance transport with frequency-conflict evidence.
- Pneumatic: pressure/flow/storage/restriction behavior rather than an information wire.
- Copper: electrical source/load/network behavior rather than a generic signal wire.
- Radio / free-space optical: wireless propagation with range/interference or line-of-sight constraints.

## Long-line development order

1. Instrument/UI depth: Measure -> Capture -> Diagnose -> Next Action.
2. Measurement-bus shielding evidence in analyzer/scope commissioning UI.
3. Lapis precision identity: calibration, drift and reference/setpoint workflows.
4. Pneumatic differentiation: pressure drop, flow restriction, reservoir response and leak evidence.
5. Optical differentiation: loss budget, splitter/filter/attenuator evidence and channel diagnostics.
6. Communication UI: expose why Bus vs Serial vs Differential was chosen using existing loading/utilization/link-margin evidence.
7. Cross-domain commissioning: topology debugger and operations monitor should point to the relevant instrument and medium-specific failure mode.

## Non-goals

- No random client-side noise.
- No universal "better cable" that dominates every other medium.
- No fake RX/TX semantics on passive media.
- No invented history or client-side world sampling.
- No new medium until an existing medium has a distinct measurable role.
