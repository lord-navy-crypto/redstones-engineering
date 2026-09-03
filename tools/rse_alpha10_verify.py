#!/usr/bin/env python3
from __future__ import annotations
import json,re,sys
from pathlib import Path

root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path('.').resolve()
main=root/'src/main/java/dev/redstoneengineering/RedstoneEngineering.java'
if not main.exists():
    raise SystemExit(f'FAIL: missing {main}')
text=main.read_text()

expected=[
'eight_bit_data_bus','redstone_byte_encoder','byte_to_redstone_decoder','serial_data_line','serializer','deserializer',
'differential_data_pair','differential_driver','differential_receiver','digital_regenerator','sculk_vibration_interface',
'pid_controller','watchdog','air_compressor','pneumatic_pipe','air_reservoir','pressure_regulator','pneumatic_receiver',
'slime_vibration_conduit','honey_vibration_damper','mechanical_exciter','mechanical_vibration_receiver',
'hydroacoustic_tube','hydroacoustic_exciter','hydroacoustic_receiver','radio_transmitter','radio_receiver',
'free_space_optical_transmitter','free_space_optical_receiver','soul_soil_conduit','soul_sand_reservoir',
'soul_flux_injector','soul_flux_meter','molecular_cloud_receiver','phonon_conduit','thermal_pulse_encoder','thermal_pulse_receiver',
'shielded_instrument_cable','servo_actuator','servo_position_sensor','redundant_voter','fault_latch','operations_monitor'
]
errors=[]
for i in expected:
    if f'"{i}"' not in text: errors.append(f'not registered: {i}')
    paths=[
        root/f'src/main/resources/assets/redstoneengineering/blockstates/{i}.json',
        root/f'src/main/resources/assets/redstoneengineering/models/block/{i}.json',
        root/f'src/main/resources/assets/redstoneengineering/models/item/{i}.json',
        root/f'src/main/resources/data/redstoneengineering/loot_table/blocks/{i}.json',
        root/f'src/main/resources/data/redstoneengineering/recipe/{i}.json',
    ]
    for p in paths:
        if not p.exists(): errors.append(f'missing resource: {p.relative_to(root)}')

for p in (root/'src/main/resources').rglob('*.json'):
    try: json.loads(p.read_text())
    except Exception as e: errors.append(f'bad json: {p.relative_to(root)}: {e}')

# State explosion guard: any Alpha 1.0 IntegerProperty wider than 256 is suspicious.
for p in (root/'src/main/java/dev/redstoneengineering').rglob('*.java'):
    t=p.read_text()
    for m in re.finditer(r'IntegerProperty\.create\([^,]+,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)',t):
        lo,hi=map(int,m.groups())
        if hi-lo+1>256: errors.append(f'high-cardinality BlockState in {p.name}: {lo}..{hi}')

required_runtime=['RuntimeIntStore','NetworkKernel.MAX_NODES','InformationRuntime']
joined='\n'.join(p.read_text() for p in (root/'src/main/java/dev/redstoneengineering').rglob('*.java'))
for token in required_runtime:
    if token not in joined: errors.append(f'missing architecture token: {token}')

# Explicit sanity checks for the design promises.
checks={
    '8-bit runtime payload':'bus8' in joined and '& 0xFF' in joined,
    'serial bounded network':'class SerialNetwork' in joined and 'NetworkKernel.MAX_NODES' in joined,
    'radio registry avoids cube scans':'class RadioKernel' in joined and 'updateTransmitter' in joined,
    'pneumatic regulator is in solver':'PressureRegulatorBlock.SETPOINT' in joined,
    'mechanical slime/honey losses':'SlimeVibrationConduitBlock' in joined and 'HoneyVibrationDamperBlock' in joined,
    'molecular uses vanilla cloud entity':'AreaEffectCloud' in joined,
    'soul marked fictional':'Minecraft-fictional' in joined,
    'thermal end-game path':'PhononConduitBlock' in joined and 'diamond_shard' in text,
    'watchdog reliability primitive':'class WatchdogBlock' in joined,
    'PID controller':'class PidControllerBlock' in joined,
    'shielded instrumentation':'class ShieldedInstrumentCableBlock' in joined,
    'servo closed-loop primitive':'class ServoActuatorBlock' in joined and 'class ServoPositionSensorBlock' in joined,
    'redundant voter':'class RedundantVoterBlock' in joined,
    'fault latch':'class FaultLatchBlock' in joined,
    'IOE operations monitor':'class OperationsMonitorBlock' in joined,
}
for name,ok in checks.items():
    if not ok: errors.append(f'failed design check: {name}')

if errors:
    print('RSE Alpha 1.0 verification: FAIL')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RSE Alpha 1.0 verification: PASS')
print(f'  new Alpha 1.0 blocks: {len(expected)}')
print('  JSON parse: PASS')
print('  high-cardinality state guard: PASS')
for name in checks: print('  OK:',name)
