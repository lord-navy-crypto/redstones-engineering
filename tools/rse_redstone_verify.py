from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
checks=[]
def require(path,text,label):
    s=(root/path).read_text()
    ok=text in s
    checks.append((ok,label))
def forbid(path,text,label):
    s=(root/path).read_text()
    ok=text not in s
    checks.append((ok,label))
require(Path('src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java'),
        'isEngineeringPort(state, direction.getOpposite())',
        'Directional redstone port query maps to physical side')
require(Path('src/main/java/dev/redstoneengineering/block/DirectionalSignalBlock.java'),
        'direction == outputSide(state).getOpposite()',
        'Directional output uses backwards redstone query convention')
for name in ['EdgeDetectorBlock.java','PulseShaperBlock.java']:
    require(Path('src/main/java/dev/redstoneengineering/block')/name,
            'if (remaining > 0)', f'{name} schedules final clear tick')
forbid(Path('src/main/java/dev/redstoneengineering/block')/'SignalTapBlock.java',
       'direction == outputSide(state) || direction == leftOf(facing)',
       'Signal Tap no longer emits on reversed sides')
require(Path('src/main/java/dev/redstoneengineering/physics/RedstoneCableNetwork.java'),
        'l.updateNeighborsAt(p.relative(t.vanillaSide(n)),t);',
        'Cable output terminal notifies vanilla output neighbor')
failed=False
for ok,label in checks:
    print(('OK  ' if ok else 'FAIL')+': '+label)
    failed |= not ok
if failed:
    sys.exit(1)
print('PASS: redstone processing static verification complete')
