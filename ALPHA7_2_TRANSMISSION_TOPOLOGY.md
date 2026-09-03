# RSE alpha.7.2 — Transmission Topology Pass

- Redstone/Lapis/Quartz/Amethyst surface media now render only real horizontal connections.
- Lapis/Quartz/Amethyst live payloads moved out of BlockState.
- Redstone/Copper/Optical cables now auto-render six-direction cable arms.
- Plain cables are two-ended; 3+ adjacent ports are a topology error.
- Redstone/Copper Junction blocks are explicit multi-port branch/splice points.
- Optical passive fiber/splice remains two-ended; Optical Splitter is required for branching.
- Iron is a field domain; Thermal is a state/conduction domain — neither gets a fake cable.
- Redstone cable graph budget fixed at 128 nodes and unloaded chunks are not scanned.
