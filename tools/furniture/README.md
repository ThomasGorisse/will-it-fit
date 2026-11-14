# Furniture models

Regenerate (Blender 5.2): `blender -b --factory-startup --python tools/furniture/make_furniture.py -- --out app/src/main/assets/models`
Flags: `--only sofa3,bed` (`--only ""` = no furniture), `--no-preview`, `--floor` (floor.glb + floor_night.glb), `--rug tools/furniture/rug/rug-knit.jpg` (rug.glb), `--thumbs app/src/main/res/drawable-nodpi` (preset_*.webp + previews/thumbnails.png; needs cwebp).
All geometry is procedural, written by us: no external assets. The one image is the rug's knit, generated with ChatGPT Image (`rug/prompt.txt`).

- One GLB per preset: metres, +Y up, front +Z, origin bottom-centre, bbox = preset size (rescaled exactly). Floor: 6.5 × 6 m oak planks, top at y = 0, centred on (-0.05, 1.5).
- Materials by name, base colour only: `fabric`/`fabric_soft` (same deep teal 225254, roughness 1, KHR_materials_sheen + KHR_materials_specular; the app recolours baseColor only), `wood`, `oak`, `steel`, `enamel`, `dark`, `metal`, `white`, `glass`, `cardboard`, floor `oak_1`…`oak_4`.
- Analytic normals, single-sided faces, no UVs, textures (except rug.glb: its knit photo and a normal map derived from it, 2.2 × 1.5 m, bottom at y = 0), cameras, lights or animations; ≤ 400 KB, ≤ 25k triangles. Thumbnails: 288 px, transparent, Cycles with a shadow catcher, < 40 KB.
- Previews land in `tools/furniture/previews/`; each run prints a size / bbox / triangle / extension table read back from the GLBs.
