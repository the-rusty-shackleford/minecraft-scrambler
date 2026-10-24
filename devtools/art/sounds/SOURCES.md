# Sounds

Every shipped sound is a cut of a recording kept here, under CC0 (creativecommons.org/publicdomain/zero/1.0),
the licence read on the recording's own page before use.

| File | Source | Licence | Use |
|---|---|---|---|
| `src/450034-motocross-start-idle-rev.ogg` | freesound.org sound 450034, "motorcycle dirt bike motocross start idle, rev, stop, kick start, false start, drive off pull away pull up stop.wav", 192 kbps preview; SHA-256 `7505f9c85ea9ac04136143390e8864aa4e006eba4c1b5dcc04ec488ed9a00cfa` | CC0 1.0 | `assets/scrambler/sounds/engine.ogg` |

**The engine loop**: the recording idles steadily from 149 to 183 s, firing 75 times a second, its 100 ms
loudness within 2 dB. `build.py sounds` cuts 3.0 s from 155.0 s (225 firings, so the beat runs on
through the join) plus 0.2 s blended into the start (`tools/sound/cutlib.py`). The profile pitches it
from 0.9 at idle to 2.0 flat out; `sounds.json` hears it 32 blocks off.
