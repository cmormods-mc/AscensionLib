"""Draws the World Boss bar: a haunted purple-and-green health bar that replaces vanilla's PINK boss bar sprites and the 20-notch overlay.

Pure Python with no libraries, like tools/pixel_art.py, so a clean checkout reproduces the same pixels. Minecraft 1.21.1 draws a boss bar from
GUI sprites, each 182 x 5: a background (the empty part), a progress (the filled part, clipped to the health), and for a notched overlay one
more pair drawn over both. CobbleRaids shows the World Boss with colour PINK and overlay NOTCHED_20; nothing else on the server uses PINK,
so reskinning it affects no other bar. (The notched overlays are shared by every colour, but only NOTCHED_20 is replaced, and only ticks are drawn.)
"""
import math

WIDTH = 182
HEIGHT = 5

GREEN = (123, 224, 168)
SKULL = [".###.",
         "#o#o#",
         ".###.",
         ".#.#.",
         "....."]

# top to bottom: a bright rim, then the body deepening to a dark base
FILL = [(236, 208, 255), (176, 112, 222), (128, 68, 184), (92, 46, 140), (56, 28, 96)]
EMPTY = [(8, 5, 16, 245), (40, 27, 64, 238), (22, 14, 38, 238), (18, 12, 30, 238), (8, 5, 16, 245)]


def _blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _skull(grid, light, dark, x0=2, y0=0):
    for y, row in enumerate(SKULL):
        for x, ch in enumerate(row):
            if ch == '#':
                grid[y0 + y][x0 + x] = light
            elif ch == 'o':
                grid[y0 + y][x0 + x] = dark


def background():
    grid = [[EMPTY[y] for _ in range(WIDTH)] for y in range(HEIGHT)]
    for y in range(1, 4):                      # faint drifting specks in the empty part
        for x in range(WIDTH):
            if (x * 5 + y * 11) % 37 == 0:
                r, g, b, a = grid[y][x]
                grid[y][x] = (min(255, r + 26), min(255, g + 22), min(255, b + 40), a)
    _skull(grid, (70, 54, 104, 255), (8, 5, 16, 255))
    return grid


def progress():
    grid = [[FILL[y] + (255,) for _ in range(WIDTH)] for y in range(HEIGHT)]
    for start in range(10, WIDTH, 23):         # green wisps drifting up and to the right
        for j in range(7):
            x, y = start + j, 3 - j // 2
            if 0 <= x < WIDTH and 1 <= y <= 3:
                r, g, b, a = grid[y][x]
                grid[y][x] = _blend((r, g, b), GREEN, 0.62) + (255,)
    for y in range(HEIGHT):                    # a soft shade into the right end so a nearly empty bar still has a rounded tip
        r, g, b, a = grid[y][WIDTH - 1]
        grid[y][WIDTH - 1] = (r // 2, g // 2, b // 2, 255)
    _skull(grid, (232, 224, 244, 255), (40, 18, 70, 255))
    return grid


def notches():
    """Twenty segments: a dark tick at every 5%, full height, everything else clear."""
    grid = [[(0, 0, 0, 0) for _ in range(WIDTH)] for _ in range(HEIGHT)]
    for k in range(1, 20):
        x = round(WIDTH * k / 20) - 1
        for y in range(HEIGHT):
            grid[y][x] = (6, 3, 12, 215)
    return grid


# file name (under assets/minecraft/textures/gui/sprites/boss_bar/) -> pixel grid
def sprites():
    return {
        'pink_background.png': background(),
        'pink_progress.png': progress(),
        'notched_20_background.png': notches(),
        'notched_20_progress.png': notches(),
    }


def mockup(png_bytes, fraction=0.7, scale=5):
    """The bar at `fraction` full, notches on top, enlarged and on a dark backdrop: for looking at, never shipped."""
    bg, fg, notch = background(), progress(), notches()
    filled = int(WIDTH * fraction)
    rows = []
    for y in range(HEIGHT * scale):
        row = []
        for x in range(WIDTH * scale):
            sx, sy = x // scale, y // scale
            layer = fg[sy][sx] if sx < filled else bg[sy][sx]
            pixel = (18, 14, 28)
            for r, g, b, a in (layer, notch[sy][sx]):
                al = a / 255.0
                pixel = (int(r * al + pixel[0] * (1 - al)), int(g * al + pixel[1] * (1 - al)), int(b * al + pixel[2] * (1 - al)))
            row.append(pixel + (255,))
        rows.append(row)
    return png_bytes(rows)
