#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
errors=[]
notes=[]
ALPHA8_BLOCKS = [
    'lapis_temperature_transducer',
    'lapis_magnetic_transducer',
    'lapis_optical_transducer',
    'lapis_voltage_transducer',
    'lapis_precision_range_sensor',
    'lapis_to_redstone_quantizer',
    'redstone_to_lapis_scaler',
    'quartz_triggered_lapis_sampler',
]

def need(path):
    p=ROOT/path
    if not p.exists(): errors.append(f"missing: {path}")
    return p

# 1. Critical semantic regression checks.
dn=need(Path('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java'))
if dn.exists():
    t=dn.read_text()
    if 'if (d.getAxis() == Direction.Axis.Y) return false;' not in t:
        errors.append('surface traces are not explicitly rejecting only the Y axis')
    for bad in ['if(!d.getAxis()!=Direction.Axis.Y)', 'if (d.getAxis() != Direction.Axis.Y) return false;']:
        if bad in t: errors.append(f'bad trace-axis expression still present: {bad}')
    if 'NetworkKernel.MAX_NODES' not in t:
        errors.append('DomainNetwork is not using shared NetworkKernel.MAX_NODES')

rn=need(Path('src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java'))
if rn.exists():
    t=rn.read_text()
    if 'NetworkKernel.MAX_NODES' not in t:
        errors.append('RedstoneCableNetwork is not using shared NetworkKernel.MAX_NODES')
    if 'hasChunkAt' not in t:
        errors.append('RedstoneCableNetwork lacks loaded-chunk guard')

nk=need(Path('src/main/java/dev/redstoneengineering/physics/NetworkKernel.java'))
if nk.exists():
    m=re.search(r'MAX_NODES\s*=\s*(\d+)', nk.read_text())
    if not m or int(m.group(1)) != 128:
        errors.append('NetworkKernel.MAX_NODES must remain 128 in alpha.8')

# 2. Transmission-state sanity: live payload should be runtime, not cable block properties.
for rel in [
    'src/main/java/dev/redstoneengineering/block/RedstoneSignalCableBlock.java',
    'src/main/java/dev/redstoneengineering/block/CopperWireBlock.java',
    'src/main/java/dev/redstoneengineering/block/OpticalFiberBlock.java',
    'src/main/java/dev/redstoneengineering/block/LapisSignalLineBlock.java',
    'src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java',
    'src/main/java/dev/redstoneengineering/block/AmethystResonanceDustBlock.java',
]:
    p=need(Path(rel))
    if p.exists() and 'RuntimeIntStore' not in p.read_text():
        errors.append(f'{rel}: expected runtime payload storage')


# 2b. Alpha.8 sensor/transducer registration and resource gate.
re_main=need(Path('src/main/java/dev/redstoneengineering/RedstoneEngineering.java'))
if re_main.exists():
    main_text=re_main.read_text()
    for name in ALPHA8_BLOCKS:
        token=name.upper()
        if name not in main_text:
            errors.append(f'alpha.8 block not registered: {name}')

for name in ALPHA8_BLOCKS:
    for rel in [
        Path(f'src/main/resources/assets/redstoneengineering/blockstates/{name}.json'),
        Path(f'src/main/resources/assets/redstoneengineering/models/block/{name}.json'),
        Path(f'src/main/resources/assets/redstoneengineering/models/item/{name}.json'),
        Path(f'src/main/resources/assets/redstoneengineering/textures/block/{name}.png'),
        Path(f'src/main/resources/data/redstoneengineering/loot_table/blocks/{name}.json'),
        Path(f'src/main/resources/data/redstoneengineering/recipe/{name}.json'),
    ]:
        need(rel)

# Live alpha.8 transducer measurements must use RuntimeIntStore rather than large dynamic properties.
base=need(Path('src/main/java/dev/redstoneengineering/block/AbstractLapisTransducerBlock.java'))
if base.exists():
    text=base.read_text()
    if 'RuntimeIntStore' not in text:
        errors.append('alpha.8 transducer base is not using RuntimeIntStore')
    if 'SensorModel' not in text:
        errors.append('alpha.8 transducer base is not using SensorModel')

sensor=need(Path('src/main/java/dev/redstoneengineering/physics/SensorModel.java'))
if sensor.exists():
    t=sensor.read_text()
    for required in ['samplePeriod', 'noiseAmplitude', 'resolutionStep', 'latencySamples']:
        if required not in t: errors.append(f'SensorModel missing {required}')

# 3. JSON parse check.
json_count=0
for p in ROOT.joinpath('src/main/resources').rglob('*.json'):
    json_count += 1
    try: json.loads(p.read_text())
    except Exception as e: errors.append(f'JSON error {p.relative_to(ROOT)}: {e}')
notes.append(f'parsed {json_count} JSON files')

# 4. Resource reference sanity for blockstate model refs and block model texture refs.
assets=ROOT/'src/main/resources/assets/redstoneengineering'
if assets.exists():
    for p in (assets/'blockstates').glob('*.json'):
        obj=json.loads(p.read_text())
        refs=[]
        def walk(x):
            if isinstance(x,dict):
                if isinstance(x.get('model'),str): refs.append(x['model'])
                for v in x.values(): walk(v)
            elif isinstance(x,list):
                for v in x: walk(v)
        walk(obj)
        for ref in refs:
            if ref.startswith('redstoneengineering:block/'):
                name=ref.split('/',1)[1]
                if not (assets/'models/block'/f'{name}.json').exists():
                    errors.append(f'missing block model for {p.name}: {ref}')
notes.append('checked blockstate model references')

print('RSE alpha.8 static verification')
for n in notes: print('  OK:', n)
if errors:
    print(f'  FAIL: {len(errors)} issue(s)')
    for e in errors: print('   -',e)
    sys.exit(1)
print('  PASS: static verification complete')
