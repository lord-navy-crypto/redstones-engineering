# Alpha 1.0.12 Test Matrix — Directional I/O Renovation

| Area | Test | Expected result |
| --- | --- | --- |
| Reference Source | Place with FRONT east | Only east is an EngineeringPort output |
| Reference Source | Query vanilla redstone connectivity | Only the reversed API direction corresponding to physical FRONT connects |
| Reference Source | Change reference value | Output remains clamped to 0..15 |
| Light Sensor | Measure local brightness | POWER remains 0..15 and emits only through FRONT |
| Tank Level Sensor | Scan fluid column | Count is capped at 15 and emits only through FRONT |
| Entity Density Sensor | Count nearby living entities | Count is capped at 15 and emits only through FRONT |
| Sensor family | Inspect Jade/EngineeringPort | Exactly one REDSTONE SENSOR OUTPUT port is exposed |
| Analog Indicator | Place redstone block at BACK | LEVEL becomes 15 |
| Analog Indicator | Remove BACK source and place source at SIDE | LEVEL becomes 0; SIDE is ignored |
| Analog Indicator | Inspect EngineeringPort | Exactly one BACK REDSTONE INPUT is exposed |
| BlockState | Directional endpoints | Horizontal FACING remains low-cardinality; no history/metrics in BlockState |
| Legacy topology | Cable↔Junction both placement orders | Both endpoints connect |
| Domain isolation | Redstone cable beside Copper Junction | No direct connection |
| Converter regression | Redstone↔Lapis converter contracts | BACK/FRONT domains remain correct |
| Build | Static/reference verifiers | PASS |
| Build | Java 21 compileJava | PASS |
| Build | Gradle tests | PASS |
| Runtime | runGameTestServer | All required GameTests PASS |
| Release candidate | clean build + SHA256 + artifact upload | PASS |
