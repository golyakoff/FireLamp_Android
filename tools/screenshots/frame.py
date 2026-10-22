#!/usr/bin/env python3
"""
Puts phone screenshots into a drawn phone frame for the README.

    python3 tools/screenshots/frame.py SCREENSHOTS_DIR [OUT_DIR]

Every PNG/JPG in SCREENSHOTS_DIR becomes OUT_DIR/<name>.png (default docs/screenshots/): a dark
phone body with thin bezels, rounded screen corners, a punch-hole camera and side buttons, on a
transparent background, 600 px wide. Name the screenshots like the README expects: home, lamp, effect,
schedule, settings, system. Needs Pillow (pip install pillow).
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFilter

WIDTH = 600          # output width, px
BEZEL = 0.035        # of the screen width
BODY = (24, 24, 27)
EDGE = (70, 70, 76)


def rounded_mask(size, radius):
    mask = Image.new('L', size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def frame(path, out):
    shot = Image.open(path).convert('RGB')
    sw, sh = shot.size
    bezel = round(sw * BEZEL)
    radius = round(sw * 0.075)
    side = round(sw * 0.012)                       # room for the side buttons
    pad = round(sw * 0.04)                         # room for the shadow
    bw, bh = sw + 2 * bezel, sh + 2 * bezel
    canvas = Image.new('RGBA', (bw + 2 * (pad + side), bh + 2 * pad), (0, 0, 0, 0))
    ox, oy = pad + side, pad

    # soft shadow
    shadow = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((ox, oy + pad // 3, ox + bw, oy + bh + pad // 3), radius + bezel,
                                             fill=(0, 0, 0, 90))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(pad / 2.5)))

    d = ImageDraw.Draw(canvas)
    # side buttons: power on the right, volume on the left
    bt = round(sw * 0.014)
    d.rounded_rectangle((ox + bw - 2, oy + bh * 0.22, ox + bw + side, oy + bh * 0.30), bt, fill=EDGE)
    d.rounded_rectangle((ox - side, oy + bh * 0.16, ox + 2, oy + bh * 0.22), bt, fill=EDGE)
    d.rounded_rectangle((ox - side, oy + bh * 0.24, ox + 2, oy + bh * 0.33), bt, fill=EDGE)
    # body with a thin lighter edge
    d.rounded_rectangle((ox, oy, ox + bw, oy + bh), radius + bezel, fill=EDGE)
    d.rounded_rectangle((ox + 3, oy + 3, ox + bw - 3, oy + bh - 3), radius + bezel - 3, fill=BODY)

    # the screenshot with rounded corners
    canvas.paste(shot, (ox + bezel, oy + bezel), rounded_mask(shot.size, radius))

    # punch-hole camera over the status bar
    cr = round(sw * 0.018)
    cx, cy = ox + bezel + sw // 2, oy + bezel + round(sw * 0.035)
    d.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(8, 8, 10))
    d.ellipse((cx - cr // 3, cy - cr // 3, cx, cy), fill=(40, 44, 60))

    scale = WIDTH / canvas.width
    canvas = canvas.resize((WIDTH, round(canvas.height * scale)), Image.LANCZOS)
    canvas.save(out, optimize=True)
    print('wrote', out, canvas.size)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = sys.argv[1]
    root = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(root, 'docs', 'screenshots')
    os.makedirs(dst, exist_ok=True)
    for f in sorted(os.listdir(src)):
        name, ext = os.path.splitext(f)
        if ext.lower() in ('.png', '.jpg', '.jpeg'):
            frame(os.path.join(src, f), os.path.join(dst, name + '.png'))


if __name__ == '__main__':
    main()
