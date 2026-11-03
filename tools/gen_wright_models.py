"""Generate the 3D Wrights models: wright drone, controller, blueprint slate, blueprint case,
personal wrightport (worn dock), the two-block wrightport tower, and the iris hatch blades.

Run from the repo root: python tools/gen_wright_models.py
Textures land in textures/item/part next to the upgrade part textures.
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from gen_drone_parts import (  # noqa: E402
    COPPER, GUN, MODELS, S, STEEL, add, bevel, bolt, cyl, get, image, mix, mul, noisy, put, save,
    PARENT, tex_blade,
)

ASSETS = MODELS.parent.parent
PART_MODELS = ASSETS / "models" / "part"
BLOCK_MODELS = ASSETS / "models" / "block"
BLOCKSTATES = ASSETS / "blockstates"

AMBER = (255, 150, 40)
AMBER_L = (255, 214, 140)
CYAN = (60, 206, 255)
CYAN_L = (190, 240, 255)


def T(name):
    return f"wrights:item/part/{name}"


# ---------------------------------------------------------------- textures

def line(img, x0, y0, x1, y1, c):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        put(img, x0, y0, c)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def tex_shell():
    rng = random.Random(21)
    img = image()
    base = (192, 197, 204)
    for y in range(S):
        for x in range(S):
            put(img, x, y, noisy(rng, add(base, (S - y) // 5), 3))
    bevel(img, 30, -60)
    for x in range(2, 30):
        put(img, x, 10, (122, 128, 138))
        put(img, x, 11, (226, 230, 236))
        put(img, x, 21, (122, 128, 138))
        put(img, x, 22, (226, 230, 236))
    for y in range(11, 21):
        put(img, 16, y, (122, 128, 138))
        put(img, 17, y, (226, 230, 236))
    for sx in (5, 22):
        for x in range(sx, sx + 6):
            put(img, x, 15, (40, 44, 52))
            put(img, x, 17, (40, 44, 52))
    for cx, cy in ((4, 4), (27, 4), (4, 27), (27, 27)):
        bolt(img, cx, cy)
    save(img, "shell")


def accent_pixels():
    out = {}
    for y in range(S):
        for x in range(S):
            v = 226 - abs(y - 15.5) * 1.6
            if (x + y) % 12 < 3:
                v -= 46
            out[(x, y)] = v
    for i in range(S):
        out[(i, 0)] = 255
        out[(0, i)] = 255
        out[(i, S - 1)] = 120
        out[(S - 1, i)] = 120
    return out


def tex_accent():
    img = image()
    for (x, y), v in accent_pixels().items():
        put(img, x, y, (int(v), int(v), int(v)))
    save(img, "accent")
    img = image()
    for (x, y), v in accent_pixels().items():
        k = v / 255
        put(img, x, y, (int(AMBER[0] * k), int(AMBER[1] * k), int(AMBER[2] * k)))
    save(img, "accent_amber")


def tex_screen():
    img = image()
    for y in range(S):
        for x in range(S):
            c = (8, 18, 26) if (x // 2 + y // 2) % 2 else (10, 22, 32)
            put(img, x, y, c)
    for i in range(S):
        for d in (0, S - 1):
            put(img, i, d, (40, 120, 150))
            put(img, d, i, (40, 120, 150))
    for x in range(2, 30):
        put(img, x, 3, (40, 110, 140))
    for x in range(3, 9):
        put(img, x, 2, (120, 230, 255))
    put(img, 27, 2, (120, 255, 140))
    put(img, 28, 2, (120, 255, 140))
    for row, w in ((7, 11), (10, 8), (13, 12), (16, 6), (19, 10)):
        for x in range(3, 3 + w):
            put(img, x, row, (90, 200, 240) if x % 5 else (40, 110, 140))
    cx, cy, r = 22, 16, 7
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if abs(d - r) < 0.6 or abs(d - r / 2) < 0.5:
                put(img, x, y, (50, 160, 200))
    line(img, cx, cy, cx + 5, cy - 4, (130, 240, 255))
    for bx, by in ((19, 13), (25, 19), (23, 11)):
        put(img, bx, by, AMBER_L)
    for x in range(3, 29):
        put(img, x, 26, (24, 70, 90))
    for x in range(3, 18):
        put(img, x, 27, (120, 230, 255))
        put(img, x, 28, (60, 170, 210))
    save(img, "screen")


def tex_grip():
    img = image()
    for y in range(S):
        for x in range(S):
            k = (x + y) % 4 == 0 or (x - y) % 4 == 0
            c = (52, 52, 58) if k else (30, 30, 34)
            put(img, x, y, mul(c, cyl(x) * 0.9))
    bevel(img, 18, -16)
    save(img, "grip")


def slate_base(img):
    for y in range(S):
        for x in range(S):
            c = (14, 38, 80)
            if x % 4 == 0 or y % 4 == 0:
                c = (30, 72, 140)
            if x % 8 == 0 or y % 8 == 0:
                c = (60, 128, 210)
            put(img, x, y, c)
    for i in range(S):
        for d in (0, 1, S - 2, S - 1):
            put(img, i, d, add(STEEL, -20 if d > 1 else 10))
            put(img, d, i, add(STEEL, -20 if d > 1 else 10))


def tex_slate():
    img = image()
    slate_base(img)
    for x in range(4, 12):
        put(img, x, 4, (160, 210, 255))
    save(img, "slate")
    img = image()
    slate_base(img)
    white = (220, 244, 255)
    # A small isometric building outline with dimension ticks.
    pts = [(8, 20), (16, 24), (24, 20), (24, 11), (16, 7), (8, 11)]
    for a, b in zip(pts, pts[1:] + pts[:1]):
        line(img, *a, *b, white)
    line(img, 8, 11, 16, 15, white)
    line(img, 16, 15, 24, 11, white)
    line(img, 16, 15, 16, 24, white)
    line(img, 12, 6, 16, 3, white)
    line(img, 16, 3, 20, 6, white)
    for x in range(8, 25):
        put(img, x, 27, (120, 200, 255) if x % 2 else (60, 128, 210))
    put(img, 8, 26, white)
    put(img, 24, 26, white)
    for x in range(4, 10):
        put(img, x, 4, AMBER_L)
    save(img, "slate_filled")


def tex_holo():
    img = image()
    edge = (90, 226, 255)
    hi = (210, 250, 255)
    for i in range(S):
        for d in (0, S - 1):
            put(img, i, d, edge)
            put(img, d, i, edge)
    for i in range(6):
        for cx, cy, sx, sy in ((1, 1, 1, 1), (30, 1, -1, 1), (1, 30, 1, -1), (30, 30, -1, -1)):
            put(img, cx + sx * i, cy, hi)
            put(img, cx, cy + sy * i, hi)
    for y in range(4, 28, 6):
        for x in range(4, 28, 2):
            put(img, x, y, (60, 180, 230))
    save(img, "holo")


def tex_case():
    rng = random.Random(22)
    img = image()
    base = (58, 64, 62)
    for y in range(S):
        for x in range(S):
            c = noisy(rng, base, 3)
            r = y % 7
            if r == 0:
                c = add(c, 26)
            elif r == 6:
                c = add(c, -24)
            put(img, x, y, c)
    bevel(img, 30, -40)
    for cx, cy in ((3, 3), (28, 3), (3, 28), (28, 28)):
        for dy in (-2, -1, 0, 1, 2):
            for dx in (-2, -1, 0, 1, 2):
                put(img, cx + dx, cy + dy, add(STEEL, -10 - (dx + dy) * 6))
    save(img, "case")


def tex_label():
    img = image()
    for y in range(S):
        for x in range(S):
            put(img, x, y, (222, 216, 198))
    for i in range(S):
        for d in (0, S - 1):
            put(img, i, d, (120, 114, 100))
            put(img, d, i, (120, 114, 100))
    for row, w in ((5, 18), (9, 12), (13, 20)):
        for x in range(4, 4 + w):
            put(img, x, row, (40, 40, 44) if x % 4 else (222, 216, 198))
    for x in range(4, 28):
        if (x * 7) % 5 < 3:
            for y in range(19, 27):
                put(img, x, y, (24, 24, 28))
    for y in range(4, 9):
        for x in range(24, 29):
            put(img, x, y, AMBER)
    save(img, "label")


def tex_led_off():
    img = image()
    for y in range(S):
        for x in range(S):
            t = 1 - abs(y - 15.5) / 15.5
            put(img, x, y, mix((40, 22, 22), (90, 40, 36), t))
    save(img, "led_off")


def tex_port_panel():
    rng = random.Random(23)
    img = image()
    base = (70, 76, 88)
    for y in range(S):
        for x in range(S):
            put(img, x, y, noisy(rng, base, 3))
    bevel(img, 30, -40)
    for i in range(3, 29):
        for v in (15,):
            put(img, i, v, add(base, -30))
            put(img, i, v + 1, add(base, 18))
            put(img, v, i, add(base, -30))
            put(img, v + 1, i, add(base, 18))
    for sy in range(19, 27, 3):
        for x in range(19, 28):
            put(img, x, sy, (20, 22, 26))
            put(img, x, sy + 1, add(base, 20))
    for sy in range(19, 27, 3):
        for x in range(4, 13):
            put(img, x, sy, (20, 22, 26))
            put(img, x, sy + 1, add(base, 20))
    for x in range(4, 13):
        for y in range(4, 12):
            if (x + y) % 6 < 3:
                put(img, x, y, (232, 182, 44))
            else:
                put(img, x, y, (34, 34, 38))
    for x in range(19, 28):
        put(img, x, 6, AMBER)
        put(img, x, 7, AMBER_L)
    for cx, cy in ((3, 3), (28, 3), (3, 28), (28, 28), (19, 10), (27, 10)):
        bolt(img, cx, cy)
    save(img, "port_panel")


def tex_port_core():
    rng = random.Random(24)
    img = image()
    base = (56, 60, 70)
    for y in range(S):
        for x in range(S):
            c = noisy(rng, base, 3)
            if x < 9 or x > 22:
                rib = x % 3
                c = add(c, 20 if rib == 0 else (-16 if rib == 2 else 0))
            put(img, x, y, c)
    bevel(img, 26, -40)
    for y in (2, 29):
        for x in range(2, 30):
            put(img, x, y, add(base, -26))
    for cx, cy in ((12, 4), (19, 4), (12, 27), (19, 27)):
        bolt(img, cx, cy)
    save(img, "port_core")


def tex_core_glow():
    img = image()
    for y in range(S):
        for x in range(S):
            t = 1 - abs(x - 15.5) / 15.5
            c = mix((150, 60, 10), mix(AMBER, (255, 245, 210), 0.6), t ** 1.4)
            if y % 8 in (0, 1):
                c = mul(c, 0.42)
            put(img, x, y, c)
    save(img, "core_glow")


def tex_bay():
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c0, y - c0)
            c = mix((14, 15, 18), (40, 44, 50), min(1, r / 16))
            if abs(r - 11.5) < 0.9:
                c = AMBER if int(math.degrees(math.atan2(y - c0, x - c0)) // 15) % 2 else mul(AMBER, 0.55)
            elif abs(r - 6) < 0.6:
                c = (60, 160, 200)
            if abs(x - c0) < 0.6 or abs(y - c0) < 0.6:
                if 2 < r < 5:
                    c = (120, 200, 230)
            put(img, x, y, c)
    save(img, "bay")


def tex_iris_blade():
    img = image()
    for y in range(S):
        for x in range(S):
            c = add(STEEL, -int(y * 1.2) + (12 if x < 4 else 0))
            if y < 3:
                c = (40, 44, 50) if y < 2 else (90, 96, 106)
            if x + y == 34 or x + y == 35:
                c = add(c, -40)
            if 25 <= x <= 27 and y >= 3:
                c = AMBER if x != 27 else mul(AMBER, 0.6)
            put(img, x, y, c)
    for x in range(S):
        put(img, x, S - 1, (36, 40, 46))
    for cx, cy in ((29, 8), (29, 24)):
        bolt(img, cx, cy)
    save(img, "iris_blade")


def tex_pillar():
    img = image()
    for y in range(S):
        for x in range(S):
            rib = x % 4
            c = add(STEEL, [22, 4, -18, -44][rib] - 30)
            if y % 16 in (0, 1):
                c = add(GUN, -10 if y % 16 else 20)
            put(img, x, y, c)
    for cy in (8, 24):
        bolt(img, 15, cy)
    save(img, "pillar")


def tex_lamp():
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = max(abs(x - c0), abs(y - c0))
            v = 255 - r * 4.4
            put(img, x, y, (int(v), int(v), int(v)))
    for i in range(S):
        put(img, i, 0, (90, 90, 90))
        put(img, 0, i, (90, 90, 90))
        put(img, i, S - 1, (90, 90, 90))
        put(img, S - 1, i, (90, 90, 90))
    save(img, "lamp")


def tex_port_door():
    rng = random.Random(25)
    img = image()
    base = (78, 84, 96)
    for y in range(S):
        for x in range(S):
            put(img, x, y, noisy(rng, base, 3))
    for i in range(S):
        for d in range(3):
            put(img, i, d, add(GUN, 10 - d * 6))
            put(img, d, i, add(GUN, 10 - d * 6))
            put(img, i, S - 1 - d, add(GUN, -20 + d * 6))
            put(img, S - 1 - d, i, add(GUN, -20 + d * 6))
    for y in range(4, 9):
        for x in range(4, 28):
            put(img, x, y, (232, 182, 44) if ((x + y) // 3) % 2 else (30, 30, 34))
    for y in range(14, 18):
        for x in range(10, 22):
            put(img, x, y, (26, 28, 32) if y in (14, 17) else add(STEEL, 20))
    for y in range(22, 27):
        for x in range(6, 14):
            put(img, x, y, (222, 216, 198) if (x + y) % 5 else (60, 60, 60))
    for cx, cy in ((5, 12), (26, 12), (5, 27), (26, 27)):
        bolt(img, cx, cy)
    save(img, "port_door")


def tex_pack_bay():
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c0, y - c0)
            ang = math.atan2(y - c0, x - c0)
            c = mix((8, 9, 12), (46, 50, 58), min(1, r / 15))
            if 7 <= r < 8.4:
                c = AMBER if int(math.degrees(ang) // 22.5) % 2 else mul(AMBER, 0.6)
            elif r < 7 and int((math.degrees(ang) + 360) % 45) < 3:
                c = (30, 34, 40)
            put(img, x, y, c)
    save(img, "pack_bay")


def tex_cell():
    img = image()
    for y in range(S):
        for x in range(S):
            k = cyl(x)
            if y < 5 or y > 26:
                c = mul(add(STEEL, 10 if y in (0, 31) else -10), k)
            elif x < 6 or x > 25:
                c = mul((40, 44, 52), k)
            else:
                bar = (y - 6) % 4
                c = (14, 30, 18) if bar == 3 else mix((40, 210, 90), (180, 255, 200), 0.3 if bar == 1 else 0)
                c = mul(c, 0.7 + 0.4 * k)
            put(img, x, y, c)
    save(img, "cell")


def tex_strap():
    img = image()
    for y in range(S):
        for x in range(S):
            c = (44, 44, 48) if ((x + y) // 2) % 2 else (34, 34, 38)
            if x in (2, 29) and y % 4 < 2:
                c = (150, 150, 140)
            if x in (0, 31):
                c = (22, 22, 24)
            put(img, x, y, c)
    save(img, "strap")


# ---------------------------------------------------------------- model helpers

UV = [0, 0, 16, 16]
FACES = ("north", "east", "south", "west", "up", "down")


def cube(frm, to, tex, rot=None, glow=False, tint=False):
    """tex: a texture key for all faces, or a dict keyed by face / 'side' / 'end' / 'all'. None hides a face."""
    if not isinstance(tex, dict):
        tex = {"all": tex}
    faces = {}
    for f in FACES:
        if f in tex:
            key = tex[f]
        elif f in ("up", "down") and "end" in tex:
            key = tex["end"]
        elif f not in ("up", "down") and "side" in tex:
            key = tex["side"]
        else:
            key = tex.get("all")
        if key is None:
            continue
        face = {"uv": UV, "texture": key}
        if tint:
            face["tintindex"] = 0
        faces[f] = face
    el = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": faces}
    if rot:
        el["rotation"] = rot
    if glow:
        el["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def rot(axis, angle, origin):
    return {"angle": angle, "axis": axis, "origin": [round(v, 4) for v in origin]}


def ring(center, lo, hi, r_out, thick, tex, axis="y", glow=False):
    """Octagonal bezel. axis 'y' lies flat (lo/hi are heights); axis 'z' stands in the XY plane (lo/hi are depths)."""
    cx, cy = center
    half = r_out * math.tan(math.pi / 8)
    r_in = r_out - thick
    a, b = cx - half, cx + half
    out = []
    if axis == "y":
        segs = [
            ([a, lo, cy - r_out], [b, hi, cy - r_in]),
            ([a, lo, cy + r_in], [b, hi, cy + r_out]),
            ([cx - r_out, lo, cy - half], [cx - r_in, hi, cy + half]),
            ([cx + r_in, lo, cy - half], [cx + r_out, hi, cy + half]),
        ]
        pivot = [cx, (lo + hi) / 2, cy]
        for frm, to in segs:
            out.append(cube(frm, to, tex, glow=glow))
        for frm, to in segs[:2]:
            for ang in (45, -45):
                lift = 0.03
                out.append(cube([frm[0], frm[1] + lift, frm[2]], [to[0], to[1] + lift, to[2]], tex,
                                rot("y", ang, pivot), glow))
    else:
        segs = [
            ([a, cy + r_in, lo], [b, cy + r_out, hi]),
            ([a, cy - r_out, lo], [b, cy - r_in, hi]),
            ([cx - r_out, cy - half, lo], [cx - r_in, cy + half, hi]),
            ([cx + r_in, cy - half, lo], [cx + r_out, cy + half, hi]),
        ]
        pivot = [cx, cy, (lo + hi) / 2]
        for frm, to in segs:
            out.append(cube(frm, to, tex, glow=glow))
        for frm, to in segs[:2]:
            for ang in (45, -45):
                lift = 0.03
                out.append(cube([frm[0], frm[1], frm[2] - lift], [to[0], to[1], to[2] - lift], tex,
                                rot("z", ang, pivot), glow))
    return out


def turn(axis, x, y, a):
    """Right-handed turn of an in-plane point. For 'y' the pair is (x, z); for 'z' it is (x, y)."""
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    if axis == "y":
        return x * c + y * s, -x * s + y * c
    return x * c - y * s, x * s + y * c


class Iris:
    """Eight skewed plates. Blade 0 points along +X; the renderer turns copies by 45° and slides them out."""

    SKEW = 22.5
    LENGTH = 4.4
    WIDTH = 4.0
    INNER = 0.3
    SLIDE = 4.85

    def __init__(self, axis, center, depth, scale, thick=0.05, step=0.035):
        self.axis = axis
        self.center = center
        self.depth = depth
        self.k = scale
        self.thick = thick
        self.step = step

    def blade_rect(self):
        k = self.k
        x0 = -self.INNER * k
        x1 = (self.LENGTH - self.INNER) * k
        return x0, x1, self.WIDTH * k

    def blade_model(self, tex):
        cx, cy = self.center
        x0, x1, w = self.blade_rect()
        if self.axis == "y":
            frm = [cx + x0, self.depth, cy - w / 2]
            to = [cx + x1, self.depth + self.thick, cy + w / 2]
            r = rot("y", self.SKEW, [cx + x1, self.depth, cy])
        else:
            frm = [cx + x0, cy - w / 2, self.depth - self.thick]
            to = [cx + x1, cy + w / 2, self.depth]
            r = rot("z", self.SKEW, [cx + x1, cy, self.depth])
        return cube(frm, to, tex, r)

    def closed_blades(self, tex):
        """Static closed iris for item models, using only rotations JSON allows."""
        cx, cy = self.center
        x0, x1, w = self.blade_rect()
        length = x1 - x0
        mid = (x0 + x1) / 2
        rx, ry = turn(self.axis, mid - x1, 0.0, self.SKEW)
        bx, by = x1 + rx, ry
        out = []
        for i in range(8):
            phi = self.SKEW + 45 * i
            k90 = int(math.floor(phi / 90 + 0.5))
            psi = phi - 90 * k90
            px, py = turn(self.axis, bx, by, 45 * i)
            gx, gy = cx + px, cy + py
            dl, dw = (w, length) if k90 % 2 else (length, w)
            off = self.step * i
            if self.axis == "y":
                frm = [gx - dl / 2, self.depth + off, gy - dw / 2]
                to = [gx + dl / 2, self.depth + off + self.thick, gy + dw / 2]
                r = rot("y", psi, [gx, self.depth + off, gy])
            else:
                frm = [gx - dl / 2, gy - dw / 2, self.depth - off - self.thick]
                to = [gx + dl / 2, gy + dw / 2, self.depth - off]
                r = rot("z", psi, [gx, gy, self.depth - off])
            out.append(cube(frm, to, tex, r))
        return out


BLOCK_IRIS = Iris("y", (8, 8), 30.1, 1.0)
PACK_IRIS = Iris("z", (8, 18), 10.7, 0.6, thick=0.04, step=0.03)


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def textures(**keys):
    out = {k: (v if ":" in v else T(v)) for k, v in keys.items()}
    out.setdefault("particle", T("frame"))
    return out


# ---------------------------------------------------------------- wright drone

# Rotor mount plates, matching PODS in client/WrightModel.java. A rotor part stands on each plate top (y 7).
PODS = [(3.25, 3.25), (12.75, 3.25), (3.25, 12.75), (12.75, 12.75)]


def drone_body():
    """The bare chassis. Rotors stand on the four plates, the Power Core on the raised top plate (y 9.25),
    and the Fabricator Head hangs under the belly plate (y 5)."""
    shell = {"side": "#shell", "up": "#shell", "down": "#frame"}
    els = [
        cube([5, 6, 5], [11, 8.5, 11], shell),
        cube([6, 8.5, 6], [10, 9.25, 10], {"side": "#steel", "up": "#frame"}),
        cube([7.25, 9.25, 6.5], [8.75, 9.45, 9.5], "#accent", tint=True),
        cube([5.5, 5, 5.5], [10.5, 6, 10.5], "#frame"),
        cube([6.5, 6.1, 4.1], [9.5, 8.1, 5], "#frame"),
        cube([7, 6.5, 3.8], [9, 7.7, 4.1], "#lens", glow=True),
        cube([4.85, 6.6, 6], [5, 7.8, 10], "#accent", tint=True),
        cube([11, 6.6, 6], [11.15, 7.8, 10], "#accent", tint=True),
        cube([6.5, 4.6, 6.5], [9.5, 5, 9.5], {"side": "#steel", "down": "#frame", "up": "#frame"}),
        cube([10.6, 6.7, 6.6], [11.6, 7.6, 7.4], "#led", glow=True),
    ]
    for x0, x1 in ((1.3, 6), (10, 14.7)):
        for ang in (45, -45):
            els.append(cube([x0, 6.4, 7.5], [x1, 7, 8.5], {"side": "#frame", "end": "#steel"}, rot("y", ang, [8, 6.7, 8])))
    for px, pz in PODS:
        els.append(cube([px - 1.1, 6.5, pz - 1.1], [px + 1.1, 7, pz + 1.1], {"side": "#steel", "end": "#frame"}))
        els.append(cube([px - 0.5, 6.1, pz - 0.5], [px + 0.5, 6.5, pz + 0.5], "#led", glow=True))
    for x0 in (4.6, 10.6):
        els.append(cube([x0, 0.8, 4], [x0 + 0.8, 1.3, 12], "#steel"))
        for z in (5.5, 10):
            els.append(cube([x0 + 0.15, 1.3, z], [x0 + 0.6, 6.4, z + 0.5], "#frame"))
    return els


DRONE_TEX = dict(shell="shell", frame="frame", steel="steel", motor="motor", motor_top="motor_top",
                 lens="lens_mk3", led="led_mk1", blade="blade_drone")


# ---------------------------------------------------------------- controller

def controller():
    els = [
        cube([2, 1, 4], [14, 3, 12], {"side": "#frame", "up": "#steel", "down": "#frame"}),
        cube([4.5, 3, 5.5], [11.5, 3.2, 10.5], {"up": "#screen", "side": "#frame", "down": None}, glow=True),
        cube([4, 3, 5], [12, 3.1, 11], {"side": "#steel", "up": "#frame", "down": None}),
        cube([0.5, 0.5, 5], [3, 3, 11.5], "#grip"),
        cube([13, 0.5, 5], [15.5, 3, 11.5], "#grip"),
        cube([2.5, 2, 3.4], [5, 2.9, 4], "#led"),
        cube([11, 2, 3.4], [13.5, 2.9, 4], "#led"),
        cube([6, 1.5, 3.2], [10, 2.75, 4], "#frame"),
        cube([7, 1.8, 3], [9, 2.45, 3.2], "#lens", glow=True),
        cube([12, 3, 4.4], [12.6, 7, 5], "#steel"),
        cube([11.9, 7, 4.3], [12.7, 7.6, 5.1], "#led", glow=True),
    ]
    for x in (1.75,):
        els.append(cube([x - 0.75, 3, 6.75], [x + 0.75, 3.4, 8.25], "#frame"))
        els.append(cube([x - 0.35, 3.4, 7.15], [x + 0.35, 4.6, 7.85], "#steel"))
        els.append(cube([x - 0.65, 4.6, 6.85], [x + 0.65, 5, 8.15], {"side": "#grip", "end": "#motor_top"}))
    for i, (bx, bz) in enumerate(((13.6, 7.0), (14.6, 8.0), (13.6, 9.0), (12.6, 8.0))):
        els.append(cube([bx - 0.4, 3, bz - 0.4], [bx + 0.4, 3.35, bz + 0.4], f"#btn{i}", glow=True))
    return els


# ---------------------------------------------------------------- blueprint slate & case

def slate(filled):
    els = [
        cube([2, 0, 3], [14, 1, 13], {"side": "#frame", "up": "#slate_top", "down": "#frame"}),
        cube([2.5, 1, 11.6], [13.5, 1.3, 12.6], {"side": "#steel", "up": "#frame"}),
        cube([3, 1.3, 11.85], [4, 1.5, 12.35], "#led", glow=True),
        cube([12, 1.3, 11.85], [13, 1.5, 12.35], "#led_b", glow=True),
        cube([7, 1, 3.3], [9, 1.5, 4.3], {"side": "#frame", "up": "#lens"}, glow=True),
        cube([1.5, 0, 2.5], [3, 1.25, 4], "#steel"),
        cube([13, 0, 2.5], [14.5, 1.25, 4], "#steel"),
        cube([1.5, 0, 12], [3, 1.25, 13.5], "#steel"),
        cube([13, 0, 12], [14.5, 1.25, 13.5], "#steel"),
    ]
    if filled:
        els.append(cube([5, 3, 4.5], [11, 8.5, 10.5], "#holo", glow=True))
        els.append(cube([6.5, 4.5, 6], [9.5, 7, 9], "#holo", rot("y", 45, [8, 5.75, 7.5]), glow=True))
        els.append(cube([7.6, 1.5, 3.6], [8.4, 3, 4.4], "#lens", glow=True))
    return els


def case(filled):
    els = [
        cube([2, 0, 5], [14, 11, 11], {"side": "#case", "end": "#frame"}),
        cube([1.5, 0, 4.5], [2.75, 11.25, 5.75], "#steel"),
        cube([13.25, 0, 4.5], [14.5, 11.25, 5.75], "#steel"),
        cube([1.5, 0, 10.25], [2.75, 11.25, 11.5], "#steel"),
        cube([13.25, 0, 10.25], [14.5, 11.25, 11.5], "#steel"),
        cube([6, 11, 7.5], [7, 12.25, 8.5], "#steel"),
        cube([9, 11, 7.5], [10, 12.25, 8.5], "#steel"),
        cube([6, 12.25, 7.4], [10, 13.1, 8.6], {"side": "#grip", "end": "#grip"}),
        cube([4, 8.5, 11], [5.5, 10, 11.5], "#steel"),
        cube([10.5, 8.5, 11], [12, 10, 11.5], "#steel"),
        cube([4.5, 2.5, 11], [10.5, 6.5, 11.2], {"south": "#label", "side": "#frame", "end": "#frame"}),
        cube([11.25, 3, 11], [12.25, 4, 11.3], "#led", glow=filled),
        cube([2, 1, 4.6], [14, 2, 5], "#hazard"),
    ]
    if filled:
        for i, z in enumerate((6, 7.4, 8.8)):
            els.append(cube([3.5 + i * 0.4, 11, z], [12.5 - i * 0.4, 12.75 - i * 0.35, z + 0.6],
                            {"side": "#frame", "north": "#slate_top", "south": "#slate_top", "up": "#steel"}))
    return els


# ---------------------------------------------------------------- personal wrightport (worn dock)

def pack(item=False):
    els = [
        cube([3.5, 12, 15], [12.5, 24, 16], "#frame"),
        cube([2.25, 12.5, 10.75], [13.75, 23.5, 15], {"side": "#frame", "north": "#bay", "end": "#frame"}),
        cube([2.25, 21.05, 9.9], [13.75, 23.5, 10.4], {"side": "#steel", "north": "#frame"}),
        cube([2.25, 12.5, 9.9], [13.75, 14.95, 10.4], {"side": "#steel", "north": "#frame"}),
        cube([2.25, 14.95, 9.9], [4.95, 21.05, 10.4], {"side": "#steel", "north": "#frame"}),
        cube([11.05, 14.95, 9.9], [13.75, 21.05, 10.4], {"side": "#steel", "north": "#frame"}),
        cube([1.25, 13.5, 11.5], [2.25, 22.5, 14.25], {"side": "#cell", "end": "#steel"}, glow=True),
        cube([13.75, 13.5, 11.5], [14.75, 22.5, 14.25], {"side": "#cell", "end": "#steel"}, glow=True),
        cube([3.5, 23.5, 11.25], [12.5, 25, 15.5], {"side": "#steel", "up": "#frame"}),
        cube([4.5, 24.2, 10.9], [11.5, 24.7, 11.25], "#led", glow=True),
        cube([10.2, 25, 12.2], [11.6, 25.6, 13.6], "#frame"),
        cube([10.6, 25.6, 12.6], [11.2, 29, 13.2], "#steel"),
        cube([4, 10.75, 11.5], [6.5, 12.5, 14], {"side": "#nozzle_side", "down": "#nozzle", "up": "#frame"}),
        cube([9.5, 10.75, 11.5], [12, 12.5, 14], {"side": "#nozzle_side", "down": "#nozzle", "up": "#frame"}),
        cube([6.5, 11.5, 12], [9.5, 12.5, 13.5], "#heatsink"),
        cube([4.5, 24, 15], [6, 24.5, 20.4], "#strap"),
        cube([10, 24, 15], [11.5, 24.5, 20.4], "#strap"),
        cube([4.5, 15, 20], [6, 24, 20.4], "#strap"),
        cube([10, 15, 20], [11.5, 24, 20.4], "#strap"),
        cube([5.8, 18.5, 20.1], [10.2, 19.5, 20.55], "#steel"),
    ]
    els += ring((8, 18), 9.4, 9.9, 7.2 * PACK_IRIS.k, 3.2 * PACK_IRIS.k, "#bezel", axis="z")
    if item:
        els += PACK_IRIS.closed_blades("#blade")
        els.append(pack_lamp_el())
    return els


def pack_lamp_el():
    return cube([10.4, 29, 12.4], [11.4, 30, 13.4], "#lamp", tint=True, glow=True)


PACK_TEX = dict(frame="frame", steel="steel", bay="pack_bay", cell="cell", led="led_mk1", nozzle="nozzle",
                nozzle_side="nozzle_side", heatsink="heatsink", strap="strap", bezel="duct_mk1",
                blade="iris_blade", lamp="lamp")


# ---------------------------------------------------------------- wrightport tower

LAMPS = [(-0.75, -0.75), (14.75, -0.75), (-0.75, 14.75), (14.75, 14.75)]


def tower():
    els = [
        cube([-1, 0, -1], [17, 2, 17], {"side": "#hazard", "end": "#frame"}),
        cube([0, 2, 0], [16, 14, 16], {"side": "#panel", "end": None}),
        cube([4, 3, -0.4], [12, 12.5, 0], {"north": "#door", "side": "#steel", "end": "#steel"}),
        cube([16, 4, 4], [16.8, 12, 12], "#heatsink"),
        cube([-0.8, 4, 4], [0, 12, 12], "#heatsink"),
        cube([-0.5, 14, -0.5], [16.5, 16, 16.5], {"side": "#band", "end": "#frame"}),
        cube([1, 16, 1], [15, 28, 15], {"side": "#core", "end": None}),
        cube([11.5, 2, 16], [12.75, 28, 16.75], {"side": "#nozzle_side", "end": "#steel"}),
        cube([-0.75, 2, 3.25], [0, 14, 4.5], {"side": "#nozzle_side", "end": "#steel"}),
        cube([-1, 28, -1], [17, 30, 17], {"side": "#frame", "up": "#bay", "down": "#frame"}),
        cube([-1, 30, -1], [17, 31, 3], {"side": "#steel", "end": "#frame"}),
        cube([-1, 30, 13], [17, 31, 17], {"side": "#steel", "end": "#frame"}),
        cube([-1, 30, 3], [3, 31, 13], {"side": "#steel", "end": "#frame"}),
        cube([13, 30, 3], [17, 31, 13], {"side": "#steel", "end": "#frame"}),
    ]
    for x, z in ((-1, -1), (14.5, -1), (-1, 14.5), (14.5, 14.5)):
        els.append(cube([x, 2, z], [x + 2.5, 28, z + 2.5], {"side": "#pillar", "end": None}))
    for frm, to in (
        ([5.5, 18, 0.6], [10.5, 26, 1]), ([5.5, 18, 15], [10.5, 26, 15.4]),
        ([0.6, 18, 5.5], [1, 26, 10.5]), ([15, 18, 5.5], [15.4, 26, 10.5]),
    ):
        els.append(cube(frm, to, "#glow", glow=True))
    for z0, z1 in ((0.4, 1.2), (14.8, 15.6)):
        for y in (17.4, 26):
            els.append(cube([5, y, z0], [11, y + 0.6, z1], "#steel"))
    for x0, x1 in ((0.4, 1.2), (14.8, 15.6)):
        for y in (17.4, 26):
            els.append(cube([x0, y, 5], [x1, y + 0.6, 11], "#steel"))
    for frm, to in (
        ([2, 31, -0.6], [14, 31.2, 0]), ([2, 31, 16], [14, 31.2, 16.6]),
        ([-0.6, 31, 2], [0, 31.2, 14]), ([16, 31, 2], [16.6, 31.2, 14]),
    ):
        els.append(cube(frm, to, "#led", glow=True))
    els += ring((8, 8), 31, 31.6, 7.2, 3.2, "#bezel")
    return els


def tower_lamps():
    return [cube([x, 31, z], [x + 2, 32, z + 2], "#lamp", tint=True, glow=True) for x, z in LAMPS]


TOWER_TEX = dict(frame="frame", steel="steel", hazard="hazard", panel="port_panel", door="port_door",
                 heatsink="heatsink", band="duct_mk1", core="port_core", glow="core_glow",
                 nozzle_side="nozzle_side", bay="bay", pillar="pillar", led="led_mk1", bezel="duct_mk1",
                 blade="iris_blade", lamp="lamp")


# ---------------------------------------------------------------- displays

ITEM_DISPLAY = json.loads((MODELS / "drone_part.json").read_text(encoding="utf-8"))["display"]

FLAT_DISPLAY = {
    **ITEM_DISPLAY,
    "gui": {"rotation": [45, 225, 0], "translation": [0, 1.5, 0], "scale": [0.8, 0.8, 0.8]},
    "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -1], "scale": [0.75, 0.75, 0.75]},
    "firstperson_righthand": {"rotation": [-20, 45, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [-20, 225, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
}

PACK_DISPLAY = {
    "gui": {"rotation": [20, 160, 0], "translation": [-7.3, -6, 0], "scale": [0.78, 0.78, 0.78]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, 0], "scale": [0.6, 0.6, 0.6]},
    "head": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, -1, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, -1, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 315, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
}

TOWER_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -4.25, 0], "scale": [0.36, 0.36, 0.36]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 6, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]},
}


def main():
    for fn in (tex_shell, tex_accent, tex_screen, tex_grip, tex_slate, tex_holo, tex_case, tex_label, tex_led_off,
               tex_port_panel, tex_port_core, tex_core_glow, tex_bay, tex_iris_blade, tex_pillar, tex_lamp,
               tex_port_door, tex_pack_bay, tex_cell, tex_strap):
        fn()
    tex_blade("drone", (206, 212, 220), (246, 248, 250))

    # Wright: only the chassis is a model. Rotors and the Fabricator Head are drawn on it from the fitted
    # parts, by the entity renderer in the world and by the item renderer for the icon.
    write(PART_MODELS / "drone_body.json", {"textures": textures(**{**DRONE_TEX, "accent": "accent"}),
                                            "elements": drone_body()})
    write(MODELS / "wright.json", {"parent": "minecraft:builtin/entity", "gui_light": "side",
                                   "textures": {"particle": T("shell")}, "display": PARENT["display"]})

    write(MODELS / "controller.json", {
        "parent": "wrights:item/drone_part",
        "textures": textures(frame="frame", steel="steel", screen="screen", grip="grip", led="led_mk1",
                             lens="lens_mk3", motor_top="motor_top", btn0="led_mk2", btn1="led_mk3",
                             btn2="led_mk5", btn3="led_mk4"),
        "elements": controller(),
        "display": FLAT_DISPLAY,
    })

    slate_tex = dict(frame="frame", steel="steel", led="led_mk3", led_b="led_off", lens="lens_mk3", holo="holo")
    write(MODELS / "blueprint.json", {
        "parent": "wrights:item/drone_part",
        "textures": textures(**slate_tex, slate_top="slate"),
        "elements": slate(False),
        "display": FLAT_DISPLAY,
        "overrides": [{"predicate": {"wrights:filled": 1.0}, "model": "wrights:item/blueprint_filled"}],
    })
    write(MODELS / "blueprint_filled.json", {
        "parent": "wrights:item/drone_part",
        "textures": textures(**{**slate_tex, "led_b": "led_mk2"}, slate_top="slate_filled"),
        "elements": slate(True),
        "display": FLAT_DISPLAY,
    })

    case_tex = dict(case="case", frame="frame", steel="steel", grip="grip", label="label", hazard="hazard",
                    slate_top="slate_filled")
    write(MODELS / "blueprint_folder.json", {
        "parent": "wrights:item/drone_part",
        "textures": textures(**case_tex, led="led_off"),
        "elements": case(False),
        "overrides": [{"predicate": {"wrights:filled": 1.0}, "model": "wrights:item/blueprint_folder_filled"}],
    })
    write(MODELS / "blueprint_folder_filled.json", {
        "parent": "wrights:item/drone_part",
        "textures": textures(**case_tex, led="led_mk2"),
        "elements": case(True),
    })

    # Worn dock: static shell, one iris blade, and the antenna lamp. The item shows it closed.
    write(PART_MODELS / "pack.json", {"textures": textures(**PACK_TEX), "elements": pack()})
    write(PART_MODELS / "pack_blade.json", {"textures": textures(**PACK_TEX),
                                            "elements": [PACK_IRIS.blade_model("#blade")]})
    write(PART_MODELS / "pack_lamp.json", {"textures": textures(**PACK_TEX), "elements": [pack_lamp_el()]})
    write(MODELS / "personal_wrightport.json", {"gui_light": "side", "textures": textures(**PACK_TEX),
                                                "elements": pack(item=True), "display": PACK_DISPLAY})

    # Wrightport tower: two blocks tall. The lower half draws everything; the upper half is collision only.
    write(BLOCK_MODELS / "wrightport.json", {"ambientocclusion": False, "textures": textures(**TOWER_TEX),
                                             "elements": tower()})
    write(BLOCK_MODELS / "wrightport_upper.json", {"textures": {"particle": T("frame")}, "elements": []})
    write(PART_MODELS / "iris_blade.json", {"textures": textures(**TOWER_TEX),
                                            "elements": [BLOCK_IRIS.blade_model("#blade")]})
    write(PART_MODELS / "wrightport_lamps.json", {"textures": textures(**TOWER_TEX), "elements": tower_lamps()})
    write(MODELS / "wrightport.json", {"ambientocclusion": False, "gui_light": "side",
                                       "textures": textures(**TOWER_TEX),
                                       "elements": tower() + BLOCK_IRIS.closed_blades("#blade") + tower_lamps(),
                                       "display": TOWER_DISPLAY})
    write(BLOCKSTATES / "wrightport.json", {"variants": {
        "half=lower": {"model": "wrights:block/wrightport"},
        "half=upper": {"model": "wrights:block/wrightport_upper"},
    }})


if __name__ == "__main__":
    main()
