---
title: Scrambler — project
type: overview
layer: store
tags: [overview]
---

# Scrambler

## What this is

A dirt bike for Vanilla Wheels: a data-only NeoForge 1.21.1 mod (`lowcodefml`) holding a profile, one
Blockbench project (the model as received, its red parts made dyeable), a chassis recipe, a lang file
and an engine loop, with the protocol (1.19.0) nested inside. D-0001 says why it is as it is.

## 1.0.0 — built 2026-10-23, not released

Built on Rusty's ask of 2026-10-23 with the Iron Horse and Vanilla Wheels 1.19.0 (its D-0047: the
horse's jump, the lean, moving parts). Gate (`./gradlew build`): 5 GameTests and the booth's checks
green on the booth display in software. Waiting on Rusty's look at the booth photos and his go; no tag,
no remote yet.

## Shape

No Java in the shipped mod. `devtools/art/build.py` writes every game file from
`devtools/art/reference/dirtbike.bbmodel` through `minecraft mods/tools/bbgen/bike.py`; its numbers
and where they come from are in `devtools/art/reference/SOURCES.md`. The gametest source set holds the
GameTests and the booth, which presses the real jump key through `devtools/booth/xkey.py`.
