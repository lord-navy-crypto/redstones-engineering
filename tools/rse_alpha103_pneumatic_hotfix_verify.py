#!/usr/bin/env python3
from pathlib import Path
import sys, json
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
main=root/'src/main/java/dev/redstoneengineering/RedstoneEngineering.java'
text=main.read_text()
checks={
'proportional codec':'PNEUMATIC_PROPORTIONAL_VALVE_CODEC',
'relief codec':'PNEUMATIC_RELIEF_VALVE_CODEC',
'cylinder codec':'PNEUMATIC_CYLINDER_CODEC',
'proportional block':'PNEUMATIC_PROPORTIONAL_VALVE = BLOCKS.registerBlock',
'relief block':'PNEUMATIC_RELIEF_VALVE = BLOCKS.registerBlock',
'cylinder block':'PNEUMATIC_CYLINDER = BLOCKS.registerBlock',
}
for label,needle in checks.items():
    if needle not in text: raise SystemExit(f'FAIL: missing {label}')
for name in ['PneumaticProportionalValveBlock.java','PneumaticReliefValveBlock.java','PneumaticCylinderBlock.java']:
    if not (root/'src/main/java/dev/redstoneengineering/block'/name).exists(): raise SystemExit('FAIL: missing '+name)
net=(root/'src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java').read_text()
for needle in ['PneumaticProportionalValveBlock','PneumaticReliefValveBlock','PneumaticCylinderBlock','pneumatic_relief']:
    if needle not in net: raise SystemExit('FAIL: pneumatic network missing '+needle)
for rid in ['pneumatic_proportional_valve','pneumatic_relief_valve','pneumatic_cylinder']:
    for rel in [f'src/main/resources/assets/redstoneengineering/blockstates/{rid}.json',f'src/main/resources/assets/redstoneengineering/models/block/{rid}.json',f'src/main/resources/assets/redstoneengineering/models/item/{rid}.json',f'src/main/resources/data/redstoneengineering/recipe/{rid}.json',f'src/main/resources/data/redstoneengineering/loot_table/blocks/{rid}.json']:
        p=root/rel
        if not p.exists(): raise SystemExit('FAIL: missing '+rel)
        json.loads(p.read_text())
print('RSE Alpha 1.0.3 pneumatic compile hotfix verification: PASS')
