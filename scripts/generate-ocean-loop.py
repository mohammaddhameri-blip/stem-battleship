"""Bake the light ocean ripples into a seamless three-second, 10 fps GIF.
Run with Python and Pillow; no wave calculations are needed in the game loop.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import math

WIDTH, HEIGHT, COUNT = 600, 320, 30
frames = []
# Transparent background lets the browser composite the static ocean gradient.
palette = [0, 0, 0, 49, 117, 139, 75, 142, 161] + [0, 0, 0] * 253
for frame in range(COUNT):
    phase = frame / COUNT * math.tau
    image = Image.new('P', (WIDTH, HEIGHT), 0)
    image.putpalette(palette)
    draw = ImageDraw.Draw(image)
    for i in range(115):
        x = (i * 96.5) % WIDTH + math.sin(phase + i) * 4
        y = (i * 36.5) % HEIGHT + math.sin(phase + i * 2.1) * 3
        length = (9 + i % 7 * 6) / 2
        points = []
        for step in range(9):
            t = step / 8
            points.append((x + length * t, y - (4 + i % 3) * t * (1-t) + math.sin(phase+i)*t*t))
        draw.line(points, fill=2 if i % 4 == 0 else 1, width=1)
    frames.append(image)
output = Path(__file__).resolve().parents[1] / 'ocean-loop.gif'
frames[0].save(output, save_all=True, append_images=frames[1:], duration=100,
               loop=0, transparency=0, disposal=2, optimize=False)
print(f'{output.name}: {output.stat().st_size:,} bytes')
