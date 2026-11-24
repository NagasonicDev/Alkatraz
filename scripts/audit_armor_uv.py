import json, glob, os, struct

base = 'resource-packs/Alkatraz-Modern/assets'
SETS = ['apprentice', 'scholar', 'enchanter', 'archmage']
SLOTS = ['hat', 'robe', 'leggings', 'boots']
KEYS = [f'{s}_{sl}' for s in SETS for sl in SLOTS]

def png_size(path):
    with open(path, 'rb') as f:
        data = f.read(33)
    return struct.unpack('>II', data[16:24])

print(f"{'key':22} {'texSize':12} {'png':12} uvMaxU uvMaxV  viol")
for k in KEYS:
    f = f'{base}/alkatraz/models/item/{k}_3d.json'
    m = json.load(open(f))
    ts = m.get('texture_size', [16, 16])
    tex = m['textures']['0']  # e.g. alkatraz:item/armor/apprentice_robe
    ns, path = tex.split(':', 1)
    png = f'{base}/{ns}/textures/{path}.png'
    W, H = png_size(png)
    max_u = max_v = 0
    min_u = min_v = float('inf')
    viol = 0
    for el in m['elements']:
        for fname, face in el['faces'].items():
            u0, v0, u1, v1 = face['uv']
            max_u = max(max_u, u0, u1)
            max_v = max(max_v, v0, v1)
            min_u = min(min_u, u0, u1)
            min_v = min(min_v, v0, v1)
            if u1 > W or v1 > H or u0 < 0 or v0 < 0:
                viol += 1
            # MC 26.1 cuboid bake scales uv by sprite_px/16, so uv must be
            # within [0..16] (texture_size is a Blockbench-only field).
            if u1 > 16 or v1 > 16 or u0 < 0 or v0 < 0:
                viol += 1
    print(f"{k:22} {str(ts):12} {str((W, H)):12} {max_u:7.1f} {max_v:7.1f}  {viol}")
