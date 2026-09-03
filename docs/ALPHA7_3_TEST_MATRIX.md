# alpha.7.3 Test Matrix

| Test | Expected |
|---|---|
| Lapis trace N↔S | connects + propagates |
| Lapis trace E↔W | connects + propagates |
| Lapis trace Up/Down | no connection |
| Quartz trace horizontal | clock propagates |
| Quartz trace vertical | no connection |
| Amethyst dust horizontal | f/A propagates |
| Plain Redstone cable bend | PASS |
| Plain Redstone cable vertical | PASS |
| Plain Redstone cable degree 3 | topology error / no graph transmission through invalid cable |
| Redstone Junction degree 3+ | PASS |
| Copper cable degree 3 | topology error |
| Copper Junction branch | PASS |
| Optical passive fiber degree 3 | topology error |
| Optical Splitter 1→2 | PASS with power split |
| Any graph >128 nodes | bounded; diagnostic shows BUDGET-LIMITED |
| Unloaded adjacent chunk | traversal does not force-load |
| remove runtime-state block | runtime entry removed |
| `compileJava` | PASS |
| `build` | PASS |
| fresh client launch | PASS |
