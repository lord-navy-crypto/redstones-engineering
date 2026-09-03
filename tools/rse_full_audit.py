#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]; warnings=[]; ok=[]
def read(rel):
 p=ROOT/rel
 if not p.exists(): errors.append(f'missing {rel}'); return ''
 return p.read_text(errors='ignore')

def require(rel,token,msg):
 if token not in read(rel): errors.append(msg)
def forbid(rel,token,msg):
 if token in read(rel): errors.append(msg)

# Kernel invariants learned from alpha.6/7 failures.
nk=read('src/main/java/dev/redstoneengineering/physics/NetworkKernel.java')
m=re.search(r'MAX_NODES\s*=\s*(\d+)',nk)
if not m or int(m.group(1))!=128: errors.append('NetworkKernel.MAX_NODES must remain 128')
require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java','if (d.getAxis() == Direction.Axis.Y) return false;','surface trace must reject Y only')
for bad in ['if(!d.getAxis()!=Direction.Axis.Y)','if (d.getAxis() != Direction.Axis.Y) return false;']:
 forbid('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java',bad,f'bad trace-axis expression present: {bad}')
require('src/main/java/dev/redstoneengineering/physics/DomainDriverRegistry.java','activeClaims','driver registry missing')
for net in ['DomainNetwork.java','RedstoneCableNetwork.java']:
 require('src/main/java/dev/redstoneengineering/physics/'+net,'hasChunkAt',net+' lacks loaded chunk guard')

# Redstone backwards query convention + transient runtime state.
require('src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java','isEngineeringPort(state, direction.getOpposite())','redstone physical port mapping regressed')
require('src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java','direction == outputSide(state).getOpposite()','redstone output query direction regressed')
for fn,oldprops in {
 'PwmControllerBlock.java':['PHASE ='],
 'SampleHoldBlock.java':['HELD =','TRIGGERED ='],
 'EdgeDetectorBlock.java':['LAST =','REMAINING ='],
 'PulseShaperBlock.java':['LAST =','REMAINING ='],
}.items():
 s=read('src/main/java/dev/redstoneengineering/block/'+fn)
 if 'RuntimeIntStore' not in s: errors.append(fn+' must use RuntimeIntStore')
 for token in oldprops:
  if token in s: errors.append(fn+' still stores transient property '+token)
require('src/main/java/dev/redstoneengineering/block/PwmControllerBlock.java','if (inhibited) output = 0;','PWM inhibit must force OFF')
require('src/main/java/dev/redstoneengineering/block/SignalAnalyzerBlock.java','return EngineeringSignal.clamp(targetState.getValue(RedStoneWireBlock.POWER));','Analyzer must read dust node itself')

# Domain correctness repairs.
for token in ['CopperCableJunctionBlock.voltage','CopperSeriesResistorBlock.outputVoltage','CopperCapacitorBlock.outputVoltage','CopperFuseBlock.outputVoltage','InductionCoilBlock.outputVoltage']:
 require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java',token,'Copper sampler missing '+token)
require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java','DRIVER', 'placeholder') if False else None
require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java','DomainDriverRegistry.activeClaims','processed domain driver conflicts not checked')
require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java','block instanceof OpticalReceiverBlock || block instanceof OpticalEmitterBlock','optical terminal rule missing')
require('src/main/java/dev/redstoneengineering/physics/DomainNetwork.java','block instanceof CopperResistiveLoadBlock || block instanceof ElectromagnetBlock','copper terminal rule missing')
for f in ['CopperCableJunctionBlock.java','OpticalFiberJunctionBlock.java']:
 require('src/main/java/dev/redstoneengineering/block/'+f,'RuntimeIntStore',f+' must retain runtime payload')
require('src/main/java/dev/redstoneengineering/instrument/InstrumentNetwork.java','seenProbes','probe deduplication missing')
require('src/main/java/dev/redstoneengineering/block/QuartzTimingLineBlock.java','Math.min(4096,periodTicks)','Quartz runtime period must preserve processed long clocks')

# Registration/resources.
main=read('src/main/java/dev/redstoneengineering/RedstoneEngineering.java')
ids=sorted(set(re.findall(r'registerBlock\(\s*"([a-z0-9_]+)"',main,re.S)))
if len(ids)<70: warnings.append(f'only {len(ids)} registered blocks discovered')
assets=ROOT/'src/main/resources/assets/redstoneengineering'; data=ROOT/'src/main/resources/data/redstoneengineering'
for bid in ids:
 for rel in [assets/'blockstates'/f'{bid}.json', assets/'models/item'/f'{bid}.json', data/'loot_table/blocks'/f'{bid}.json', data/'recipe'/f'{bid}.json']:
  if not rel.exists(): errors.append('registered block missing resource '+str(rel.relative_to(ROOT)))
for lang in ['en_us','zh_cn']:
 p=assets/'lang'/f'{lang}.json'
 if p.exists():
  obj=json.loads(p.read_text())
  for bid in ids:
   if f'block.redstoneengineering.{bid}' not in obj: errors.append(f'{lang} missing name for {bid}')

# JSON/model references and recipe 1.21.1 ingredient objects.
json_count=0
for p in (ROOT/'src/main/resources').rglob('*.json'):
 json_count+=1
 try: json.loads(p.read_text())
 except Exception as e: errors.append(f'JSON {p.relative_to(ROOT)}: {e}')

def walk_models(x, out):
 if isinstance(x,dict):
  if isinstance(x.get('model'),str): out.append(x['model'])
  for v in x.values(): walk_models(v,out)
 elif isinstance(x,list):
  for v in x: walk_models(v,out)
for p in (assets/'blockstates').glob('*.json'):
 try:o=json.loads(p.read_text())
 except:continue
 refs=[]; walk_models(o,refs)
 for ref in refs:
  if ref.startswith('redstoneengineering:block/'):
   name=ref.split('block/',1)[1]
   if not (assets/'models/block'/f'{name}.json').exists(): errors.append(f'{p.name} missing model {ref}')
for p in (assets/'models/item').glob('*.json'):
 o=json.loads(p.read_text()); parent=o.get('parent','')
 if parent.startswith('redstoneengineering:block/') and not (assets/'models/block'/f"{parent.split('block/',1)[1]}.json").exists(): errors.append(f'{p.name} missing parent {parent}')
for p in (assets/'models/block').glob('*.json'):
 o=json.loads(p.read_text())
 for ref in o.get('textures',{}).values():
  if isinstance(ref,str) and ref.startswith('redstoneengineering:block/') and not (assets/'textures/block'/f"{ref.split('block/',1)[1]}.png").exists(): errors.append(f'{p.name} missing texture {ref}')
for p in (data/'recipe').glob('*.json'):
 o=json.loads(p.read_text())
 for k,v in o.get('key',{}).items():
  if isinstance(v,str): errors.append(f'{p.name} key {k} uses legacy string ingredient')
 for v in o.get('ingredients',[]):
  if isinstance(v,str): errors.append(f'{p.name} uses legacy string ingredient')

# Simple Java structural/syntax regression tokens.
java=list((ROOT/'src/main/java').rglob('*.java'))
for p in java:
 s=p.read_text(errors='ignore')
 if s.count('{')!=s.count('}'): errors.append(f'unbalanced braces {p.relative_to(ROOT)}')
for bad in ['if(!d.getAxis()!=Direction.Axis.Y)','adjacentCopperLevel(l,p);\n        DomainNetwork.recomputeCopper']:
 for p in java:
  if bad in p.read_text(errors='ignore'): errors.append(f'known bad pattern {bad} in {p.relative_to(ROOT)}')

print('RSE alpha.8.0.2 FULL AUDIT')
print(f'  registered blocks: {len(ids)}')
print(f'  Java files: {len(java)}')
print(f'  JSON files parsed: {json_count}')
for w in warnings: print('  WARN:',w)
if errors:
 print(f'  FAIL: {len(errors)} issue(s)')
 for e in errors: print('   -',e)
 sys.exit(1)
print('  PASS: full static audit complete')
