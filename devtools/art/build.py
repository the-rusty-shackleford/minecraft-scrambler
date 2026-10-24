"""The Scrambler's game files, from the model as received (devtools/art/reference/dirtbike.bbmodel).

Writes the mesh (the received project, its red bodywork made dyeable), the profile, the lang file,
the chassis recipe and its unlock, and sounds.json, through the shared bike writer
(minecraft mods/tools/bbgen/bike.py). Everything it writes is committed; this script is the source of
truth for those files. Every number below is read off the model (devtools/art/reference/SOURCES.md
says where): the wheels' axles, the steering head's line, the seat's top.

Run: uv run --no-project --with pillow python devtools/art/build.py
     uv run --no-project --with pillow --with numpy python devtools/art/build.py sounds   (the engine loop; needs ffmpeg)

Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / "tools/bbgen"))
import bike  # noqa: E402  (the shared writer: minecraft mods/tools/bbgen)

# The steering head: the head tube is a cube along +Z turned -116 degrees about X, so the steering
# axis runs along that cube, raked 26 degrees back from upright, through the steering group's pivot.
HEAD = math.radians(-116.0)
STEER_AXIS = [0.0, round(-math.sin(HEAD), 5), round(math.cos(HEAD), 5)]

# The red parts: the bodywork bar the seat and filler cap, the number plate, the front fender.
PAINTED = frozenset({
    "tank", "tank_crown", "shroud_l", "shroud_r", "shroud_wing_l", "shroud_wing_r",
    "side_panel_l", "side_panel_r", "side_panel_low_l", "side_panel_low_r",
    "rear_fender_inner", "tail", "tail_ridge",
    "number_plate", "number_plate_edge",
    "front_fender", "front_fender_ridge", "front_fender_tip", "fender_lip_l", "fender_lip_r",
})

PROFILE = {
    "scale": 0.0625,
    "handedness": "right",
    # 15.8 units across the bars, 39.6 nose to tail, 22.9 to the bar ends: a block, two and a half, and under a block and a half.
    "body": {"width": 1.0, "length": 2.5, "height": 1.45,
             "parts": [{"at": [0, 0, 14], "width": 0.8, "height": 1.2}, {"at": [0, 0, -14], "width": 0.8, "height": 1.2}]},
    # On the seat's front half, its top 19.6 up, the attachment 0.4 over it as every truck's is.
    "seats": [{"at": [0, 20.0, -4.0], "driver": True}],
    # Drawn by the model's own wheel groups (below): these are only where it touches the ground.
    "wheels": {"radius": 6.2, "drawn": False, "positions": [
        {"forward": 13.4, "right": 0, "up": 6.2, "steers": True},
        {"forward": -12.8, "right": 0, "up": 5.85}]},
    # Light and quick: quicker off the mark and tighter than a truck, a little slower flat out.
    "engine": {"max_speed": 0.75, "acceleration": 0.03, "reverse_speed": 0.2, "brake": 0.07, "drag": 0.01},
    "handling": {"grip": 0.8, "steer_degrees": 34, "drift_grip": 0.2, "drift_boost": 0.2, "drift_charge_ticks": 40},
    "climb": 1.0,
    "mass": 0.35,
    "durability": 3.0,
    "fuel": {"capacity": 12000},
    "paint": {"part": {"group": sorted(PAINTED)}, "default": "red"},
    "sounds": {"engine": "scrambler:engine", "pitch": [0.9, 2.0], "volume": [0.3, 0.8]},
    "repair": {"ingredient": {"tag": "c:ingots/steel"}, "full_cost": 6},
    # An average horse's jump (D-0001): a full bar three blocks up.
    "jump": {"strength": 0.7},
    # Upright parked: its stand is modelled stowed along the swingarm and too short to reach the ground.
    "lean": {"degrees": 40, "parked": 0},
    "moving": [
        {"part": {"group": "steering"}, "pivot": [0, 19.15587, 5.74588], "axis": STEER_AXIS, "by": "steer"},
        {"part": {"group": "wheel_front"}, "pivot": [0, 6.2, 13.4], "axis": [1, 0, 0], "by": "roll", "radius": 6.2, "within": 0},
        {"part": {"group": "wheel_rear"}, "pivot": [0, 5.85, -12.8], "axis": [1, 0, 0], "by": "roll", "radius": 5.85},
    ],
}

SCRAMBLER = bike.Bike(
    ns="scrambler",
    name="scrambler",
    display="Scrambler",
    reference="devtools/art/reference/dirtbike.bbmodel",
    painted=PAINTED,
    profile=PROFILE,
    # Six steel ingots in a frame's shape.
    recipe={"pattern": ["  S", "SSS", "S S"], "key": {"S": {"tag": "c:ingots/steel"}}},
    unlock="#c:ingots/steel",
    distance=32,
)

if __name__ == "__main__":
    if sys.argv[1:] == ["sounds"]:
        # The idle of a motocross bike, 75 firings a second at 155 s: 225 of them, the beat running on through the join.
        bike.engine_loop(ROOT, "scrambler", "devtools/art/sounds/src/450034-motocross-start-idle-rev.ogg", 155.0, 3.0, 0.2)
    else:
        print("factory", bike.build(ROOT, SCRAMBLER))
