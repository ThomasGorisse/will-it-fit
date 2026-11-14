# Furniture models

Regenerate (Blender 5.2, ~1 min per model with previews):
`blender -b --factory-startup --python tools/furniture/make_furniture.py -- --out app/src/main/assets/models`
(add `--only sofa3,bed` or `--no-preview`). All geometry is procedural, written by us: no external assets.

- One GLB per preset: metres, +Y up, front +Z, origin at bottom-centre, bbox = preset size (rescaled exactly).
- Materials by name, base colour only: `fabric` (recoloured to the hero teal), `fabric_soft`, `wood`, `dark`, `metal`, `white`, `glass`, `cardboard`.
- Analytic smooth normals, single-sided faces, no UVs, textures, cameras, lights or animations; ≤ 400 KB, ≤ 25k triangles.
- Previews land in `tools/furniture/previews/`; the run ends with a size / bbox / triangle table read back from the GLBs.
