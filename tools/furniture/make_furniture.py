"""Procedural furniture for Will It Fit (our own geometry, no external assets).

Run headless:
  blender -b --factory-startup --python tools/furniture/make_furniture.py -- --out app/src/main/assets/models
Options after `--`: --only sofa3,bed (--only "" builds no furniture)   --no-preview
  --floor        also writes floor.glb / floor_night.glb (oak parquet) and their previews
  --thumbs DIR   renders the preset thumbnails (288 px transparent webp) into DIR,
                 e.g. app/src/main/res/drawable-nodpi, plus previews/thumbnails.png

Everything is authored in app space (metres, +Y up, front +Z, origin bottom-centre), with
analytic per-vertex normals, then rescaled to the exact preset box, converted to Blender space
(x, -z, y) and exported as GLB with export_yup so glTF space equals app space again.
"""
import json
import math
import os
import struct
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------------------- materials
MATERIALS = {
    #            sRGB hex   rough  metal
    # Upholstery: matte, with glTF sheen (KHR_materials_sheen) and a low specular
    # (KHR_materials_specular) so Filament's ubershader shades it like wool or velvet.
    # fabric_soft is the same teal: the target's cushions match the frame.
    "fabric": ("225254", 1.0, 0.0),
    "fabric_soft": ("225254", 1.0, 0.0),
    "wood": ("C49A6C", 0.55, 0.0),
    "dark": ("2A2B2F", 0.60, 0.0),
    "metal": ("B9BEC5", 0.30, 0.9),
    "white": ("F3F0EA", 0.90, 0.0),
    "glass": ("07090D", 0.08, 0.0),
    "cardboard": ("C8A272", 0.85, 0.0),
    # Objects that keep their own colour (the app only recolours fabric/fabric_soft).
    "oak": ("D3AE80", 0.50, 0.0),
    "steel": ("D9DDE1", 0.36, 0.35),
    "enamel": ("F5F5F2", 0.32, 0.0),
}

# Preset sizes, metres: width (x), height (y), depth (z).
SIZES = {
    "sofa3": (2.20, 0.85, 0.95),
    "sofa2": (1.70, 0.85, 0.90),
    "bed": (1.50, 0.95, 2.05),
    "wardrobe": (1.00, 2.01, 0.60),
    "fridge": (0.60, 1.86, 0.66),
    "washer": (0.60, 0.85, 0.60),
    "tv": (1.23, 0.77, 0.25),
    "bike": (1.78, 1.05, 0.60),
    "stroller": (0.60, 1.05, 0.90),
    "box": (0.60, 0.40, 0.40),
}
ORDER = ["sofa3", "sofa2", "bed", "wardrobe", "fridge", "washer", "tv", "bike", "stroller", "box"]


def srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


FABRICS = ("fabric", "fabric_soft")
SHEEN_TINT = "3A6E70"  # a breath above the cloth; anything lighter reads as cyan on the phone

# Oak parquet (floor.glb) and its dark-theme walnut twin (floor_night.glb): same material names.
# Close tones: the app's sun is strong, and wide steps read as a patchwork, not as oak.
# Greige natural oak: the sun warms it on screen, so the base stays cooler than it looks.
FLOOR_DAY = {"oak_1": ("947E66", 0.62, 0.0), "oak_2": ("8C765E", 0.66, 0.0),
             "oak_3": ("9A846B", 0.60, 0.0), "oak_4": ("88725B", 0.68, 0.0)}
FLOOR_NIGHT = {"oak_1": ("6E5038", 0.55, 0.0), "oak_2": ("684B34", 0.58, 0.0),
               "oak_3": ("735540", 0.52, 0.0), "oak_4": ("634731", 0.60, 0.0)}


def hex_linear(hexa):
    return [srgb_to_linear(int(hexa[i:i + 2], 16) / 255.0) for i in (0, 2, 4)]


def make_material(name, table=MATERIALS):
    hexa, rough, metal = table[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.use_backface_culling = True  # glTF doubleSided = false, like Filament's default
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    rgb = [srgb_to_linear(int(hexa[i:i + 2], 16) / 255.0) for i in (0, 2, 4)]
    bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if name in FABRICS:
        bsdf.inputs["Sheen Weight"].default_value = 0.5
        bsdf.inputs["Sheen Roughness"].default_value = 0.5
        bsdf.inputs["Sheen Tint"].default_value = (*hex_linear(SHEEN_TINT), 1.0)
        bsdf.inputs["Specular IOR Level"].default_value = 0.2
    return m


# ---------------------------------------------------------------------------- vector helpers
def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def mul(a, s): return (a[0] * s, a[1] * s, a[2] * s)
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def norm(a):
    l = math.sqrt(dot(a, a))
    return (0.0, 1.0, 0.0) if l < 1e-12 else (a[0] / l, a[1] / l, a[2] / l)


def rot_x(p, ang):
    c, s = math.cos(ang), math.sin(ang)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def basis(w):
    helper = (0.0, 1.0, 0.0) if abs(w[1]) < 0.9 else (1.0, 0.0, 0.0)
    u = norm(cross(helper, w))
    v = cross(w, u)
    return u, v


# ---------------------------------------------------------------------------- part builder
class Model:
    def __init__(self):
        self.parts = {}  # material -> [verts, normals, faces]

    def add(self, mat, verts, normals, faces):
        P = self.parts.setdefault(mat, [[], [], []])
        base = len(P[0])
        P[0].extend(verts)
        P[1].extend(normals)
        for f in faces:
            # Orient every face outward: geometric normal must agree with its vertex normals.
            a, b, c = verts[f[0]], verts[f[1]], verts[f[2 if len(f) == 3 else 2]]
            g = (0.0, 0.0, 0.0)
            for i in range(len(f)):
                p, q = verts[f[i]], verts[f[(i + 1) % len(f)]]
                g = add(g, ((p[1] - q[1]) * (p[2] + q[2]), (p[2] - q[2]) * (p[0] + q[0]), (p[0] - q[0]) * (p[1] + q[1])))
            nsum = (0.0, 0.0, 0.0)
            for i in f:
                nsum = add(nsum, normals[i])
            if dot(g, nsum) < 0:
                f = list(reversed(f))
            P[2].append([base + i for i in f])

    # Rounded box: exact radius-r rounding (seg steps per 45 deg), optional pillow bulge and tilt.
    def rbox(self, mat, x0, x1, y0, y1, z0, z1, r=0.01, seg=2, mids=(0, 0, 0), bulge=(0, 0, 0), tilt=0.0):
        c = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        h = ((x1 - x0) / 2, (y1 - y0) / 2, (z1 - z0) / 2)
        r = min(r, 0.98 * min(h))
        inner = [hh - r for hh in h]
        coords = []
        for a in range(3):
            lo = [-(inner[a] + r * math.tan(math.radians(45.0 * (seg - k) / seg))) for k in range(seg)]
            n = mids[a] + 2
            mid = [-inner[a] + 2 * inner[a] * i / (n - 1) for i in range(n)] if inner[a] > 1e-6 else [0.0]
            coords.append(lo + mid + [-v for v in reversed(lo)])
        index, verts, normals, faces = {}, [], [], []

        def vid(p):
            key = tuple(round(x, 7) for x in p)
            if key not in index:
                index[key] = len(verts)
                verts.append(p)
            return index[key]

        for a in range(3):
            b, cc = (a + 1) % 3, (a + 2) % 3
            for s in (1, -1):
                ub, uc = coords[b], coords[cc]
                for i in range(len(ub) - 1):
                    for j in range(len(uc) - 1):
                        quad = []
                        for (ii, jj) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                            p = [0.0, 0.0, 0.0]
                            p[a] = s * h[a]
                            p[b] = ub[ii]
                            p[cc] = uc[jj]
                            quad.append(vid(tuple(p)))
                        faces.append(quad if s > 0 else list(reversed(quad)))
        out_v, out_n = [], []
        for p in verts:
            cl = [max(-inner[k], min(inner[k], p[k])) for k in range(3)]
            d = norm(sub(p, cl))
            q = [cl[k] + d[k] * r for k in range(3)]
            n = list(d)
            u = [cl[k] / inner[k] if inner[k] > 1e-6 else 0.0 for k in range(3)]
            for a in range(3):
                if not bulge[a]:
                    continue
                o = [k for k in range(3) if k != a]
                P = (1 - u[o[0]] ** 2) * (1 - u[o[1]] ** 2)
                q[a] += bulge[a] * d[a] * P
                for bi in range(2):
                    b, other = o[bi], o[1 - bi]
                    if inner[b] > 1e-6:
                        dP = (-2 * u[b] / inner[b]) * (1 - u[other] ** 2)
                        n[b] -= bulge[a] * abs(d[a]) * dP
            q, n = tuple(q), norm(tuple(n))
            if tilt:
                q, n = rot_x(q, tilt), rot_x(n, tilt)
            out_v.append(add(q, c))
            out_n.append(n)
        self.add(mat, out_v, out_n, faces)

    def box(self, mat, x0, x1, y0, y1, z0, z1, r=0.004):
        self.rbox(mat, x0, x1, y0, y1, z0, z1, r=r, seg=1)

    # Cylinder / cone from p0 (radius r0) to p1 (radius r1), capped, hard cap edges.
    def cyl(self, mat, p0, p1, r0, r1=None, seg=16, caps=True):
        r1 = r0 if r1 is None else r1
        axis = sub(p1, p0)
        L = math.sqrt(dot(axis, axis))
        w = norm(axis)
        u, v = basis(w)
        verts, normals, faces = [], [], []
        for k in range(seg):
            t = 2 * math.pi * k / seg
            rho = add(mul(u, math.cos(t)), mul(v, math.sin(t)))
            n = norm(add(rho, mul(w, (r0 - r1) / L)))
            verts += [add(p0, mul(rho, r0)), add(p1, mul(rho, r1))]
            normals += [n, n]
        for k in range(seg):
            k2 = (k + 1) % seg
            faces.append([2 * k, 2 * k2, 2 * k2 + 1, 2 * k + 1])
        if caps:
            for end, (p, r, s) in enumerate(((p0, r0, -1), (p1, r1, 1))):
                base = len(verts)
                verts.append(p)
                normals.append(mul(w, s))
                for k in range(seg):
                    t = 2 * math.pi * k / seg
                    verts.append(add(p, mul(add(mul(u, math.cos(t)), mul(v, math.sin(t))), r)))
                    normals.append(mul(w, s))
                for k in range(seg):
                    faces.append([base, base + 1 + k, base + 1 + (k + 1) % seg])
        self.add(mat, verts, normals, faces)

    def torus(self, mat, c, w, R, r, segR=48, segr=12):
        w = norm(w)
        u, v = basis(w)
        verts, normals, faces = [], [], []
        for i in range(segR):
            t = 2 * math.pi * i / segR
            rho = add(mul(u, math.cos(t)), mul(v, math.sin(t)))
            for j in range(segr):
                f = 2 * math.pi * j / segr
                n = add(mul(rho, math.cos(f)), mul(w, math.sin(f)))
                verts.append(add(c, add(mul(rho, R), mul(n, r))))
                normals.append(n)
        for i in range(segR):
            for j in range(segr):
                i2, j2 = (i + 1) % segR, (j + 1) % segr
                faces.append([i * segr + j, i2 * segr + j, i2 * segr + j2, i * segr + j2])
        self.add(mat, verts, normals, faces)

    # Quarter ellipsoid shell (y >= cy, z <= cz), open to the front and the bottom: a pram hood.
    def hood(self, mat, c, rad, thick=0.06, na=24, nb=12):
        k = 1.0 - thick

        def pt(a, b, s):
            d = (math.cos(a), math.sin(a) * math.cos(b), -math.sin(a) * math.sin(b))
            p = add(c, (rad[0] * s * d[0], rad[1] * s * d[1], rad[2] * s * d[2]))
            n = norm((d[0] / rad[0], d[1] / rad[1], d[2] / rad[2]))
            return p, n

        verts, normals, faces = [], [], []
        grid = {}
        for side, s in ((0, 1.0), (1, k)):
            for i in range(na + 1):
                for j in range(nb + 1):
                    p, n = pt(math.pi * i / na, 0.5 * math.pi * j / nb, s)
                    grid[side, i, j] = len(verts)
                    verts.append(p)
                    normals.append(n if side == 0 else mul(n, -1))
        for side in (0, 1):
            for i in range(na):
                for j in range(nb):
                    faces.append([grid[side, i, j], grid[side, i + 1, j], grid[side, i + 1, j + 1], grid[side, i, j + 1]])
        self.add(mat, verts, normals, faces)
        # Rims: front arc (j = 0, faces +z) and bottom arc (j = nb, faces -y).
        for j, rn in ((0, (0, 0, 1)), (nb, (0, -1, 0))):
            verts, normals, faces = [], [], []
            for i in range(na + 1):
                for s in (1.0, k):
                    verts.append(pt(math.pi * i / na, 0.5 * math.pi * j / nb, s)[0])
                    normals.append(rn)
            for i in range(na):
                faces.append([2 * i, 2 * i + 2, 2 * i + 3, 2 * i + 1])
            self.add(mat, verts, normals, faces)


# ---------------------------------------------------------------------------- models
def sofa(W, H, D, seats):
    m = Model()
    hw, hd = W / 2, D / 2
    leg, arm, arm_top, base_top = 0.12, 0.165, 0.625, 0.32
    back_d = 0.17
    for x in (-hw + 0.075, hw - 0.075):
        for z in (-hd + 0.085, hd - 0.085):
            m.cyl("wood", (x, leg + 0.005, z), (x, 0.0, z), 0.026, 0.016, seg=20)
    m.rbox("fabric", -hw + 0.05, hw - 0.05, leg, base_top, -hd + 0.03, hd - 0.012, r=0.03, seg=2)
    for s in (-1, 1):
        x0, x1 = (-hw, -hw + arm) if s < 0 else (hw - arm, hw)
        m.rbox("fabric", x0, x1, leg, arm_top, -hd, hd, r=0.04, seg=3, mids=(1, 2, 3), bulge=(0.004, 0.006, 0.0))
    m.rbox("fabric", -hw + arm * 0.6, hw - arm * 0.6, leg, 0.70, -hd + 0.005, -hd + back_d, r=0.04, seg=3, mids=(4, 2, 1),
           bulge=(0.0, 0.005, 0.004))
    inner0, inner1 = -hw + arm, hw - arm
    cw = (inner1 - inner0) / seats
    g = 0.006
    for i in range(seats):
        x0, x1 = inner0 + i * cw + g, inner0 + (i + 1) * cw - g
        m.rbox("fabric_soft", x0, x1, base_top - 0.005, base_top + 0.145, -hd + back_d - 0.01, hd - 0.004, r=0.05, seg=3,
               mids=(4, 1, 4), bulge=(0.006, 0.016, 0.008))
        m.rbox("fabric_soft", x0, x1, base_top + 0.11, H - 0.012, -hd + back_d - 0.03, -hd + back_d + 0.175, r=0.065, seg=3,
               mids=(4, 4, 1), bulge=(0.006, 0.008, 0.022), tilt=-math.radians(9))
    return m


def bed(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    for x in (-hw + 0.07, hw - 0.07):
        for z in (-hd + 0.14, hd - 0.07):
            m.cyl("wood", (x, 0.105, z), (x, 0.0, z), 0.024, 0.016, seg=20)
    m.rbox("wood", -hw, hw, 0.10, 0.30, -hd + 0.06, hd, r=0.015, seg=2)
    m.rbox("fabric", -hw, hw, 0.10, H - 0.04, -hd, -hd + 0.05, r=0.02, seg=2)
    n = 5
    cw = W / n
    for i in range(n):
        x0, x1 = -hw + i * cw + 0.004, -hw + (i + 1) * cw - 0.004
        m.rbox("fabric", x0, x1, 0.30, H, -hd + 0.035, -hd + 0.10, r=0.03, seg=3, mids=(1, 3, 1), bulge=(0.0, 0.0, 0.012))
    m.rbox("white", -0.70, 0.70, 0.27, 0.50, -hd + 0.105, hd - 0.03, r=0.045, seg=3, mids=(2, 0, 3), bulge=(0.004, 0.008, 0.004))
    m.rbox("fabric_soft", -0.735, 0.735, 0.36, 0.565, 0.42, hd - 0.008, r=0.06, seg=3, mids=(6, 1, 3), bulge=(0.006, 0.01, 0.008))
    m.rbox("fabric_soft", -0.728, 0.728, 0.545, 0.585, 0.43, 0.66, r=0.02, seg=2, mids=(6, 0, 1), bulge=(0.0, 0.006, 0.0))
    for s in (-1, 1):
        cx = s * 0.335
        m.rbox("white", cx - 0.285, cx + 0.285, 0.47, 0.64, -hd + 0.13, -hd + 0.47, r=0.07, seg=3, mids=(4, 1, 4),
               bulge=(0.01, 0.03, 0.01), tilt=-math.radians(14))
    return m


def wardrobe(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    m.box("dark", -hw + 0.02, hw - 0.02, 0.0, 0.075, -hd + 0.01, hd - 0.07)
    m.rbox("wood", -hw, hw, 0.07, H, -hd, hd - 0.045, r=0.01, seg=2)
    g = 0.0025
    for s in (-1, 1):
        x0, x1 = (-hw + 0.004, -g) if s < 0 else (g, hw - 0.004)
        m.rbox("oak", x0, x1, 0.078, H - 0.006, hd - 0.047, hd - 0.025, r=0.005, seg=2)
        x = s * 0.035
        y0, y1 = H * 0.44, H * 0.66
        m.cyl("dark", (x, y0, hd - 0.008), (x, y1, hd - 0.008), 0.0075, seg=12)
        for y in (y0 + 0.02, y1 - 0.02):
            m.cyl("dark", (x, y, hd - 0.026), (x, y, hd - 0.008), 0.005, seg=10)
    return m


def fridge(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    m.box("dark", -hw + 0.03, hw - 0.03, 0.0, 0.04, -hd + 0.02, hd - 0.12)
    m.rbox("steel", -hw, hw, 0.025, H, -hd, hd - 0.085, r=0.03, seg=3)
    split = 0.70
    m.rbox("steel", -hw + 0.001, hw - 0.001, 0.03, split - 0.005, hd - 0.095, hd - 0.035, r=0.025, seg=3, mids=(0, 0, 0))
    m.rbox("steel", -hw + 0.001, hw - 0.001, split + 0.005, H - 0.004, hd - 0.095, hd - 0.035, r=0.025, seg=3)
    x = hw - 0.075
    for y0, y1 in ((split + 0.07, split + 0.62), (split - 0.30, split - 0.07)):
        m.cyl("dark", (x, y0, hd - 0.011), (x, y1, hd - 0.011), 0.011, seg=14)
        for y in (y0 + 0.025, y1 - 0.025):
            m.cyl("dark", (x, y, hd - 0.04), (x, y, hd - 0.011), 0.007, seg=10)
    return m


def washer(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    for x in (-hw + 0.05, hw - 0.05):
        for z in (-hd + 0.05, hd - 0.07):
            m.cyl("dark", (x, 0.0, z), (x, 0.016, z), 0.022, seg=12)
    m.rbox("enamel", -hw, hw, 0.012, H, -hd, hd - 0.035, r=0.022, seg=3)
    m.rbox("white", -hw + 0.012, hw - 0.012, H - 0.125, H - 0.014, hd - 0.045, hd - 0.026, r=0.008, seg=2)
    m.box("white", -hw + 0.03, -0.10, H - 0.11, H - 0.03, hd - 0.03, hd - 0.022, r=0.003)
    m.box("glass", 0.0, 0.13, H - 0.088, H - 0.05, hd - 0.03, hd - 0.0245, r=0.003)
    m.cyl("metal", (0.20, H - 0.069, hd - 0.03), (0.20, H - 0.069, hd - 0.012), 0.03, seg=24)
    cy = 0.39
    m.cyl("white", (0, cy, hd - 0.04), (0, cy, hd - 0.026), 0.215, seg=48)
    m.cyl("glass", (0, cy, hd - 0.04), (0, cy, hd - 0.012), 0.152, 0.135, seg=48)
    m.torus("metal", (0, cy, hd - 0.025), (0, 0, 1), 0.168, 0.023, segR=56, segr=12)
    return m


def tv(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    m.rbox("metal", -0.17, 0.17, 0.0, 0.014, -hd, hd, r=0.006, seg=2)
    m.rbox("metal", -0.035, 0.035, 0.01, 0.20, -0.05, -0.02, r=0.01, seg=2)
    m.rbox("dark", -hw, hw, 0.075, H, -0.016, 0.004, r=0.005, seg=2)
    m.rbox("dark", -0.36, 0.36, 0.16, 0.56, -0.05, -0.01, r=0.025, seg=2)
    m.box("glass", -hw + 0.009, hw - 0.009, 0.086, H - 0.009, 0.0, 0.0048, r=0.0004)
    return m


def bike(W, H, D):
    m = Model()
    R, F = (-0.54, 0.35), (0.54, 0.35)
    BB, ST, HTt, HTb = (-0.06, 0.30), (-0.20, 0.82), (0.40, 0.87), (0.43, 0.72)
    p3 = lambda p, z=0.0: (p[0], p[1], z)
    for c in (R, F):
        m.torus("dark", p3(c), (0, 0, 1), 0.326, 0.024, segR=56, segr=10)
        m.torus("metal", p3(c), (0, 0, 1), 0.298, 0.011, segR=48, segr=8)
        m.cyl("metal", p3(c, -0.045), p3(c, 0.045), 0.022, seg=14)
        for k in range(16):
            t = 2 * math.pi * k / 16
            z = 0.035 if k % 2 else -0.035
            m.cyl("metal", p3(c, z), (c[0] + 0.29 * math.cos(t), c[1] + 0.29 * math.sin(t), 0.0), 0.0018, seg=4, caps=False)
    m.cyl("fabric", p3(BB), p3(ST), 0.017)
    m.cyl("fabric", (ST[0] + 0.01, ST[1] - 0.03, 0), (HTt[0], HTt[1] - 0.04, 0), 0.016)
    m.cyl("fabric", p3(BB), (HTb[0], HTb[1] + 0.02, 0), 0.021)
    m.cyl("fabric", p3(HTb), p3(HTt), 0.022)
    for z in (-0.045, 0.045):
        m.cyl("fabric", p3(BB, z * 0.4), p3(R, z), 0.011, seg=12)
        m.cyl("fabric", (ST[0] + 0.01, ST[1] - 0.04, z * 0.3), p3(R, z), 0.010, seg=12)
        m.cyl("fabric", (HTb[0], HTb[1] + 0.01, z * 0.6), p3(F, z), 0.012, seg=12)
    m.cyl("metal", p3(ST), (-0.23, 0.94, 0), 0.0135)
    m.rbox("dark", -0.37, -0.21, 0.93, 0.98, -0.08, 0.08, r=0.022, seg=2, mids=(1, 0, 1), bulge=(0, 0.006, 0))
    m.rbox("dark", -0.25, -0.09, 0.935, 0.975, -0.032, 0.032, r=0.015, seg=2)
    m.cyl("metal", p3(HTt), (0.37, 1.025, 0), 0.014)
    m.cyl("metal", (0.37, 1.025, -0.20), (0.37, 1.025, 0.20), 0.011, seg=12)
    for s in (-1, 1):
        m.cyl("dark", (0.37, 1.025, s * 0.18), (0.37, 1.025, s * 0.30), 0.017, seg=12)
    m.cyl("metal", p3(BB, 0.045), p3(BB, 0.052), 0.10, seg=32)
    m.cyl("dark", p3(BB, -0.06), p3(BB, 0.06), 0.03, seg=14)
    for s in (-1, 1):
        end = (BB[0] + s * 0.11, BB[1] - s * 0.13)
        m.cyl("dark", p3(BB, s * 0.065), p3(end, s * 0.065), 0.011, seg=8)
        m.box("dark", end[0] - 0.05, end[0] + 0.05, end[1] - 0.012, end[1] + 0.012, s * 0.11 - 0.04, s * 0.11 + 0.04,
              r=0.004)
    return m


def stroller(W, H, D):
    m = Model()
    hd = D / 2
    for z, rw in ((-0.30, 0.13), (0.33, 0.11)):
        for s in (-1, 1):
            x = s * 0.278
            m.torus("dark", (x, rw, z), (1, 0, 0), rw - 0.022, 0.022, segR=40, segr=10)
            m.cyl("white", (x - 0.008, rw, z), (x + 0.008, rw, z), rw - 0.03, seg=32)
            m.cyl("metal", (x - 0.02, rw, z), (x + 0.02, rw, z), 0.03, seg=16)
        m.cyl("metal", (-0.27, rw, z), (0.27, rw, z), 0.008, seg=8)
    for s in (-1, 1):
        x = s * 0.235
        m.cyl("metal", (x, 0.13, -0.30), (x, 1.0, -0.42), 0.012, seg=12)
        m.cyl("metal", (x, 0.11, 0.33), (x, 0.55, -0.36), 0.012, seg=12)
        m.cyl("metal", (x * 0.85, 0.50, -0.30), (x * 0.85, 0.50, 0.24), 0.009, seg=10)
    m.cyl("dark", (-0.25, 1.02, -0.425), (0.25, 1.02, -0.425), 0.022, seg=16)
    for s in (-1, 1):
        m.cyl("metal", (s * 0.235, 0.99, -0.418), (s * 0.235, 1.02, -0.425), 0.012, seg=12)
    m.rbox("fabric", -0.225, 0.225, 0.50, 0.80, -0.30, 0.36, r=0.09, seg=3, mids=(2, 1, 3), bulge=(0.01, 0.0, 0.01))
    m.rbox("fabric_soft", -0.205, 0.205, 0.785, 0.815, 0.02, 0.335, r=0.014, seg=2, mids=(2, 0, 2), bulge=(0, 0.006, 0))
    m.hood("fabric_soft", (0.0, 0.78, 0.02), (0.232, 0.24, 0.31), thick=0.06)
    m.rbox("dark", -0.17, 0.17, 0.20, 0.30, -0.22, 0.16, r=0.02, seg=2)
    return m


def box(W, H, D):
    m = Model()
    hw, hd = W / 2, D / 2
    m.rbox("cardboard", -hw, hw, 0.0, H, -hd, hd, r=0.007, seg=2)
    t, tw = 0.0012, 0.026
    m.box("white", -hw - t, hw + t, H - 0.001, H + t, -tw, tw, r=0.0004)
    for s in (-1, 1):
        x0, x1 = (hw - 0.001, hw + t) if s > 0 else (-hw - t, -hw + 0.001)
        m.box("white", x0, x1, H - 0.075, H, -tw, tw, r=0.0004)
    return m


BUILDERS = {
    "sofa3": lambda s: sofa(*s, seats=3),
    "sofa2": lambda s: sofa(*s, seats=2),
    "bed": lambda s: bed(*s),
    "wardrobe": lambda s: wardrobe(*s),
    "fridge": lambda s: fridge(*s),
    "washer": lambda s: washer(*s),
    "tv": lambda s: tv(*s),
    "bike": lambda s: bike(*s),
    "stroller": lambda s: stroller(*s),
    "box": lambda s: box(*s),
}


# ---------------------------------------------------------------------------- scene + export
def clear_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def build_scene(name, size):
    m = BUILDERS[name](size)
    allv = [v for P in m.parts.values() for v in P[0]]
    lo = [min(v[k] for v in allv) for k in range(3)]
    hi = [max(v[k] for v in allv) for k in range(3)]
    sc = [size[k] / (hi[k] - lo[k]) for k in range(3)]
    off = [-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2]
    return objects_from_model(name, m, sc, off), sc


def objects_from_model(name, m, sc=(1, 1, 1), off=(0, 0, 0), table=MATERIALS):
    objs = []
    for mat_name, (verts, normals, faces) in m.parts.items():
        V = [((v[0] + off[0]) * sc[0], (v[1] + off[1]) * sc[1], (v[2] + off[2]) * sc[2]) for v in verts]
        N = [norm((n[0] / sc[0], n[1] / sc[1], n[2] / sc[2])) for n in normals]
        me = bpy.data.meshes.new(f"{name}_{mat_name}")
        me.from_pydata([(x, -z, y) for (x, y, z) in V], [], faces)
        me.materials.append(make_material(mat_name, table))
        me.shade_smooth()
        loop_normals = []
        for poly in me.polygons:
            for li in poly.loop_indices:
                n = N[me.loops[li].vertex_index]
                loop_normals.append((n[0], -n[2], n[1]))
        me.normals_split_custom_set(loop_normals)
        me.validate(clean_customdata=False)
        ob = bpy.data.objects.new(f"{name}_{mat_name}", me)
        bpy.context.scene.collection.objects.link(ob)
        objs.append(ob)
    return objs


def export(objs, path):
    for o in bpy.context.scene.objects:
        o.select_set(o in objs)
    kw = dict(filepath=path, export_format="GLB", use_selection=True, export_yup=True, export_apply=True,
              export_texcoords=False, export_normals=True, export_tangents=False, export_materials="EXPORT",
              export_cameras=False, export_lights=False, export_animations=False, export_extras=False,
              export_skins=False, export_morph=False)
    try:
        bpy.ops.export_scene.gltf(**kw, export_vertex_color="NONE")
    except TypeError:
        bpy.ops.export_scene.gltf(**kw)


def render_preview(glb, png):
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=glb)
    obs = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    from mathutils import Vector
    pts = [o.matrix_world @ Vector(c) for o in obs for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2
    radius = (hi - lo).length / 2
    scn = bpy.context.scene
    # ground
    bpy.ops.mesh.primitive_plane_add(size=60, location=(0, 0, 0))
    gm = bpy.data.materials.new("ground")
    gm.use_nodes = True
    gb = next(n for n in gm.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    gb.inputs["Base Color"].default_value = (0.42, 0.42, 0.42, 1)
    gb.inputs["Roughness"].default_value = 1.0
    bpy.context.object.data.materials.append(gm)
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs["Color"].default_value = (0.42, 0.42, 0.42, 1)
    bg.inputs["Strength"].default_value = 0.9
    scn.world = world
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.2
    sun.data.angle = math.radians(12)
    sun.rotation_euler = (math.radians(50), 0, math.radians(-35))
    scn.collection.objects.link(sun)
    fill = bpy.data.objects.new("fill", bpy.data.lights.new("fill", "AREA"))
    fill.data.energy = 120 * max(1.0, radius) ** 2
    fill.data.size = 3 * max(1.0, radius)
    fill.location = ctr + Vector((-2.5, -2.5, 1.5)) * max(1.0, radius)
    scn.collection.objects.link(fill)
    tgt = bpy.data.objects.new("tgt", None)
    tgt.location = ctr
    scn.collection.objects.link(tgt)
    for l in (fill,):
        c = l.constraints.new("TRACK_TO")
        c.target = tgt
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = 50
    d = Vector((0.55, -0.85, 0.42)).normalized()
    cam.location = ctr + d * (radius / math.sin(math.radians(14.5)) * 1.0)
    c = cam.constraints.new("TRACK_TO")
    c.target = tgt
    scn.collection.objects.link(cam)
    scn.camera = cam
    for eng in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            scn.render.engine = eng
            break
        except TypeError:
            pass
    try:
        scn.eevee.taa_render_samples = 24
    except AttributeError:
        pass
    scn.render.resolution_x, scn.render.resolution_y = 600, 450
    scn.render.resolution_percentage = 100
    scn.render.image_settings.file_format = "PNG"
    scn.view_settings.view_transform = "Standard"
    scn.render.filepath = png
    bpy.ops.render.render(write_still=True)


# ---------------------------------------------------------------------------- GLB check
def read_glb(path):
    with open(path, "rb") as f:
        data = f.read()
    magic, ver, length = struct.unpack_from("<III", data, 0)
    assert magic == 0x46546C67, "not a GLB"
    off, js, binc = 12, None, None
    while off < length:
        clen, ctype = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + clen]
        if ctype == 0x4E4F534A:
            js = json.loads(chunk)
        elif ctype == 0x004E4942:
            binc = chunk
        off += 8 + clen
    return js, binc


def check_glb(name, path, size):
    js, binc = read_glb(path)
    lo, hi, tris = [1e9] * 3, [-1e9] * 3, 0
    mats = sorted(m["name"] for m in js.get("materials", []))
    top, front = [], []
    for node in js.get("nodes", []):
        assert all(k not in node for k in ("rotation", "scale", "matrix")) or node.get("rotation") in (None, [0, 0, 0, 1]), node
        t = node.get("translation", [0, 0, 0])
        if "mesh" not in node:
            continue
        for prim in js["meshes"][node["mesh"]]["primitives"]:
            acc = js["accessors"][prim["attributes"]["POSITION"]]
            for k in range(3):
                lo[k] = min(lo[k], acc["min"][k] + t[k])
                hi[k] = max(hi[k], acc["max"][k] + t[k])
            tris += js["accessors"][prim["indices"]]["count"] // 3 if "indices" in prim else acc["count"] // 3
            bv = js["bufferViews"][acc["bufferView"]]
            o = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
            stride = bv.get("byteStride", 12)
            mname = js["materials"][prim["material"]]["name"] if "material" in prim else ""
            for i in range(acc["count"]):
                p = struct.unpack_from("<fff", binc, o + i * stride)
                top.append(p)
                if mname in ("glass", "metal"):
                    front.append(p[2])
    dims = [hi[k] - lo[k] for k in range(3)]
    ok_size = all(abs(dims[k] - size[k]) <= 0.002 for k in range(3))
    ok_origin = abs(lo[1]) < 1e-4 and abs(lo[0] + hi[0]) < 2e-3 and abs(lo[2] + hi[2]) < 2e-3
    ymax = hi[1]
    high = [p for p in top if p[1] > ymax - 0.03 * size[1]]
    top_z = sum(p[2] for p in high) / len(high)
    kb = os.path.getsize(path) / 1024
    flags = []
    if not ok_size:
        flags.append("SIZE")
    if not ok_origin:
        flags.append("ORIGIN")
    if kb > 400 or tris > 25000:
        flags.append("WEIGHT")
    if js.get("cameras") or js.get("animations") or js.get("extensions", {}).get("KHR_lights_punctual") or js.get("images"):
        flags.append("EXTRA")
    front_z = sum(front) / len(front) if front else float("nan")
    return dict(name=name, kb=kb, tris=tris, lo=lo, hi=hi, top_z=top_z, front_z=front_z, mats=mats, flags=flags)


# ---------------------------------------------------------------------------- floor (--floor)
FLOOR_CENTRE, FLOOR_SIZE = (-0.05, 1.5), (6.5, 6.0)  # x, z; top surface at y = 0
PLANK_W, PLANK_L, PLANK_T, PLANK_GAP, PLANK_BEVEL = 0.14, 1.5, 0.01, 0.0015, 0.0018


def plank(m, mat, x0, x1, z0, z1):
    b, t = PLANK_BEVEL, PLANK_T
    top = [(x0 + b, 0.0, z0 + b), (x1 - b, 0.0, z0 + b), (x1 - b, 0.0, z1 - b), (x0 + b, 0.0, z1 - b)]
    rim = [(x0, -b, z0), (x1, -b, z0), (x1, -b, z1), (x0, -b, z1)]
    bot = [(x0, -t, z0), (x1, -t, z0), (x1, -t, z1), (x0, -t, z1)]
    m.add(mat, top, [(0.0, 1.0, 0.0)] * 4, [[0, 1, 2, 3]])
    outs = [(0.0, 0.0, -1.0), (1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (-1.0, 0.0, 0.0)]
    for i in range(4):
        j = (i + 1) % 4
        m.add(mat, [top[i], top[j], rim[j], rim[i]], [norm(add(outs[i], (0.0, 1.0, 0.0)))] * 4, [[0, 1, 2, 3]])
        m.add(mat, [rim[i], rim[j], bot[j], bot[i]], [outs[i]] * 4, [[0, 1, 2, 3]])


def floor_model(seed=7):
    import random
    rng = random.Random(seed)
    m = Model()
    xmin, xmax = FLOOR_CENTRE[0] - FLOOR_SIZE[0] / 2, FLOOR_CENTRE[0] + FLOOR_SIZE[0] / 2
    zmin, zmax = FLOOR_CENTRE[1] - FLOOR_SIZE[1] / 2, FLOOR_CENTRE[1] + FLOOR_SIZE[1] / 2
    g = PLANK_GAP / 2
    # Planks run away from the viewer (along z), as in the target picture.
    x0 = xmin
    while x0 < xmax - 1e-6:
        x1 = min(x0 + PLANK_W, xmax)
        z = zmin - rng.uniform(0.15, PLANK_L - 0.15)  # staggered joints
        while z < zmax - 1e-6:
            z0, z1 = max(z, zmin), min(z + PLANK_L, zmax)
            if z1 - z0 > 0.02:
                plank(m, rng.choice(sorted(FLOOR_DAY)), x0 + g, x1 - g, z0 + g, z1 - g)
            z += PLANK_L
        x0 = x1
    return m


def build_floor(out):
    paths = []
    for fname, table in (("floor", FLOOR_DAY), ("floor_night", FLOOR_NIGHT)):
        clear_scene()
        objs = objects_from_model(fname, floor_model(), table=table)
        path = os.path.join(out, f"{fname}.glb")
        export(objs, path)
        paths.append(path)
    return paths


def floor_preview(floor_glb, sofa_glb, png):
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=floor_glb)
    bpy.ops.import_scene.gltf(filepath=sofa_glb)
    from mathutils import Vector
    scn = bpy.context.scene
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.9, 0.85, 0.78, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.55
    scn.world = world
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 2.2
    sun.data.angle = math.radians(8)
    sun.rotation_euler = (math.radians(55), 0, math.radians(-40))
    scn.collection.objects.link(sun)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = 32
    cam.location = Vector((-0.5, -3.9, 1.45))  # app (-0.5, 1.45, 3.9)
    cam.rotation_euler = (Vector((0.0, 0.0, 0.35)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    scn.collection.objects.link(cam)
    scn.camera = cam
    scn.render.engine = "BLENDER_EEVEE"
    scn.eevee.taa_render_samples = 24
    scn.render.resolution_x, scn.render.resolution_y = 600, 450
    scn.view_settings.view_transform = "Standard"
    scn.render.image_settings.file_format = "PNG"
    scn.render.filepath = png
    bpy.ops.render.render(write_still=True)


# ---------------------------------------------------------------------------- thumbnails (--thumbs)
# drawable id -> (model, app-space scale x, y, z)
THUMBS = [
    ("sofa2", "sofa2", (1, 1, 1)), ("sofa3", "sofa3", (1, 1, 1)),
    ("bed140", "bed", (1, 1, 1)), ("bed160", "bed", (1.70 / 1.50, 1, 2.15 / 2.05)),
    ("wardrobe", "wardrobe", (1, 1, 1)), ("fridge", "fridge", (1, 1, 1)), ("washer", "washer", (1, 1, 1)),
    ("tv55", "tv", (1, 1, 1)), ("tv65", "tv", (1.18, 1.18, 1.18)),
    ("bike", "bike", (1, 1, 1)), ("stroller", "stroller", (1, 1, 1)),
]
THUMB_PX = 288


def render_thumb(glb, png, s):
    from mathutils import Vector
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=glb)
    obs = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    for o in obs:
        o.scale = (s[0], s[2], s[1])  # app (x, y, z) -> Blender (x, z, y)
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ v.co for o in obs for v in o.data.vertices]
    lo = Vector([min(p[k] for p in pts) for k in range(3)])
    hi = Vector([max(p[k] for p in pts) for k in range(3)])
    ctr, R = (lo + hi) / 2, (hi - lo).length / 2
    d = Vector((0.62, -1.0, 0.52)).normalized()
    rot = d.to_track_quat("Z", "Y")
    right, up = rot @ Vector((1, 0, 0)), rot @ Vector((0, 1, 0))
    xs = [(p - ctr).dot(right) for p in pts]
    ys = [(p - ctr).dot(up) for p in pts]
    scn = bpy.context.scene
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = max(max(xs) - min(xs), max(ys) - min(ys)) / 0.80
    cam.data.clip_end = 200
    cam.location = ctr + right * (max(xs) + min(xs)) / 2 + up * (max(ys) + min(ys)) / 2 + d * 30
    cam.rotation_euler = rot.to_euler()
    scn.collection.objects.link(cam)
    scn.camera = cam
    bpy.ops.mesh.primitive_plane_add(size=80, location=(ctr.x, ctr.y, 0))
    bpy.context.object.is_shadow_catcher = True
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (1, 1, 1, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = THUMB_WORLD
    scn.world = world
    key = bpy.data.objects.new("key", bpy.data.lights.new("key", "AREA"))
    key.data.size = 3.0 * R
    key.location = ctr + Vector((-0.2, -0.35, 3.0)).normalized() * 4 * R
    key.data.energy = THUMB_KEY * (4 * R) ** 2
    key.rotation_euler = (ctr - key.location).to_track_quat("-Z", "Y").to_euler()
    scn.collection.objects.link(key)
    scn.render.engine = "CYCLES"
    scn.cycles.device = "CPU"
    scn.cycles.samples = 96
    scn.cycles.use_denoising = True
    scn.render.film_transparent = True
    scn.render.resolution_x = scn.render.resolution_y = THUMB_PX
    scn.render.resolution_percentage = 100
    scn.render.image_settings.file_format = "PNG"
    scn.render.image_settings.color_mode = "RGBA"
    scn.view_settings.view_transform = "Standard"
    scn.render.filepath = png
    bpy.ops.render.render(write_still=True)
    clean_alpha(png)


def clean_alpha(png, floor=0.035, fade_px=24):
    """Drop the shadow catcher's faint veil and fade the shadow out before the frame edge."""
    import numpy as np
    img = bpy.data.images.load(png)
    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)
    a = np.clip((px[..., 3] - floor) / (1 - floor), 0, 1)
    yy, xx = np.mgrid[0:h, 0:w]
    edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy)).astype(np.float32)
    px[..., 3] = a * np.clip(edge / fade_px, 0, 1)
    img.pixels.foreach_set(px.ravel())
    img.save()
    bpy.data.images.remove(img)


THUMB_KEY = 11.0  # area light W per m^2 of distance
THUMB_WORLD = 0.45  # exposure set so lit fabric reads close to #1F6F78


def build_thumbs(models, out_dir, tmp_dir):
    import subprocess
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(tmp_dir, exist_ok=True)
    cwebp = next((p for p in ("/opt/homebrew/bin/cwebp", "/usr/local/bin/cwebp") if os.path.exists(p)), None)
    made = []
    for tid, model, s in THUMBS:
        png = os.path.join(tmp_dir, f"preset_{tid}.png")
        render_thumb(os.path.join(models, f"{model}.glb"), png, s)
        webp = os.path.join(out_dir, f"preset_{tid}.webp")
        for q in (85, 78, 70, 60):
            subprocess.run([cwebp, "-quiet", "-q", str(q), "-alpha_q", "90", "-m", "6", "-exact", png, "-o", webp], check=True)
            if os.path.getsize(webp) <= 40 * 1024:
                break
        made.append((tid, webp, os.path.getsize(webp)))
    contact_sheet([w for _, w, _ in made], os.path.join(HERE, "previews", "thumbnails.png"))
    return made


def contact_sheet(files, png, cols=6, bg=(0.97, 0.955, 0.94)):
    import numpy as np
    n = THUMB_PX
    rows = (len(files) + cols - 1) // cols
    W, H = cols * n, rows * n
    sheet = np.ones((H, W, 4), dtype=np.float32)
    sheet[..., :3] = bg
    for k, f in enumerate(files):
        img = bpy.data.images.load(f)
        a = np.empty(n * n * 4, dtype=np.float32)
        img.pixels.foreach_get(a)
        a = a.reshape(n, n, 4)
        r, c = rows - 1 - k // cols, k % cols  # Blender rows go bottom-up
        cell = sheet[r * n:(r + 1) * n, c * n:(c + 1) * n]
        cell[..., :3] = cell[..., :3] * (1 - a[..., 3:4]) + a[..., :3] * a[..., 3:4]
    out = bpy.data.images.new("sheet", W, H, alpha=False)
    out.pixels.foreach_set(sheet.ravel())
    out.filepath_raw = png
    out.file_format = "PNG"
    out.save()


def material_report(path):
    js, _ = read_glb(path)
    rows = []
    for m in js.get("materials", []):
        ext = m.get("extensions", {})
        pbr = m.get("pbrMetallicRoughness", {})
        rows.append(f"{m['name']}: base {[round(c, 3) for c in pbr.get('baseColorFactor', [1, 1, 1, 1])[:3]]} "
                    f"rough {pbr.get('roughnessFactor', 1)} metal {pbr.get('metallicFactor', 1)} ext {json.dumps(ext)}")
    return js.get("extensionsUsed", []), rows


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    out = "app/src/main/assets/models"
    only, preview, floor, thumbs = ORDER, True, False, None
    i = 0
    while i < len(argv):
        if argv[i] == "--out":
            out = argv[i + 1]; i += 1
        elif argv[i] == "--only":
            only = [n for n in argv[i + 1].split(",") if n]; i += 1
        elif argv[i] == "--no-preview":
            preview = False
        elif argv[i] == "--floor":
            floor = True
        elif argv[i] == "--thumbs":
            thumbs = argv[i + 1]; i += 1
        i += 1
    out = os.path.abspath(out)
    os.makedirs(out, exist_ok=True)
    prev_dir = os.path.join(HERE, "previews")
    os.makedirs(prev_dir, exist_ok=True)
    rows = []
    for name in [n for n in ORDER if n in only]:
        clear_scene()
        objs, sc = build_scene(name, SIZES[name])
        path = os.path.join(out, f"{name}.glb")
        export(objs, path)
        if preview:
            render_preview(path, os.path.join(prev_dir, f"{name}.png"))
        rows.append(check_glb(name, path, SIZES[name]))
        print(f"[built] {name}  fit-scale {sc[0]:.3f} {sc[1]:.3f} {sc[2]:.3f}", flush=True)
    print("\nname      KB     tris   bbox min (x y z)            bbox max (x y z)          topZ  frontZ  flags   materials")
    for r in rows:
        lo, hi = r["lo"], r["hi"]
        print(f"{r['name']:<9}{r['kb']:6.1f}{r['tris']:8d}   "
              f"{lo[0]:+.4f} {lo[1]:+.4f} {lo[2]:+.4f}   {hi[0]:+.4f} {hi[1]:+.4f} {hi[2]:+.4f}  "
              f"{r['top_z']:+.2f}  {r['front_z']:+.2f}   {','.join(r['flags']) or 'ok':<6}  {' '.join(r['mats'])}")
    for r in rows[:1]:
        used, mats = material_report(os.path.join(out, f"{r['name']}.glb"))
        print(f"\n{r['name']} extensionsUsed {used}")
        for line in mats:
            print("  " + line)
    if floor:
        paths = build_floor(out)
        for p in paths:
            js, _ = read_glb(p)
            acc = [js["accessors"][pr["attributes"]["POSITION"]] for me in js["meshes"] for pr in me["primitives"]]
            lo = [min(a["min"][k] for a in acc) for k in range(3)]
            hi = [max(a["max"][k] for a in acc) for k in range(3)]
            tris = sum(js["accessors"][pr["indices"]]["count"] // 3 for me in js["meshes"] for pr in me["primitives"])
            print(f"[floor] {os.path.basename(p)} {os.path.getsize(p) / 1024:.1f} KB  {tris} tris  "
                  f"min {[round(v, 4) for v in lo]} max {[round(v, 4) for v in hi]}  "
                  f"materials {sorted(m['name'] for m in js['materials'])}")
        if preview:
            for p in paths:
                floor_preview(p, os.path.join(out, "sofa3.glb"),
                              os.path.join(prev_dir, os.path.basename(p).replace(".glb", ".png")))
    if thumbs:
        tmp = os.path.join(prev_dir, "_thumbs_png")
        for tid, webp, size in build_thumbs(out, os.path.abspath(thumbs), tmp):
            print(f"[thumb] {tid:<9} {size / 1024:5.1f} KB  {webp}")
        import shutil
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()
