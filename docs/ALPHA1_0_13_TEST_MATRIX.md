# Alpha 1.0.13 Test Matrix

| Area | Setup | Expected result |
| --- | --- | --- |
| Axial port contract | Series Resistor facing EAST | WEST=INPUT, EAST=OUTPUT, other faces absent |
| Capacitor port contract | Capacitor facing SOUTH | NORTH=INPUT, SOUTH=OUTPUT |
| Fuse port contract | Fuse facing WEST | EAST=INPUT, WEST=OUTPUT |
| Source semantics | Inspect Copper Voltage Source | All compatible physical faces are COPPER ELECTRICAL OUTPUT |
| Load semantics | Inspect Copper Resistive Load | All physical faces are INPUT; load is not transparent |
| Meter semantics | Point Copper Circuit Meter at copper target | Only FACING is MEASUREMENT input |
| Series runtime propagation | Source -> wire -> resistor -> wire -> load | Input > 0; output > 0 and output < input; load receives output segment |
| Fuse safety | Low-rated fuse under real load | TRIPPED=true and protected output=0 |
| Side isolation | Feed series resistor from a SIDE | Output remains 0 |
| Historical topology | Redstone cable/junction tests | All Alpha 1.0.11 tests remain green |
| Historical directional I/O | Source/sensor/indicator tests | All Alpha 1.0.12 tests remain green |
| Build | Java 21 + required dependency platform | compileJava succeeds |
| Runtime gate | `./gradlew runGameTestServer` | All registered RSE GameTests pass |
| Release build | `./gradlew clean build` | JAR produced after GameTests pass |
