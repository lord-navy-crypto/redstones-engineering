# Engineering Compass

The **Engineering Compass** is a passive commissioning and layout reference for Redstone Systems Engineering builds.

## World-axis contract

The block has no directional BlockState and does not rotate with player placement. Its top markers therefore remain aligned to Minecraft world axes:

- **NORTH** — red, long/prominent marker
- **EAST** — blue
- **SOUTH** — green
- **WEST** — yellow

The long red marker is the primary datum. Once NORTH is identified, the remaining three markers provide the complete horizontal frame without requiring the player to infer direction from camera orientation.

## Engineering role

This block is intentionally an **operator reference**, not a simulation component. It exists to reduce orientation mistakes while building and commissioning explicit RX/TX routes.

It has:

- no BlockEntity;
- no scheduled tick loop;
- no redstone input or output;
- no EngineeringPortProvider contract;
- no domain-network participation;
- no hidden direction inference.

The block may be placed next to a machine, cable run, test bench, or commissioning area without changing the behavior of the system being observed.

## UI relationship

The Compass complements, but does not replace, the Engineering UI Route page and I/O Compass overlay:

- the physical block establishes a persistent world reference in the build;
- the Route page configures declared RX/TX faces;
- the I/O Compass overlay reports the device's actual declared ports and evidence.

The three surfaces must agree on Minecraft's absolute NORTH/EAST/SOUTH/WEST directions.
