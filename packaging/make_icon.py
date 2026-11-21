#!/usr/bin/env python3
"""Generates the Rat-Man app icon from the in-game cheese sprite: glowing cheese on a blue tile.

Writes packaging/ratman.png/.ico/.icns (the Mac and Windows apps, and the web page) and
src/main/resources/images/app_icon.png (the game window / Dock icon). Needs Pillow.
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
S = 1024
M = 72            # margin around the tile
R = 210           # corner radius

TOP, BOTTOM = (70, 150, 255), (22, 58, 170)   # blue gradient, light at the top
GLOW = (255, 214, 70)

# --- blue tile with a vertical gradient
grad = Image.new('RGBA', (S, S))
gd = ImageDraw.Draw(grad)
for y in range(S):
    t = y / (S - 1)
    gd.line([(0, y), (S, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)) + (255,))
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([M, M, S - M, S - M], radius=R, fill=255)
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
img.paste(grad, (0, 0), mask)

# soft light in the upper half, and a darker rim, for some depth
shine = Image.new('L', (S, S), 0)
ImageDraw.Draw(shine).ellipse([M - 200, M - 520, S - M + 200, S // 2 + 40], fill=70)
shine = ImageChops.multiply(shine.filter(ImageFilter.GaussianBlur(60)), mask)
img = Image.composite(Image.new('RGBA', (S, S), (255, 255, 255, 255)), img, shine)
rim = Image.new('L', (S, S), 0)
ImageDraw.Draw(rim).rounded_rectangle([M, M, S - M, S - M], radius=R, outline=255, width=22)
rim = ImageChops.multiply(rim.filter(ImageFilter.GaussianBlur(6)), mask)
img = Image.composite(Image.new('RGBA', (S, S), (10, 25, 80, 255)), img, rim.point(lambda v: v * 0.55))

# --- glow behind the cheese (like the big cheese in the game)
glow = Image.new('RGBA', (S, S), GLOW + (0,))
ga = Image.new('L', (S, S), 0)
ImageDraw.Draw(ga).ellipse([S // 2 - 300, S // 2 - 270, S // 2 + 300, S // 2 + 330], fill=200)
glow.putalpha(ImageChops.multiply(ga.filter(ImageFilter.GaussianBlur(90)), mask))
img.alpha_composite(glow)

# --- the cheese sprite, scaled up crisp, with a dark outline and a drop shadow
sprite = Image.open(os.path.join(ROOT, 'src/main/resources/images/pelletcheese.png')).convert('RGBA')
cheese = sprite.crop(sprite.getbbox())
scale = 600 // max(cheese.size)
cheese = cheese.resize((cheese.width * scale, cheese.height * scale), Image.NEAREST)
pad = 40
a = Image.new('L', (cheese.width + 2 * pad, cheese.height + 2 * pad), 0)
a.paste(cheese.split()[3], (pad, pad))
outline = Image.new('RGBA', a.size, (60, 35, 5, 255))
outline.putalpha(a.filter(ImageFilter.MaxFilter(27)))
shadow = Image.new('RGBA', a.size, (5, 15, 50, 255))
shadow.putalpha(a.filter(ImageFilter.MaxFilter(27)).filter(ImageFilter.GaussianBlur(18)).point(lambda v: v * 0.6))
art = Image.new('RGBA', a.size, (0, 0, 0, 0))
art.alpha_composite(outline)
art.alpha_composite(cheese, (pad, pad))
x, y = (S - art.width) // 2, (S - art.height) // 2 + 25
img.alpha_composite(shadow, (x + 6, y + 26))
img.alpha_composite(art, (x, y))

img.save(os.path.join(HERE, 'ratman.png'))
img.save(os.path.join(HERE, 'ratman.ico'), sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
img.save(os.path.join(HERE, 'ratman.icns'))
img.resize((256, 256), Image.LANCZOS).save(os.path.join(ROOT, 'src/main/resources/images/app_icon.png'))
print('wrote ratman.png/.ico/.icns and app_icon.png')
