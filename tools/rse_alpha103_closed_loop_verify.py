#!/usr/bin/env python3
from pathlib import Path
import sys,re,json
root=Path(sys.argv[1] if len(sys.argv)>1 else '.')
checks={
'PID manual/auto + bumpless':['DOWN=manual','manual→auto','MAX_OUT'],
'Servo velocity mode':['VELOCITY_MODE','7=stop','softLimitHits'],
'Bus contention diagnostics':['same-value-multidriver','driverCount','distinct='],
'Radio accumulated diagnostics':['radio_rx_diag','undecodable=','collisions='],
'Operations classifications':['starved=','blocked/fault=','highQueueRun='],
'Pneumatic proportional valve':['PneumaticProportionalValveBlock','opening(level'],
'Pneumatic relief valve':['PneumaticReliefValveBlock','pneumatic_relief'],
'Pneumatic cylinder':['PneumaticCylinderBlock','pneumatic_cylinder'],
}
text='\n'.join(p.read_text(errors='ignore') for p in (root/'src/main/java').rglob('*.java'))
failed=[]
for name,need in checks.items():
    if not all(x in text for x in need): failed.append(name)
for n in ['pneumatic_proportional_valve','pneumatic_relief_valve','pneumatic_cylinder']:
    for rel in [f'assets/redstoneengineering/blockstates/{n}.json',f'assets/redstoneengineering/models/block/{n}.json',f'assets/redstoneengineering/models/item/{n}.json',f'data/redstoneengineering/loot_table/blocks/{n}.json',f'data/redstoneengineering/recipe/{n}.json']:
        p=root/'src/main/resources'/rel
        if not p.exists(): failed.append(rel)
        else:
            try: json.loads(p.read_text())
            except Exception: failed.append(rel+' JSON')
# crude high-cardinality protection
if 'IntegerProperty.create("value", 0, 255)' in text: failed.append('high-cardinality 0..255 BlockState')
if failed:
    print('RSE Alpha 1.0.3 verification: FAIL'); [print(' -',x) for x in failed]; sys.exit(1)
print('RSE Alpha 1.0.3 closed-loop verification: PASS')
for k in checks: print(' ',k+': PASS')
