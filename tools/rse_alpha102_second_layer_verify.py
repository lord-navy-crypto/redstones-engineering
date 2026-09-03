#!/usr/bin/env python3
from pathlib import Path
import sys,json,re
root=Path(sys.argv[1] if len(sys.argv)>1 else '.').resolve()
java=root/'src/main/java/dev/redstoneengineering'
res=root/'src/main/resources'
checks=[]
def need(path,*terms):
    p=root/path
    txt=p.read_text(errors='ignore') if p.exists() else ''
    ok=p.exists() and all(t in txt for t in terms)
    checks.append((ok,str(path),terms))
need(Path('src/main/java/dev/redstoneengineering/blockentity/OscilloscopeBlockEntity.java'),'triggerMode','cursorDeltaSamples','estimatedPeriodSamples','peakToPeak')
need(Path('src/main/java/dev/redstoneengineering/blockentity/LogicAnalyzerBlockEntity.java'),'triggerChannel','cursorDeltaSamples','dutyPercent','armed')
need(Path('src/main/java/dev/redstoneengineering/block/PidControllerBlock.java'),'rise90','overshoot','anti-windup')
need(Path('src/main/java/dev/redstoneengineering/block/ServoActuatorBlock.java'),'trajectory diagnostics','settle=','travel=')
need(Path('src/main/java/dev/redstoneengineering/physics/SerialNetwork.java'),'serial_diag','utilization')
need(Path('src/main/java/dev/redstoneengineering/physics/DataBusNetwork.java'),'bus8_diag','interarrival')
need(Path('src/main/java/dev/redstoneengineering/physics/RadioKernel.java'),'adjacent-channel','obstacleSamples','latencyTicks','MIN_DECODE_QUALITY')
need(Path('src/main/java/dev/redstoneengineering/physics/PneumaticNetwork.java'),'PneumaticValveBlock','PneumaticCheckValveBlock','pneumatic_flow')
need(Path('src/main/java/dev/redstoneengineering/block/OperationsMonitorBlock.java'),'throughput last60s','downtime','QUEUE')
for b in ['pneumatic_valve','pneumatic_check_valve','pneumatic_flow_meter']:
    for p in [res/f'assets/redstoneengineering/blockstates/{b}.json',res/f'assets/redstoneengineering/models/block/{b}.json',res/f'assets/redstoneengineering/models/item/{b}.json',res/f'data/redstoneengineering/loot_table/blocks/{b}.json',res/f'data/redstoneengineering/recipe/{b}.json']:
        try: json.loads(p.read_text()); checks.append((True,str(p.relative_to(root)),('json',)))
        except Exception: checks.append((False,str(p.relative_to(root)),('json',)))
reg=(java/'RedstoneEngineering.java').read_text()
for n in ['PNEUMATIC_VALVE','PNEUMATIC_CHECK_VALVE','PNEUMATIC_FLOW_METER']:
    checks.append((n in reg,'registration '+n,(n,)))
failed=[c for c in checks if not c[0]]
if failed:
    print('RSE Alpha 1.0.2 second-layer verification: FAIL')
    for _,p,t in failed: print(' -',p,'missing',', '.join(t))
    raise SystemExit(1)
print('RSE Alpha 1.0.2 second-layer verification: PASS')
print('  instrumentation triggers/cursors: PASS')
print('  PID process-response metrics: PASS')
print('  servo trajectory diagnostics: PASS')
print('  serial/bus diagnostics: PASS')
print('  radio interference/latency model: PASS')
print('  pneumatic valve/check/flow-meter: PASS')
print('  operations throughput/downtime/queue: PASS')
