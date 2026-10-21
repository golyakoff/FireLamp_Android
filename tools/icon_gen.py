"""
FireLamp launcher icon: a cylinder lamp whose LED matrix shows a flame.
One geometry -> Android adaptive icon vectors (foreground, background, monochrome) + PNG preview.
Usage: python3 tools/icon_gen.py app/src/main/res <preview png>   (needs Pillow for the preview)
"""
import math
import sys

from PIL import Image, ImageDraw

VP = 108.0  # adaptive icon viewport, dp

# ---------- geometry ----------
CX = 54.0
RX = 16.5          # cylinder half width
TOP, BOTTOM = 33.0, 73.0
CAP_RY = 4.5       # perspective of the round ends
BASE_RX, BASE_H = 18.5, 5.0

BG = "#1A1D26"
GLOW = "#7A3412"
BODY = "#2C313C"
BODY_EDGE = "#3B4250"
CAP = "#555D6E"
CAP_INNER = "#3A404D"
BASE = "#20232B"
LED_OFF = "#3A404D"

ROWS = 10
COL_ANGLES = [-72, -50, -29, -9, 11, 31, 52, 73]
FLAME = [2, 4, 6, 9, 8, 6, 3, 2]  # lit LEDs per column, from the bottom: a tongue leaning left
SPARKS = {(4, 9)}                 # (column, row): a spark torn off the tip
LED_R = 1.45


def flame_color(heat):  # 1 = the hot core at the bottom middle, 0 = the edge of the flame
    if heat > 0.78:
        return "#FFF3B0"
    if heat > 0.55:
        return "#FFD54A"
    if heat > 0.35:
        return "#FFA000"
    if heat > 0.18:
        return "#FF5722"
    return "#D32F2F"


def arc(cx, cy, rx, ry, a0, a1, n=24):
    """Points of an ellipse arc, angles in degrees (0 = right, 90 = down/front)."""
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def ellipse(cx, cy, rx, ry):
    return arc(cx, cy, rx, ry, 0, 360, 48)


shapes = []  # (points, color, alpha, layer) layer: "fg" / "mono"


def add(points, color, alpha=1.0, mono=None):
    shapes.append((points, color, alpha, mono))


# base (a wider, short cylinder under the lamp)
base_body = [(CX - BASE_RX, BOTTOM)] + list(reversed(arc(CX, BOTTOM + BASE_H, BASE_RX, CAP_RY, 0, 180))) + [(CX + BASE_RX, BOTTOM)]
add(base_body, BASE, mono=0.5)
add(ellipse(CX, BOTTOM, BASE_RX, CAP_RY), BASE, mono=0.5)

# glass body: side edges + front halves of the round ends
# front outline: top front arc (right -> left), down the left side, bottom front arc (left -> right), up
top_front = arc(CX, TOP, RX, CAP_RY, 0, 180)          # right -> left
bottom_front = arc(CX, BOTTOM, RX, CAP_RY, 180, 0)    # left -> right
body = top_front + bottom_front
add(body, BODY, mono=0.35)
# a soft highlight along the left edge of the glass
hl = arc(CX, TOP, RX, CAP_RY, 150, 172, 6) + arc(CX, BOTTOM, RX, CAP_RY, 172, 150, 6)
add(hl, BODY_EDGE)

# top cap
add(ellipse(CX, TOP, RX, CAP_RY), CAP, mono=0.6)
add(ellipse(CX, TOP, RX - 3.0, CAP_RY - 1.0), CAP_INNER, mono=0.0)

# LEDs on the front of the cylinder: rows follow the ellipse of the surface
leds = []
for c, a in enumerate(COL_ANGLES):
    t = math.radians(a)
    x = CX + (RX - 2.2) * math.sin(t)
    rx = LED_R * (0.35 + 0.65 * math.cos(t))
    for r in range(ROWS):  # r = 0 is the bottom row
        y0 = BOTTOM - 4.0 - r * (BOTTOM - TOP - 6.5) / (ROWS - 1)
        y = y0 + CAP_RY * math.cos(t)
        spark = (c, r) in SPARKS
        lit = r < FLAME[c] or spark
        # hotter low and in the middle columns
        heat = 0.0 if spark else (1 - r / FLAME[c]) * (FLAME[c] / max(FLAME)) if lit else 0
        color = flame_color(heat) if lit else LED_OFF
        if lit:
            add(ellipse(x, y, rx * 1.9, LED_R * 1.9), color, alpha=0.22)  # glow
        add(ellipse(x, y, rx, LED_R), color, mono=1.0 if lit else None)

# ---------- Android vector output ----------


def path_data(points):
    p = " ".join(f"{x:.2f},{y:.2f}" for x, y in points[1:])
    x0, y0 = points[0]
    return f"M{x0:.2f},{y0:.2f} L{p} Z"


def vector(paths_xml):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated: a cylinder lamp whose LED matrix shows a flame. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + paths_xml + '</vector>\n')


def path_xml(points, color, alpha=1.0):
    a = f'\n        android:fillAlpha="{alpha:.2f}"' if alpha < 1 else ""
    return f'    <path\n        android:fillColor="{color}"{a}\n        android:pathData="{path_data(points)}" />\n'


res = sys.argv[1]
fg = "".join(path_xml(p, c, a) for p, c, a, _ in shapes)
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector(fg))

mono = "".join(path_xml(p, "#FFFFFF", m) for p, _, _, m in shapes if m)
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(vector(mono))

bg = ('    <path\n        android:fillColor="' + BG + '"\n        android:pathData="M0,0h108v108h-108z" />\n'
      '    <path android:pathData="M0,0h108v108h-108z">\n'
      '        <aapt:attr name="android:fillColor">\n'
      '            <gradient\n                android:type="radial"\n'
      '                android:centerX="54"\n                android:centerY="58"\n'
      '                android:gradientRadius="42">\n'
      f'                <item android:offset="0" android:color="#FF{GLOW[1:]}" />\n'
      f'                <item android:offset="1" android:color="#00{BG[1:]}" />\n'
      '            </gradient>\n        </aapt:attr>\n    </path>\n')
open(f"{res}/drawable/ic_launcher_background.xml", "w").write(
    vector(bg).replace('<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                       '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                       '    xmlns:aapt="http://schemas.android.com/aapt"'))

# ---------- PNG preview ----------
S = 8
N = int(VP * S)


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def render(layer_shapes, background):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    if background:
        px = img.load()
        bg, glow = rgb(BG), rgb(GLOW)
        for yy in range(N):
            for xx in range(N):
                d = min(math.hypot(xx / S - 54, yy / S - 58) / 42, 1.0)
                k = 1 - d
                px[xx, yy] = tuple(int(bg[i] + (glow[i] - bg[i]) * k) for i in range(3)) + (255,)
    for points, color, alpha in layer_shapes:
        over = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        ImageDraw.Draw(over).polygon([(x * S, y * S) for x, y in points], fill=rgb(color) + (int(255 * alpha),))
        img = Image.alpha_composite(img, over)
    return img


def masked(img):
    # launchers show the middle 72 of 108 dp; a circular mask
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).ellipse([18 * S, 18 * S, 90 * S, 90 * S], fill=255)
    out = Image.new("RGBA", (N, N), (255, 255, 255, 255))
    out.paste(img, (0, 0), mask)
    return out.crop((14 * S, 14 * S, 94 * S, 94 * S)).resize((320, 320), Image.LANCZOS)


full = render([(p, c, a) for p, c, a, _ in shapes], background=True)
mono_img = render([(p, "#202124", m) for p, _, _, m in shapes if m], background=False)
mono_bg = Image.new("RGBA", (N, N), (0xD3, 0xE3, 0xFD, 255))
mono_full = Image.alpha_composite(mono_bg, mono_img)

sheet = Image.new("RGBA", (680, 340), (255, 255, 255, 255))
sheet.paste(masked(full), (10, 10))
sheet.paste(masked(mono_full), (350, 10))
sheet.save(sys.argv[2])
print("ok", len(shapes), "shapes")
