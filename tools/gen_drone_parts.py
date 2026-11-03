"""Generate the 3D drone-part items: Rotor Assembly (rotor_mk1..5, flight) and Fabricator Head
(fabricator_mk1..5, blocks per operation).
Run from the repo root: python tools/gen_drone_parts.py
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "wrights"
TEX = ASSETS / "textures" / "item" / "part"
MODELS = ASSETS / "models" / "item"

S = 32

# Scout..Apex: (mark, accent, accent light)
TIERS = [
    (1, (255, 150, 40), (255, 214, 140)),
    (2, (80, 236, 110), (196, 255, 204)),
    (3, (60, 206, 255), (190, 240, 255)),
    (4, (170, 112, 255), (226, 204, 255)),
    (5, (255, 52, 88), (255, 186, 198)),
]

GUN = (64, 69, 80)
STEEL = (150, 157, 168)
COPPER = (198, 116, 58)


def clamp(v: float) -> int:
    return max(0, min(255, int(round(v))))


def add(c, d):
    return tuple(clamp(x + d) for x in c[:3])


def mul(c, k):
    return tuple(clamp(x * k) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def image():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def put(img, x, y, c, a=255):
    if 0 <= x < S and 0 <= y < S:
        img.putpixel((x, y), (*c[:3], a))


def get(img, x, y):
    return img.getpixel((x, y))[:3]


def noisy(rng, c, amt):
    return add(c, rng.randint(-amt, amt))


def bevel(img, light=36, dark=-34):
    for i in range(S):
        put(img, i, 0, add(get(img, i, 0), light))
        put(img, 0, i, add(get(img, 0, i), light))
        put(img, i, 1, add(get(img, i, 1), light // 2))
        put(img, 1, i, add(get(img, 1, i), light // 2))
        put(img, i, S - 1, add(get(img, i, S - 1), dark))
        put(img, S - 1, i, add(get(img, S - 1, i), dark))
        put(img, i, S - 2, add(get(img, i, S - 2), dark // 2))
        put(img, S - 2, i, add(get(img, S - 2, i), dark // 2))


def bolt(img, cx, cy):
    ring = [(-1, -1, 30), (0, -1, 40), (1, -1, 10), (-1, 0, 40), (1, 0, -30),
            (-1, 1, 10), (0, 1, -30), (1, 1, -40)]
    for dx, dy, d in ring:
        put(img, cx + dx, cy + dy, add((112, 118, 130), d))
    put(img, cx, cy, (196, 202, 212))
    put(img, cx - 2, cy, add(get(img, cx - 2, cy), -18))
    put(img, cx, cy + 2, add(get(img, cx, cy + 2), -18))


def save(img, name):
    TEX.mkdir(parents=True, exist_ok=True)
    img.save(TEX / f"{name}.png")


def cyl(x):
    """Fake cylinder shading across a face, brightest a little left of centre."""
    t = x / (S - 1)
    return 0.62 + 0.5 * math.sin(math.pi * t) + 0.16 * math.cos(math.pi * (t - 0.32) * 2.2)


# ---------------------------------------------------------------- textures

def tex_frame():
    rng = random.Random(11)
    img = image()
    for y in range(S):
        for x in range(S):
            put(img, x, y, noisy(rng, add(GUN, (S - y) // 6), 4))
    bevel(img)
    for i in range(5, 27):
        put(img, i, 5, add(GUN, -26))
        put(img, 5, i, add(GUN, -26))
        put(img, i, 26, add(GUN, 16))
        put(img, 26, i, add(GUN, 16))
    for sy in (11, 15, 19):
        for x in range(9, 23):
            put(img, x, sy, (18, 20, 24))
            put(img, x, sy + 1, (30, 33, 38))
            put(img, x, sy + 2, add(GUN, 18))
        put(img, 8, sy, add(GUN, -10))
        put(img, 23, sy + 1, add(GUN, 10))
    for x in range(8, 14):
        for y in range(22, 25):
            put(img, x, y, (96, 102, 112) if (x + y) % 3 else (70, 74, 82))
    put(img, 20, 23, (255, 176, 48))
    put(img, 21, 23, (255, 210, 120))
    for cx, cy in ((3, 3), (28, 3), (3, 28), (28, 28)):
        bolt(img, cx, cy)
    save(img, "frame")


def tex_steel():
    rng = random.Random(12)
    img = image()
    rows = [rng.randint(-9, 9) for _ in range(S)]
    for y in range(S):
        for x in range(S):
            c = add(STEEL, rows[y] + rng.randint(-3, 3) - y // 3)
            put(img, x, y, c)
    for x in range(S):
        put(img, x, 15, add(get(img, x, 15), -28))
        put(img, x, 16, add(get(img, x, 16), 18))
    bevel(img, 40, -50)
    bolt(img, 4, 4)
    bolt(img, 27, 27)
    save(img, "steel")


def tex_motor():
    rng = random.Random(13)
    img = image()
    for y in range(S):
        for x in range(S):
            k = cyl(x)
            col = x % 6
            if y < 4:
                c = mul(add(STEEL, 20 - y * 8), k)
            elif y > 27:
                c = mul(add(GUN, -14 - (y - 28) * 5), k)
            elif col == 4:
                wire = COPPER if (y // 2) % 2 else add(COPPER, -36)
                c = mul(add(wire, 30 if y < 9 else 0), k)
            elif col in (3, 5):
                c = mul((22, 24, 28), k)
            else:
                c = mul(add(GUN, 10 if col == 0 else -4), k)
            put(img, x, y, noisy(rng, c, 3))
    for x in range(S):
        put(img, x, 4, mul((26, 28, 32), cyl(x)))
        put(img, x, 27, mul(add(STEEL, -40), cyl(x)))
        put(img, x, 16, mul((40, 44, 52), cyl(x)))
    save(img, "motor")


def tex_motor_top():
    rng = random.Random(14)
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c0, y - c0)
            ang = math.atan2(y - c0, x - c0)
            light = -math.cos(ang + math.pi / 4) * 22
            if r > 15.2:
                c = (30, 32, 38)
            elif r > 13.6:
                c = add(STEEL, light - 10)
            elif r > 7.2:
                c = add(GUN, light * 0.6 + (6 if int(r) % 2 else -4))
            elif r > 6:
                c = (26, 28, 32)
            else:
                c = add(STEEL, 30 - r * 6 + light * 0.8)
            put(img, x, y, noisy(rng, c, 3))
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        vx, vy = c0 + math.cos(a) * 10.4, c0 + math.sin(a) * 10.4
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                put(img, int(round(vx + dx)), int(round(vy + dy)), (14, 15, 18))
        put(img, int(round(vx + 1)), int(round(vy + 2)), add(GUN, 26))
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (0, 0)):
        put(img, 15 + dx, 15 + dy, (40, 42, 48))
    put(img, 16, 16, (40, 42, 48))
    put(img, 14, 14, (220, 226, 234))
    save(img, "motor_top")


def tex_heatsink():
    img = image()
    for y in range(S):
        for x in range(S):
            col = x % 4
            base = [add(STEEL, 26), add(STEEL, -6), add(STEEL, -30), (34, 36, 42)][col]
            put(img, x, y, add(base, -y // 2))
    for x in range(S):
        put(img, x, 0, add(get(img, x, 0), 30))
        put(img, x, S - 1, (26, 28, 32))
    save(img, "heatsink")


def tex_hazard():
    rng = random.Random(15)
    img = image()
    for y in range(S):
        for x in range(S):
            yellow = ((x + y) // 6) % 2 == 0
            c = (232, 182, 44) if yellow else (30, 30, 34)
            if rng.random() < 0.06:
                c = add(c, -40 if yellow else 30)
            put(img, x, y, c)
    for i in range(S):
        for d in (0, 1, S - 2, S - 1):
            put(img, i, d, add(GUN, 10 if d < 2 else -16))
            put(img, d, i, add(GUN, 10 if d < 2 else -16))
    bevel(img, 24, -24)
    save(img, "hazard")


def tex_nozzle():
    rng = random.Random(16)
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c0, y - c0)
            ang = math.atan2(y - c0, x - c0)
            light = -math.cos(ang + math.pi / 4) * 26
            if r > 15.2:
                c = add(GUN, -20)
            elif r < 4.5:
                c = mix((8, 9, 12), (40, 44, 52), r / 4.5)
            else:
                band = int(r) // 2 % 2
                c = add(STEEL if band else add(STEEL, -52), light)
            put(img, x, y, noisy(rng, c, 3))
    save(img, "nozzle")


def tex_nozzle_side():
    rng = random.Random(17)
    img = image()
    for y in range(S):
        for x in range(S):
            groove = y % 8 in (6, 7)
            base = (30, 32, 38) if groove else add(STEEL, -y)
            if y % 8 == 0:
                base = add(STEEL, 34)
            put(img, x, y, noisy(rng, mul(base, cyl(x)), 2))
    save(img, "nozzle_side")


def tex_blade(name, accent, light):
    img = image()
    for y in range(S):
        for x in range(S):
            d = abs(x - 15.5) / 15.5
            twill = ((x + y // 2) // 2) % 2
            c = (40, 43, 50) if twill else (28, 30, 35)
            if y < 3:
                c = (124, 130, 142) if y == 0 else (88, 94, 104)
            elif y > 28:
                c = (20, 21, 25)
            if d < 0.13:
                c = add(STEEL, -30 + (10 if y < 4 else 0))
            elif d > 0.8:
                c = light if y < 3 else mix(accent, mul(accent, 0.55), (y - 3) / 28)
            elif 0.72 < d <= 0.76:
                c = (210, 214, 220) if y < 29 else (90, 94, 100)
            put(img, x, y, c)
    save(img, f"blade_{name}")


def tex_led(name, accent, light):
    img = image()
    for y in range(S):
        for x in range(S):
            t = 1 - abs(y - 15.5) / 15.5
            c = mix(mul(accent, 0.55), mix(accent, light, 0.7), t ** 1.6)
            if x % 8 == 7:
                c = mul(c, 0.72)
            put(img, x, y, c)
    for i in range(S):
        put(img, i, 0, (26, 28, 32))
        put(img, i, S - 1, (26, 28, 32))
        put(img, 0, i, (26, 28, 32))
        put(img, S - 1, i, (26, 28, 32))
    save(img, f"led_{name}")


def tex_lens(name, accent, light):
    img = image()
    c0 = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c0, y - c0)
            ang = math.atan2(y - c0, x - c0)
            rim = -math.cos(ang + math.pi / 4) * 30
            if r > 14.5:
                c = add(GUN, rim * 0.5 - 8)
            elif r > 12.2:
                c = add(STEEL, rim)
            else:
                t = r / 12.2
                c = mix(mix(light, (255, 255, 255), 0.5), mul(accent, 0.5), t ** 0.8)
                if 0.55 < t < 0.62:
                    c = mul(c, 0.82)
            put(img, x, y, c)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1), (1, 1)):
        put(img, 10 + dx, 10 + dy, (255, 255, 255))
    put(img, 20, 21, mix(light, (255, 255, 255), 0.6))
    save(img, f"lens_{name}")


def tex_duct(name, accent, light):
    rng = random.Random(18)
    img = image()
    for y in range(S):
        for x in range(S):
            if y < 4:
                c = add(STEEL, 26 - y * 10)
            elif 12 <= y <= 15:
                c = mix(accent, light, 0.5 if y in (13, 14) else 0.0)
            elif y in (11, 16):
                c = (20, 22, 26)
            elif y > 27:
                c = add(GUN, -24)
            else:
                c = noisy(rng, add(GUN, -(y - 16) // 2), 3)
            put(img, x, y, c)
    for x in range(4, S, 8):
        put(img, x, 21, (176, 182, 192))
        put(img, x + 1, 22, (30, 32, 36))
    save(img, f"duct_{name}")


# ---------------------------------------------------------------- models

UV = [0, 0, 16, 16]
FACES = ("north", "east", "south", "west", "up", "down")


def box(frm, to, tex, rot=None, glow=False, spin_z_faces=False):
    if isinstance(tex, str):
        tex = {"side": tex, "end": tex}
    faces = {}
    for f in FACES:
        key = tex["end"] if f in ("up", "down") else tex["side"]
        face = {"uv": UV, "texture": key}
        if spin_z_faces and f in ("up", "down"):
            face["rotation"] = 90
        faces[f] = face
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rot:
        el["rotation"] = rot
    if glow:
        el["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def rot(axis, angle, origin):
    return {"angle": angle, "axis": axis, "origin": list(origin)}


def octagon(y0, y1, r_out, thick, tex, glow=False):
    """Eight flat segments around the model centre; diagonals are the straight ones turned 45°."""
    half = r_out * math.tan(math.pi / 8)
    r_in = r_out - thick
    a, b = round(8 - half, 3), round(8 + half, 3)
    north = ([a, y0, 8 - r_out], [b, y1, 8 - r_in])
    south = ([a, y0, 8 + r_in], [b, y1, 8 + r_out])
    west = ([8 - r_out, y0, a], [8 - r_in, y1, b])
    east = ([8 + r_in, y0, a], [8 + r_out, y1, b])
    centre = [8, (y0 + y1) / 2, 8]
    out = []
    for frm, to in (north, south, west, east):
        out.append(box(frm, to, tex, glow=glow))
    for frm, to in (north, south):
        out.append(box(frm, to, tex, rot("y", 45, centre), glow))
        out.append(box(frm, to, tex, rot("y", -45, centre), glow))
    return out


def blade_x(y, tex, pitch=22.5, yaw=None):
    frm, to = [1, y, 7.1], [15, y + 0.5, 8.9]
    r = rot("y", yaw, [8, y + 0.25, 8]) if yaw is not None else rot("x", pitch, [8, y + 0.25, 8])
    return box(frm, to, tex, r)


def blade_z(y, tex, pitch=22.5, yaw=None):
    frm, to = [7.1, y, 1], [8.9, y + 0.5, 15]
    r = rot("y", yaw, [8, y + 0.25, 8]) if yaw is not None else rot("z", pitch, [8, y + 0.25, 8])
    return box(frm, to, tex, r, spin_z_faces=True)


def rotor(name, mark):
    t = {
        "frame": "wrights:item/part/frame",
        "steel": "wrights:item/part/steel",
        "motor": "wrights:item/part/motor",
        "motor_top": "wrights:item/part/motor_top",
        "heatsink": "wrights:item/part/heatsink",
        "blade": f"wrights:item/part/blade_{name}",
        "led": f"wrights:item/part/led_{name}",
        "particle": "wrights:item/part/frame",
    }
    if mark >= 3:
        t["duct"] = f"wrights:item/part/duct_{name}"
    motor = {"side": "#motor", "end": "#motor_top"}
    els = [
        box([0.5, 5, 7], [6, 7, 9], "#frame"),
        box([4.5, 4.5, 6.5], [5.5, 7.5, 9.5], "#steel"),
        box([5.5, 3.5, 5.5], [10.5, 4.5, 10.5], "#frame"),
        box([6, 4.5, 6], [10, 8, 10], motor),
        box([6.5, 8, 6.5], [9.5, 8.5, 9.5], "#led" if mark >= 5 else "#steel", glow=mark >= 5),
        box([7.5, 8.5, 7.5], [8.5, 9.5, 8.5], "#steel"),
        box([7, 9.25, 7], [9, 10.25, 9], {"side": "#frame", "end": "#motor_top"}),
        box([7.5, 10.25, 7.5], [8.5, 11, 8.5], "#led" if mark >= 4 else "#steel", glow=mark >= 4),
    ]
    if mark >= 4:
        els.append(box([1.5, 7, 6.75], [4, 8, 9.25], {"side": "#frame", "end": "#heatsink"}))
        els.append(box([2, 8, 7.5], [3.5, 8.25, 8.5], "#led", glow=True))
    else:
        els.append(box([1.5, 7, 7.5], [4, 7.25, 8.5], "#led", glow=True))

    els.append(blade_x(9.5, "#blade"))
    if mark >= 2:
        els.append(blade_z(9.5, "#blade"))
        for frm, to in (
            ([10, 5, 6.6], [10.75, 7.5, 7.1]), ([10, 5, 8.9], [10.75, 7.5, 9.4]),
            ([6.6, 5, 10], [7.1, 7.5, 10.75]), ([8.9, 5, 10], [9.4, 7.5, 10.75]),
            ([6.6, 5, 5.25], [7.1, 7.5, 6]), ([8.9, 5, 5.25], [9.4, 7.5, 6]),
        ):
            els.append(box(frm, to, "#heatsink"))
    if mark >= 3:
        els += octagon(9, 10.5, 7.4, 0.8, "#duct")
        for x0, x1 in ((1.4, 6.5), (9.5, 14.6)):
            for yaw in (45, -45):
                els.append(box([x0, 8.5, 7.75], [x1, 9, 8.25], "#steel", rot("y", yaw, [8, 8.75, 8])))
        els.append(box([0.75, 7, 7.5], [1.25, 9, 8.5], "#steel"))
    if mark >= 4:
        els.append(blade_x(9.5, "#blade", yaw=45))
        els.append(blade_x(9.5, "#blade", yaw=-45))
    if mark >= 5:
        els.append(box([7, 2, 7], [9, 3.5, 9], {"side": "#frame", "end": "#motor_top"}))
        els.append(blade_x(2.5, "#blade", yaw=22.5))
        els.append(blade_z(2.5, "#blade", yaw=22.5))
        els += octagon(1.75, 3.25, 7.4, 0.8, "#duct")
        for frm, to in (
            ([7.5, 3.25, 0.7], [8.5, 9, 1.3]), ([7.5, 3.25, 14.7], [8.5, 9, 15.3]),
            ([14.7, 3.25, 7.5], [15.3, 9, 8.5]),
        ):
            els.append(box(frm, to, "#steel"))
        for z in (4.5, 8, 11.5):
            els.append(box([3, 1, z - 0.4], [13, 1.4, z + 0.4], "#heatsink", rot("x", 22.5, [8, 1.2, z])))
    return {"parent": "wrights:item/drone_part", "textures": t, "elements": els}


def fabricator(name, mark):
    t = {
        "frame": "wrights:item/part/frame",
        "steel": "wrights:item/part/steel",
        "hazard": "wrights:item/part/hazard",
        "nozzle": "wrights:item/part/nozzle",
        "nozzle_side": "wrights:item/part/nozzle_side",
        "lens": f"wrights:item/part/lens_{name}",
        "led": f"wrights:item/part/led_{name}",
        "particle": "wrights:item/part/frame",
    }
    if mark >= 4:
        t["motor"] = "wrights:item/part/motor"
        t["motor_top"] = "wrights:item/part/motor_top"
    if mark >= 5:
        t["duct"] = f"wrights:item/part/duct_{name}"
    nozzle = {"side": "#nozzle_side", "end": "#nozzle"}
    els = [
        box([5, 0, 5], [11, 1, 11], "#frame"),
        box([3, 1, 3], [13, 4, 13], "#frame"),
        box([2.5, 4, 2.5], [13.5, 5, 13.5], {"side": "#steel", "end": "#frame"}),
        box([1, 1.5, 6], [3, 3.5, 10], "#hazard"),
        box([4.5, 2, 13], [11.5, 2.75, 13.25], "#led", glow=True),
        box([13, 2, 4.5], [13.25, 2.75, 11.5], "#led", glow=True),
        box([6, 5, 6], [10, 5.75, 10], "#frame"),
        box([6.5, 5.75, 6.5], [9.5, 8.5, 9.5], nozzle),
        box([7, 8.5, 7], [9, 8.9, 9], "#lens", glow=True),
    ]

    def emitter(x, z, h, half=1.0):
        els.append(box([x - half, 5, z - half], [x + half, 5 + h, z + half], nozzle))
        lens = half * 0.6
        els.append(box([x - lens, 5 + h, z - lens], [x + lens, 5 + h + 0.3, z + lens], "#lens", glow=True))

    sats = [(8, 4), (8, 12)]
    if mark >= 2:
        sats += [(4, 8), (12, 8)]
    for x, z in sats:
        emitter(x, z, 2.25 if mark >= 3 else 2)
    if mark >= 3:
        for x, z in ((4.25, 4.25), (11.75, 4.25), (4.25, 11.75), (11.75, 11.75)):
            emitter(x, z, 1.5, 0.75)
    if mark >= 4:
        coil = {"side": "#motor", "end": "#motor_top"}
        els.append(box([13, 1.25, 3.25], [14.75, 3.75, 5.25], coil))
        els.append(box([13, 1.25, 10.75], [14.75, 3.75, 12.75], coil))

        def claw(base_frm, base_to, arm_frm, arm_to, r):
            els.append(box(base_frm, base_to, "#steel"))
            els.append(box(arm_frm, arm_to, "#frame", r))

        claw([1.75, 5, 7], [3.25, 6.5, 9], [2, 6.5, 7.5], [3, 11, 8.5], rot("z", -22.5, [2.5, 6.5, 8]))
        claw([12.75, 5, 7], [14.25, 6.5, 9], [13, 6.5, 7.5], [14, 11, 8.5], rot("z", 22.5, [13.5, 6.5, 8]))
        if mark >= 5:
            claw([7, 5, 1.75], [9, 6.5, 3.25], [7.5, 6.5, 2], [8.5, 11, 3], rot("x", 22.5, [8, 6.5, 2.5]))
            claw([7, 5, 12.75], [9, 6.5, 14.25], [7.5, 6.5, 13], [8.5, 11, 14], rot("x", -22.5, [8, 6.5, 13.5]))
    if mark >= 5:
        els += octagon(10.25, 10.85, 5.6, 0.7, "#duct")
    return {"parent": "wrights:item/drone_part", "textures": t, "elements": els}


PARENT = {
    "gui_light": "side",
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0.5, 0], "scale": [0.66, 0.66, 0.66]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -2], "scale": [0.6, 0.6, 0.6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    },
}


def write_model(name, data):
    (MODELS / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    tex_frame()
    tex_steel()
    tex_motor()
    tex_motor_top()
    tex_heatsink()
    tex_hazard()
    tex_nozzle()
    tex_nozzle_side()
    write_model("drone_part", PARENT)
    for mark, accent, light in TIERS:
        tag = f"mk{mark}"
        tex_blade(tag, accent, light)
        tex_led(tag, accent, light)
        tex_lens(tag, accent, light)
        tex_duct(tag, accent, light)
        write_model(f"rotor_mk{mark}", rotor(tag, mark))
        write_model(f"fabricator_mk{mark}", fabricator(tag, mark))


if __name__ == "__main__":
    main()
