"""Draws the World Boss dome's glass panels: an animated, translucent triangle with a purple and green ghost swirl, a bright rim so the
facets read as glass, and a pale ghost face that fades in and out. Pure Python with no libraries, like tools/pixel_art.py, so a clean
checkout reproduces the same pixels.

Each frame is square (SIZE x SIZE) and the frames are stacked top to bottom, which is the layout Minecraft animates with a .mcmeta file.
The triangle points up: its base is the bottom edge and its apex the top middle, which is how the server places each panel.

Variants 0 to 2 differ in swirl phase, face place and face timing so a wall of panels does not pulse in step; variant 3 is the bright
flash a panel shows for a moment when a player crosses the wall.
"""
import math

SIZE = 64
FRAMES = 24
VARIANTS = 4
FLASH = 3

PURPLE = (154, 92, 198)
GREEN = (110, 230, 160)
RIM = (232, 204, 255)
FACE = (214, 255, 234)

# (centre x, centre y, phase of the fade as a fraction of the loop) for the faces of variants 0 to 2
FACES = [(32.0, 38.0, 0.00), (29.5, 41.0, 0.33), (34.5, 36.0, 0.66)]


def _mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def _inside_and_rim(x, y):
    """Distance in pixels from the nearest edge of the up-pointing triangle, or None outside it."""
    top, bottom = 1.0, SIZE - 2.0
    if y < top or y > bottom:
        return None
    half = (y - top) / (bottom - top) * (SIZE / 2 - 1.5)
    centre = SIZE / 2 - 0.5
    side = half - abs(x - centre)            # horizontal distance to the nearest slanted edge
    if side < 0:
        return None
    slope = math.hypot(1.0, (SIZE / 2 - 1.5) / (bottom - top))
    side_distance = side / slope             # turn that into the distance perpendicular to the edge
    return min(side_distance, bottom - y, (y - top) * 0.45 + side_distance)


def _ellipse(x, y, cx, cy, rx, ry):
    """1 at the centre falling to 0 at the rim of an ellipse."""
    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
    return max(0.0, 1.0 - d)


def pixel(variant, frame, x, y):
    edge = _inside_and_rim(x, y)
    if edge is None:
        return (0, 0, 0, 0)
    t = 2.0 * math.pi * frame / FRAMES
    shift = 2.0 * math.pi * (variant % 3) / 3.0
    s1 = math.sin(x * 0.20 + 1.5 * math.sin(y * 0.13 + t + shift) + t)
    s2 = math.sin(y * 0.17 - x * 0.09 + 1.2 * math.cos(x * 0.11 - t) - t + shift)
    swirl = ((s1 + s2) / 2.0 + 1.0) / 2.0
    colour = _mix(PURPLE, GREEN, swirl ** 2.2)
    alpha = 34.0 + 62.0 * swirl
    rim = math.exp(-edge / 2.4)
    colour = _mix(colour, RIM, min(1.0, rim * 1.1))
    alpha += 190.0 * rim

    if variant == FLASH:
        pulse = 0.5 + 0.5 * math.sin(t * 2.0)
        colour = _mix(colour, (255, 245, 255), 0.55 + 0.25 * pulse)
        alpha = alpha * 1.6 + 70.0 + 40.0 * pulse
    else:
        face_x, face_y, face_phase = FACES[variant]
        fade = max(0.0, math.sin(2.0 * math.pi * (frame / FRAMES + face_phase))) ** 2
        bob = 1.2 * math.sin(t + shift)
        head = _ellipse(x, y, face_x, face_y + bob, 12.0, 15.0)
        hollow = max(_ellipse(x, y, face_x - 4.6, face_y - 4.0 + bob, 3.4, 4.4),   # left eye
                     _ellipse(x, y, face_x + 4.6, face_y - 4.0 + bob, 3.4, 4.4),   # right eye
                     _ellipse(x, y, face_x, face_y + 7.0 + bob, 3.2, 6.0))         # mouth
        glow = head * (1.0 - min(1.0, hollow * 2.5)) * fade
        colour = _mix(colour, FACE, min(1.0, glow * 1.4))
        alpha += 100.0 * glow
    return (int(colour[0]), int(colour[1]), int(colour[2]), int(max(0.0, min(255.0, alpha))))


def strip(variant):
    """The whole animation of one variant as an RGBA grid, FRAMES frames stacked top to bottom."""
    rows = []
    for frame in range(FRAMES):
        for y in range(SIZE):
            rows.append([pixel(variant, frame, x, y) for x in range(SIZE)])
    return rows


def mcmeta():
    return '{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n'


def model(texture):
    """A flat two-sided quad with no parent, so no display transform is applied: the server places and sizes each panel."""
    face = {'uv': [0, 0, 16, 16], 'texture': '#panel'}
    import json
    return json.dumps({
        'textures': {'panel': texture, 'particle': texture},
        'elements': [{'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False,
                      'faces': {'north': face, 'south': face}}],
    }, indent=2) + '\n'
