import json, glob, os, re, struct

base = 'resource-packs/Alkatraz-Modern/assets'
SETS = ['apprentice', 'scholar', 'enchanter', 'archmage']
SLOTS = ['hat', 'robe', 'leggings', 'boots']
KEYS = [f'{s}_{sl}' for s in SETS for sl in SLOTS]

def resolve(tex, ns_default='minecraft'):
    """Resolve a texture ref like 'alkatraz:item/x' or 'item/x' to a file path."""
    if ':' in tex:
        ns, path = tex.split(':', 1)
    else:
        ns, path = ns_default, tex
    return f'{base}/{ns}/textures/{path}.png'

def png_size(path):
    with open(path, 'rb') as f:
        data = f.read(33)
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    return struct.unpack('>II', data[16:24])

problems = []

# 1) All leather item defs, equipment json, armor item models must parse
files = (glob.glob(base + '/minecraft/items/leather_*.json')
         + [f'{base}/alkatraz/equipment/{s}.json' for s in SETS]
         + [f'{base}/alkatraz/models/item/{k}.json' for k in KEYS]
         + [f'{base}/alkatraz/models/item/{k}_3d.json' for k in KEYS])
for f in files:
    if not os.path.exists(f):
        problems.append(f'MISSING FILE: {f}')
        continue
    try:
        json.load(open(f))
    except Exception as e:
        problems.append(f'BAD JSON: {f}: {e}')

# 2) _3d models: texture refs resolve; UV max <= texture_size; texture_size matches png size
for k in KEYS:
    f = f'{base}/alkatraz/models/item/{k}_3d.json'
    if not os.path.exists(f):
        continue
    m = json.load(open(f))
    ts = m.get('texture_size', [16, 16])
    for key, tex in m.get('textures', {}).items():
        if key == 'particle':
            continue
        p = resolve(tex)
        if not os.path.exists(p):
            problems.append(f'{k}_3d: texture {tex} -> {p} MISSING')
        else:
            px, py = png_size(p)
            if (px, py) != tuple(ts):
                problems.append(f'{k}_3d: png {p} is {px}x{py}, texture_size={ts}')
    # UV check
    for el in m.get('elements', []):
        maxuv = 0
        for face in el.get('faces', {}).values():
            uv = face.get('uv', [])
            if len(uv) == 4:
                maxuv = max(maxuv, uv[2], uv[3])
        if maxuv > ts[0] + 1e-9 or maxuv > ts[1] + 1e-9:
            problems.append(f'{k}_3d: element UV max {maxuv} exceeds texture_size {ts}')

# 3) 2D models: layer0 ref resolves
for k in KEYS:
    f = f'{base}/alkatraz/models/item/{k}.json'
    if os.path.exists(f):
        m = json.load(open(f))
        for key, tex in m.get('textures', {}).items():
            if key == 'particle':
                continue
            p = resolve(tex)
            if not os.path.exists(p):
                problems.append(f'{k}: texture {tex} -> {p} MISSING')

# 4) Equipment jsons: check layer textures resolve for humanoid/humanoid_leggings
for s in SETS:
    f = f'{base}/alkatraz/equipment/{s}.json'
    if os.path.exists(f):
        m = json.load(open(f))
        for layer_name, layer in m.get('layers', {}).items():
            for entry in layer:
                tex = entry.get('texture', '')
                # humanoid/humanoid_leggings suffixes are appended by vanilla
                for suffix in ('humanoid', 'humanoid_leggings'):
                    p = f'{base}/alkatraz/textures/entity/equipment/{suffix}/{s}.png'
                    if not os.path.exists(p):
                        problems.append(f'equipment {s}: missing {p}')

# 5) YAML cmd vs item-def thresholds
def yaml_cmds():
    import yaml
    cmds = {}
    for k in KEYS:
        with open(f'core/src/main/resources/magic/items/{k}.yml') as fh:
            d = yaml.safe_load(fh)
        cmds[k] = d.get('custom_model_data') or d.get('visual', {}).get('custom_model_data')
    return cmds

try:
    import yaml
    cmds = yaml_cmds()
    # build threshold map from leather item defs
    thr = {}
    for f in glob.glob(base + '/minecraft/items/leather_*.json'):
        m = json.load(open(f))
        r = m.get('model', {})
        for e in r.get('entries', []):
            thr[e['threshold']] = e.get('model', {})
    print('thresholds in pack:', sorted(thr))
    print('yaml cmds:', sorted(cmds.items(), key=lambda x: x[1]))
except Exception as e:
    print('yaml check skipped:', e)

# 6) pack.mcmeta
print(open('resource-packs/Alkatraz-Modern/pack.mcmeta').read())

print('\nPROBLEMS:' if problems else '\nNO PROBLEMS FOUND')
for p in problems:
    print(' -', p)
