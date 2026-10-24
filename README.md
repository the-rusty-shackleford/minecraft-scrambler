# Scrambler

A red dirt bike for [Vanilla Wheels](https://github.com/the-rusty-shackleford/minecraft-vanilla-wheels)
on NeoForge 1.21.1. One seat; light and quick; it leans into its turns, and it **jumps as a ridden
horse does**: hold the jump key, the jump bar fills where the experience bar was, let go. A full bar
puts it three blocks up, over a two-block wall; a tap barely leaves the ground. There is no Java in
it: the bike is a vehicle profile and one Blockbench project, and the protocol does the rest (from
Vanilla Wheels 1.19.0, its D-0047), so driving, fuel, keys, packing and the Mechanic Lift are
documented there. By Chunkworks, AGPL-3.0-or-later.

## Getting one

- **Chassis**: six steel ingots in a frame's shape (`  S` / `SSS` / `S S`), steel from Metals and
  Materials. The chassis item names the Scrambler.
- **Build**: the chassis, **two wheels** and an engine in a Mechanic Lift, then Build. It fits either
  lift's deck. Paint it there with a dye; undyed, it is the red it was modelled in.
- **Fuel**: Vanilla Wheels' gas can, as any vehicle. A twelve-thousand-tick tank, half a truck's.

## Riding it

- **Movement keys** ride; it steers its fork, the front wheel turning in it, and leans into the turn
  as far as balancing it needs (at most 40 degrees). Parked it stands upright: its stand is modelled
  stowed along the swingarm.
- **Jump** (the game's jump key, Space): hold it and the jump bar fills; let go and it jumps with what
  the bar held, exactly as a horse does (an average horse's strength, 0.7: a full bar rises 3.12
  blocks, a tap 0.62). A jump asked for in the air is made as it lands. It never drifts: on a bike the
  key is the jump's.
- **Landing** hurts neither rider nor bike below Vanilla Wheels' seven blocks of suspension: a full
  jump off a three-block ledge lands them whole.
- No horn (Vanilla Wheels' only horn is a truck's), no lamps, no chest.

| | |
|---|---|
| Top speed | 0.75 blocks a tick (15 m/s); acceleration 0.03, brake 0.07 |
| Steering | 34 degrees of lock; grip 0.8 |
| Climbs | one-block steps; a two-block wall needs a jump |
| Size | 2½ blocks long, 1 wide, 1.45 high |
| Mass, durability | 0.35; durability 3 (a blow wears it a third of what it wears a boat) |
| Repair on the lift | 6 steel ingots for a wreck |
| Engine | a motocross bike's idle, pitched from 0.9 to 2.0 with speed, heard 32 blocks off |

## How it is made

`devtools/art/reference/dirtbike.bbmodel` is the model as received, byte for byte
(`devtools/art/reference/SOURCES.md`). `devtools/art/build.py` writes every game file from it through
the shared writer `minecraft mods/tools/bbgen/bike.py`:

- the mesh, `assets/scrambler/vanillawheels/mesh/scrambler.bbmodel`: the received project with the
  red bodywork's texture turned grey (`tools/bbgen/paint.py`), its lightness kept as a share of the
  parts' mean red, so the profile's `factory` colour (#ff3f40) tints it back to the red received and
  any dye colours the same shading;
- the profile, `data/scrambler/vanillawheels/vehicle/scrambler.json`: its wheels are where it touches
  the ground (`"drawn": false`), drawn by the model's own `wheel_front` and `wheel_rear` groups, which
  roll; the `steering` group (fork, bars, front fender, front wheel) turns about the head tube's line,
  raked 26 degrees; `jump`, `lean`;
- the chassis recipe and its unlock, the lang file, `sounds.json`.

`build.py sounds` cuts the engine loop from a CC0 recording (`devtools/art/sounds/SOURCES.md`) with
`minecraft mods/tools/sound/cutlib.py`. Run both and commit what they write.

## Verification

`./gradlew build` runs five GameTests (`ScramblerGameTests`): the profile is the bike; six steel
ingots craft its chassis; a full bar clears a two-block wall and lands beyond while a tap is stopped;
it leans right into a right turn and left into a left and stands upright parked; a full jump off a
three-block ledge lands its rider and itself unhurt. Then the booth (`ScramblerBooth`, on the booth
display): side on in stock red and painted blue, the front-left quarter, leaning into a turn, the fork
steered, the jump on the **real** jump key (`devtools/booth/xkey.py`): the jump bar, then the bike at
the top of its arc beside the same view on the ground; the jump key held in a turn never drifts it.
`-PskipBooth` leaves the booth out.
