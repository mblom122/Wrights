"""Generate factory/drone-styled pixel textures for Wrights."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "wrights" / "textures"

# Industrial palette
INK = (18, 22, 28, 255)
STEEL = (58, 68, 80, 255)
STEEL_L = (92, 104, 118, 255)
STEEL_D = (36, 42, 50, 255)
PANEL = (44, 52, 62, 255)
RIVET = (170, 178, 188, 255)
COPPER = (196, 118, 62, 255)
COPPER_L = (230, 156, 88, 255)
COPPER_D = (140, 78, 40, 255)
AMBER = (255, 176, 48, 255)
AMBER_L = (255, 220, 120, 255)
CYAN = (64, 200, 255, 255)
CYAN_L = (180, 240, 255, 255)
CYAN_D = (24, 96, 140, 255)
WHITE = (236, 240, 244, 255)
HAZARD = (232, 184, 48, 255)
VOID = (0, 0, 0, 0)
IRON = (168, 168, 176, 255)
GOLD = (232, 196, 64, 255)
DIAMOND = (88, 220, 232, 255)
NETHER = (64, 52, 58, 255)
NETHER_L = (110, 72, 82, 255)


def new(w: int = 16, h: int = 16) -> Image.Image:
    return Image.new("RGBA", (w, h), VOID)


def px(img: Image.Image, x: int, y: int, c) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def fill(img: Image.Image, x0: int, y0: int, x1: int, y1: int, c) -> None:
    for y in range(y0, y1):
        for x in range(x0, x1):
            px(img, x, y, c)


def rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, c) -> None:
    for x in range(x0, x1):
        px(img, x, y0, c)
        px(img, x, y1 - 1, c)
    for y in range(y0, y1):
        px(img, x0, y, c)
        px(img, x1 - 1, y, c)


def rivets(img: Image.Image, pts, c=RIVET) -> None:
    for x, y in pts:
        px(img, x, y, c)


def panel_plate(img: Image.Image, x0=0, y0=0, x1=16, y1=16) -> None:
    fill(img, x0, y0, x1, y1, STEEL_D)
    fill(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PANEL)
    fill(img, x0 + 2, y0 + 2, x1 - 2, y1 - 2, STEEL)
    # bevel
    for x in range(x0 + 2, x1 - 2):
        px(img, x, y0 + 2, STEEL_L)
        px(img, x, y1 - 3, STEEL_D)
    for y in range(y0 + 2, y1 - 2):
        px(img, x0 + 2, y, STEEL_L)
        px(img, x1 - 3, y, STEEL_D)
    rivets(img, [(x0 + 3, y0 + 3), (x1 - 4, y0 + 3), (x0 + 3, y1 - 4), (x1 - 4, y1 - 4)])


def save(img: Image.Image, rel: str) -> None:
    path = TEX / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(ROOT))


def make_wrightport_base() -> None:
    img = new()
    panel_plate(img)
    # center dock ring
    rect(img, 4, 4, 12, 12, STEEL_D)
    fill(img, 5, 5, 11, 11, STEEL_D)
    rect(img, 5, 5, 11, 11, COPPER)
    fill(img, 6, 6, 10, 10, INK)
    px(img, 7, 7, CYAN)
    px(img, 8, 7, CYAN_L)
    px(img, 7, 8, CYAN_L)
    px(img, 8, 8, CYAN)
    save(img, "block/wrightport_base.png")


def make_wrightport_post() -> None:
    img = new()
    fill(img, 0, 0, 16, 16, STEEL_D)
    fill(img, 2, 0, 14, 16, STEEL)
    for y in range(0, 16, 3):
        fill(img, 2, y, 14, y + 1, STEEL_L if y % 6 == 0 else STEEL_D)
    for y in (2, 8, 13):
        px(img, 3, y, RIVET)
        px(img, 12, y, RIVET)
    fill(img, 6, 0, 10, 16, COPPER_D)
    fill(img, 7, 0, 9, 16, COPPER)
    save(img, "block/wrightport_post.png")


def make_wrightport_core() -> None:
    img = new()
    fill(img, 0, 0, 16, 16, CYAN_D)
    fill(img, 2, 2, 14, 14, CYAN)
    fill(img, 4, 4, 12, 12, CYAN_L)
    fill(img, 6, 6, 10, 10, WHITE)
    # scanlines
    for y in range(2, 14, 2):
        for x in range(2, 14):
            r, g, b, a = img.getpixel((x, y))
            px(img, x, y, (max(0, r - 30), max(0, g - 20), b, a))
    save(img, "block/wrightport_core.png")


def make_wrightport_light() -> None:
    img = new()
    fill(img, 0, 0, 16, 16, COPPER_D)
    fill(img, 3, 3, 13, 13, AMBER)
    fill(img, 5, 5, 11, 11, AMBER_L)
    fill(img, 7, 7, 9, 9, WHITE)
    save(img, "block/wrightport_light.png")


def make_wrightport_copper() -> None:
    img = new()
    fill(img, 0, 0, 16, 16, COPPER_D)
    fill(img, 1, 1, 15, 15, COPPER)
    for y in range(2, 14, 3):
        fill(img, 2, y, 14, y + 1, COPPER_L)
    for x in (3, 8, 12):
        for y in (3, 8, 12):
            px(img, x, y, STEEL_D)
    save(img, "block/wrightport_copper.png")


def make_hangar_textures() -> None:
    # side panel with vents
    side = new()
    panel_plate(side)
    fill(side, 5, 4, 11, 12, INK)
    for y in range(5, 12, 2):
        fill(side, 6, y, 10, y + 1, STEEL_L)
    fill(side, 2, 13, 14, 15, COPPER_D)
    fill(side, 3, 13, 13, 14, COPPER)
    save(side, "block/hangar_side.png")

    front = new()
    panel_plate(front)
    # screen
    fill(front, 3, 3, 13, 10, INK)
    rect(front, 3, 3, 13, 10, CYAN_D)
    fill(front, 4, 4, 12, 9, CYAN_D)
    # blueprint grid
    for x in range(5, 12, 2):
        for y in range(5, 9):
            px(front, x, y, CYAN)
    px(front, 6, 5, CYAN_L)
    px(front, 7, 6, WHITE)
    # status LEDs
    px(front, 4, 11, AMBER_L)
    px(front, 6, 11, CYAN_L)
    px(front, 8, 11, STEEL_L)
    px(front, 10, 11, AMBER)
    # hazard stripe
    for x in range(2, 14):
        px(front, x, 13, HAZARD if (x // 2) % 2 == 0 else INK)
        px(front, x, 14, HAZARD if (x // 2) % 2 == 0 else INK)
    save(front, "block/hangar_front.png")

    top = new()
    panel_plate(top)
    # circular hangar hatch opening
    fill(top, 4, 4, 12, 12, STEEL_D)
    rect(top, 4, 4, 12, 12, COPPER)
    fill(top, 5, 5, 11, 11, INK)
    # ring highlights
    for x, y in ((5, 6), (6, 5), (9, 5), (10, 6), (5, 9), (6, 10), (9, 10), (10, 9)):
        px(top, x, y, STEEL_L)
    # north antenna pad mark
    fill(top, 7, 1, 9, 3, INK)
    px(top, 7, 1, STEEL_L)
    px(top, 8, 1, STEEL_L)
    # rail
    fill(top, 1, 1, 15, 2, COPPER)
    fill(top, 1, 14, 15, 15, COPPER)
    fill(top, 1, 1, 2, 15, COPPER)
    fill(top, 14, 1, 15, 15, COPPER)
    save(top, "block/hangar_top.png")

    bottom = new()
    fill(bottom, 0, 0, 16, 16, STEEL_D)
    fill(bottom, 1, 1, 15, 15, PANEL)
    rivets(bottom, [(2, 2), (13, 2), (2, 13), (13, 13), (8, 8)])
    save(bottom, "block/hangar_bottom.png")

    trim = new()
    fill(trim, 0, 0, 16, 16, COPPER_D)
    fill(trim, 1, 1, 15, 15, COPPER)
    fill(trim, 3, 3, 13, 13, COPPER_L)
    save(trim, "block/hangar_trim.png")

    light = new()
    fill(light, 0, 0, 16, 16, AMBER)
    fill(light, 4, 4, 12, 12, AMBER_L)
    fill(light, 6, 6, 10, 10, WHITE)
    save(light, "block/hangar_light.png")


def make_controller() -> None:
    img = new()
    # body
    fill(img, 4, 1, 12, 15, STEEL_D)
    fill(img, 5, 2, 11, 14, STEEL)
    # antenna
    fill(img, 7, 0, 9, 2, STEEL_L)
    px(img, 7, 0, CYAN_L)
    # screen
    fill(img, 6, 3, 10, 7, INK)
    fill(img, 6, 3, 10, 4, CYAN)
    px(img, 7, 5, CYAN_L)
    px(img, 8, 5, CYAN)
    # dial
    fill(img, 6, 9, 10, 13, STEEL_D)
    fill(img, 7, 10, 9, 12, AMBER)
    px(img, 8, 11, AMBER_L)
    # side grips
    px(img, 4, 5, COPPER)
    px(img, 4, 8, COPPER)
    px(img, 4, 11, COPPER)
    px(img, 11, 5, COPPER)
    px(img, 11, 8, COPPER)
    px(img, 11, 11, COPPER)
    save(img, "item/controller.png")


def make_wright_item() -> None:
    img = new()
    # chassis
    fill(img, 4, 5, 12, 11, STEEL_D)
    fill(img, 5, 6, 11, 10, STEEL)
    fill(img, 6, 7, 10, 9, STEEL_L)
    # arms
    fill(img, 2, 5, 5, 7, PANEL)
    fill(img, 11, 5, 14, 7, PANEL)
    fill(img, 2, 9, 5, 11, PANEL)
    fill(img, 11, 9, 14, 11, PANEL)
    # rotors
    for cx, cy in ((2, 3), (12, 3), (2, 12), (12, 12)):
        fill(img, cx, cy, cx + 3, cy + 2, STEEL_D)
        fill(img, cx + 1, cy, cx + 2, cy + 2, COPPER)
        px(img, cx + 1, cy, COPPER_L)
    # sensor eye
    fill(img, 7, 7, 9, 9, AMBER)
    px(img, 7, 7, AMBER_L)
    px(img, 8, 8, WHITE)
    # antenna
    px(img, 7, 4, STEEL_L)
    px(img, 8, 4, CYAN_L)
    save(img, "item/wright.png")


def make_upgrade(name: str, accent, accent_l) -> None:
    img = new()
    fill(img, 3, 2, 13, 14, STEEL_D)
    fill(img, 4, 3, 12, 13, STEEL)
    # chip plate
    fill(img, 5, 4, 11, 10, accent)
    fill(img, 6, 5, 10, 9, accent_l)
    # pins
    for x in range(5, 11):
        px(img, x, 11, STEEL_L)
        px(img, x, 12, INK)
    # arrow / boost mark
    px(img, 7, 6, WHITE)
    px(img, 8, 6, WHITE)
    px(img, 7, 7, WHITE)
    px(img, 6, 8, WHITE)
    px(img, 9, 8, WHITE)
    rivets(img, [(4, 3), (11, 3), (4, 12), (11, 12)])
    save(img, f"item/wright_upgrade_{name}.png")


def make_blueprint(filled: bool) -> None:
    img = new()
    # sheet
    fill(img, 2, 1, 14, 15, CYAN_D)
    fill(img, 3, 2, 13, 14, (32, 72, 104, 255))
    # grid
    for x in range(4, 13, 2):
        for y in range(3, 14):
            px(img, x, y, CYAN_D)
    for y in range(4, 14, 2):
        for x in range(3, 13):
            r, g, b, a = img.getpixel((x, y))
            px(img, x, y, (min(255, r + 20), min(255, g + 30), min(255, b + 40), a))
    # border
    rect(img, 2, 1, 14, 15, STEEL_L)
    # corner fold
    px(img, 12, 2, WHITE)
    px(img, 11, 2, STEEL_L)
    px(img, 12, 3, STEEL_L)
    if filled:
        # building silhouette
        fill(img, 5, 7, 8, 12, CYAN_L)
        fill(img, 8, 9, 11, 12, CYAN)
        px(img, 6, 6, WHITE)
        px(img, 9, 8, WHITE)
    else:
        # empty dashed plan
        for x in range(5, 11):
            if x % 2 == 0:
                px(img, x, 6, CYAN)
                px(img, x, 11, CYAN)
        for y in range(7, 11):
            if y % 2 == 0:
                px(img, 5, y, CYAN)
                px(img, 10, y, CYAN)
    save(img, f"item/blueprint{'_filled' if filled else ''}.png")


def make_folder(filled: bool) -> None:
    img = new()
    fill(img, 1, 4, 15, 14, COPPER_D)
    fill(img, 2, 5, 14, 13, COPPER)
    # tab
    fill(img, 2, 2, 8, 5, COPPER_L)
    fill(img, 3, 3, 7, 5, COPPER)
    # papers
    fill(img, 3, 6, 13, 12, CYAN_D)
    fill(img, 4, 7, 12, 11, (40, 80, 112, 255))
    if filled:
        for y in (8, 9, 10):
            fill(img, 5, y, 11, y + 1, CYAN if y != 9 else CYAN_L)
        px(img, 7, 8, WHITE)
    else:
        rect(img, 5, 8, 11, 11, CYAN)
    # clasp
    fill(img, 7, 12, 9, 14, STEEL_L)
    save(img, f"item/blueprint_folder{'_filled' if filled else ''}.png")


def make_wrightport_item() -> None:
    img = new()
    # isometric-ish pad
    fill(img, 2, 10, 14, 14, STEEL_D)
    fill(img, 3, 9, 13, 13, STEEL)
    fill(img, 4, 8, 12, 12, PANEL)
    # posts
    for x, z in ((4, 5), (10, 5), (4, 9), (10, 9)):
        fill(img, x, z, x + 2, z + 4, STEEL_L)
        px(img, x, z, AMBER_L)
        px(img, x + 1, z, AMBER)
    # core
    fill(img, 6, 6, 10, 10, CYAN_D)
    fill(img, 7, 7, 9, 9, CYAN_L)
    save(img, "item/wrightport.png")


def make_personal_wrightport() -> None:
    img = new()
    # wearable module
    fill(img, 3, 2, 13, 14, STEEL_D)
    fill(img, 4, 3, 12, 13, STEEL)
    fill(img, 5, 4, 11, 11, INK)
    fill(img, 6, 5, 10, 10, CYAN_D)
    fill(img, 7, 6, 9, 9, CYAN)
    px(img, 7, 7, CYAN_L)
    # straps / clips
    fill(img, 2, 5, 4, 7, COPPER)
    fill(img, 12, 5, 14, 7, COPPER)
    fill(img, 2, 10, 4, 12, COPPER)
    fill(img, 12, 10, 14, 12, COPPER)
    rivets(img, [(4, 3), (11, 3), (4, 12), (11, 12)])
    save(img, "item/personal_wrightport.png")


def paint_box(img: Image.Image, u: int, v: int, w: int, h: int, d: int, faces: dict) -> None:
    """Paint Minecraft entity box UV layout at texOffs(u,v)."""
    # Layout:
    #     +z      +x
    #  +x top  +z  -x  -z? standard:
    #     [  ][up ][  ][  ]
    #     [west][north][east][south]
    #     [  ][down]
    # For addBox, width=w height=h depth=d
    # top: w x d at (u+d, v)
    # bottom: w x d at (u+d+w, v)
    # west: d x h at (u, v+d)
    # north: w x h at (u+d, v+d)
    # east: d x h at (u+d+w, v+d)
    # south: w x h at (u+d+w+d, v+d)
    mapping = {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }
    for face, color in faces.items():
        x0, y0, fw, fh = mapping[face]
        c = color if isinstance(color, tuple) else faces.get("_", STEEL)
        if isinstance(color, tuple):
            fill(img, x0, y0, x0 + fw, y0 + fh, color)
        # subtle edge
        if fw > 1 and fh > 1:
            rect(img, x0, y0, x0 + fw, y0 + fh, STEEL_D)


def make_hangar_door() -> None:
    img = new(32, 32)
    fill(img, 0, 0, 32, 32, STEEL_D)
    fill(img, 1, 1, 31, 31, STEEL)
    for y in range(2, 30, 3):
        fill(img, 2, y, 30, y + 1, STEEL_L if y % 6 == 0 else PANEL)
    # circular hatch hint
    for x, y in ((8, 8), (23, 8), (8, 23), (23, 23), (16, 6), (16, 25), (6, 16), (25, 16)):
        px(img, x, y, COPPER)
    rivets(img, [(3, 3), (28, 3), (3, 28), (28, 28), (16, 16)])
    # antenna / lamp pixels
    fill(img, 0, 20, 4, 28, INK)
    fill(img, 4, 20, 8, 24, AMBER_L)
    fill(img, 5, 21, 7, 23, WHITE)
    save(img, "block/hangar_door.png")


def make_drone() -> None:
    img = new(64, 64)
    # body 8x3x6 at (0,0)
    body = {
        "up": STEEL_L,
        "down": STEEL_D,
        "north": STEEL,
        "south": STEEL,
        "west": PANEL,
        "east": PANEL,
    }
    paint_box(img, 0, 0, 8, 3, 6, body)
    # panel lines on north
    for x in range(7, 14):
        px(img, x, 7, STEEL_D)
    # copper strip on north
    fill(img, 8, 8, 13, 9, COPPER)
    # lens 2x2x1 at (32,0)
    lens = {
        "up": AMBER,
        "down": AMBER,
        "north": AMBER_L,
        "south": COPPER_D,
        "west": AMBER,
        "east": AMBER,
    }
    paint_box(img, 32, 0, 2, 2, 1, lens)
    px(img, 34, 1, WHITE)
    # skids 1x1x4 at (0,10)
    skid = {
        "up": STEEL_L,
        "down": STEEL_D,
        "north": STEEL,
        "south": STEEL,
        "west": STEEL,
        "east": STEEL,
    }
    paint_box(img, 0, 10, 1, 1, 4, skid)
    # antenna 1x2x1 at (40,0)
    ant = {
        "up": CYAN_L,
        "down": STEEL,
        "north": STEEL_L,
        "south": STEEL_L,
        "west": STEEL_L,
        "east": STEEL_L,
    }
    paint_box(img, 40, 0, 1, 2, 1, ant)
    # rotors 4x1x4 at (0,16)
    rotor = {
        "up": STEEL_D,
        "down": STEEL_D,
        "north": STEEL,
        "south": STEEL,
        "west": STEEL,
        "east": STEEL,
    }
    paint_box(img, 0, 16, 4, 1, 4, rotor)
    # rotor blades cross on up face (u+d=4, v=16)
    fill(img, 4, 16, 8, 20, STEEL_D)
    fill(img, 5, 17, 7, 19, COPPER)
    px(img, 6, 18, COPPER_L)
    for x in range(4, 8):
        px(img, x, 17, STEEL_L)
        px(img, x, 18, STEEL_L)
    for y in range(16, 20):
        px(img, 5, y, STEEL_L)
        px(img, 6, y, STEEL_L)
    # hub
    px(img, 5, 17, AMBER)
    px(img, 6, 17, AMBER_L)
    px(img, 5, 18, AMBER_L)
    px(img, 6, 18, AMBER)
    # core plate 4x1x4 at (28,8)
    core = {
        "up": CYAN,
        "down": CYAN_D,
        "north": CYAN_D,
        "south": CYAN_D,
        "west": CYAN_D,
        "east": CYAN_D,
    }
    paint_box(img, 28, 8, 4, 1, 4, core)
    fill(img, 33, 8, 35, 10, CYAN_L)
    px(img, 34, 9, WHITE)
    # arms 3x1x1 at (16,10) — use integer 1 height/depth for UV
    arm = {
        "up": STEEL_L,
        "down": STEEL_D,
        "north": COPPER,
        "south": COPPER,
        "west": STEEL,
        "east": STEEL,
    }
    paint_box(img, 16, 10, 3, 1, 1, arm)
    # lens ring 3x3x1 at (48,0)
    ring = {
        "up": COPPER,
        "down": COPPER_D,
        "north": COPPER_L,
        "south": COPPER_D,
        "west": COPPER,
        "east": COPPER,
    }
    paint_box(img, 48, 0, 3, 3, 1, ring)
    px(img, 50, 1, AMBER_L)
    save(img, "entity/drone.png")


def main() -> None:
    make_wrightport_base()
    make_wrightport_post()
    make_wrightport_core()
    make_wrightport_light()
    make_wrightport_copper()
    make_hangar_textures()
    make_hangar_door()
    make_controller()
    make_wright_item()
    make_upgrade("iron", IRON, WHITE)
    make_upgrade("gold", GOLD, AMBER_L)
    make_upgrade("diamond", DIAMOND, CYAN_L)
    make_upgrade("netherite", NETHER, NETHER_L)
    make_blueprint(False)
    make_blueprint(True)
    make_folder(False)
    make_folder(True)
    make_wrightport_item()
    make_personal_wrightport()
    make_drone()
    print("done")


if __name__ == "__main__":
    main()
