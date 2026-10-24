# The model as received

`dirtbike.bbmodel`, received 2026-10-23, kept byte for byte. SHA-256 `74e25301abaade29c9b43b984aa3f5304590e886ab70269abc6cb80c4422569f`.
A Blockbench project (format 5, free model, 237 cubes, one 512x512 texture embedded). The game's mesh
is made from it by `../build.py`, which changes only the painted parts' texture (made grey, so a dye
colours them; the profile's factory colour keeps an undyed bike the red received). Copyright 2026
Chunkworks; AGPL-3.0-or-later.

Where the profile's numbers come from, in the model's units (a sixteenth of a block):

- **Wheels**: `wheel_front` turns about its group pivot (0, 6.2, 13.4), radius 6.2; `wheel_rear`
  (inside `swingarm`) about (0, 5.85, -12.8), radius 5.85. Both tyres touch y 0.
- **Steering**: the `steering` group (`front_end`, `front_fender`, `wheel_front`) pivots at
  (0, 19.15587, 5.74588), on the line of `frame/head_tube`, a cube along +Z turned -116 degrees about
  X: the axis is (0, sin 116, cos 116), raked 26 degrees.
- **Seat**: `bodywork/seat` spans z -14.2 to 1.6, its top at 19.6; the rider sits at z -4.
- **Red**: every cube of `bodywork` but `seat`, `seat_base` and `filler_cap`; `number_plate`,
  `number_plate_edge`; all of `front_fender`. No texture pixel they use is used by anything else
  (`tools/bbgen/paint.py` refuses one that is).
- **Kickstand**: `frame/kickstand` is modelled stowed along the swingarm, 4.5 long from a mount 7.3
  up: it cannot reach the ground, so the bike stands upright parked and the stand stays as drawn.
