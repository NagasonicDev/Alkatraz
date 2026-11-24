"""Rescale armor _3d model face UVs to the 16-grid.

MC 26.1's cuboid item bake scales face UVs by texture_px/16, so UVs must
live in [0..16] (texture_size is a Blockbench-only field, ignored by MC).
Our models were authored with UVs in texture-pixel space (up to 32/64),
which made every _3d model fail to bake -> null textures. Dividing UVs by
texture_size/16 keeps the exact same texel sampling on the same textures.
"""
import json
import glob

for path in sorted(glob.glob(
        'resource-packs/Alkatraz-Modern/assets/alkatraz/models/item/*_3d.json')):
    with open(path) as f:
        model = json.load(f)
    ts_w, ts_h = model['texture_size']
    fx, fy = 16 / ts_w, 16 / ts_h
    for el in model['elements']:
        for face in el['faces'].values():
            uv = face.get('uv')
            if uv:
                face['uv'] = [round(u * fx, 4) if i % 2 == 0 else round(u * fy, 4)
                              for i, u in enumerate(uv)]
    with open(path, 'w') as f:
        json.dump(model, f, indent=2)
        f.write('\n')
    print('rescaled', path)
