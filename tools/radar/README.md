# Radar test tooling

Utilities for testing the WFCore radar's DBSCAN scan pipeline without hand-building the multiblock.

## In-game commands (`/wfcore_radar`, requires op / permission level 2)

| Command | Effect |
| --- | --- |
| `/wfcore_radar targets` | Reports how many players + GregTech machines a scan would see in your dimension (no clustering). A quick sanity check before scanning. |
| `/wfcore_radar scan [eps] [minPts]` | Runs a DBSCAN scan of your current dimension and writes the raw datapoints + clusters to `‹world›/wfcore_radar_exports/scan-‹dim›-‹gametime›.json`. The chat message reports the exact path. |
| `/wfcore_radar datastick [eps] [minPts]` | Runs a scan, stores it, and drops a **printer-ready data stick** straight into your inventory — feed it to a Printer to test the report/book output without a radar. |

> **These test commands ignore the whitelist.** Unlike a built radar (which only sees whitelisted machines
> in the persistent registry), the commands sweep up **every GregTech machine** in the dimension by parsing
> its region files off-thread. They read what is on disk, so very recently placed machines in unsaved chunks
> may be missed — run `/save-all` first if in doubt. The scan runs asynchronously and reports on completion.

`eps` (neighbourhood radius) and `minPts` (minimum points to form a cluster) are the DBSCAN parameters.
Passing them on the command line overrides them **for that scan only** — handy for dialing in. When
omitted, they fall back to the configured defaults in `wfcore-radar.toml`:

```toml
[clustering]
    eps = 200      # neighbourhood radius in blocks
    minPts = 10    # fewest neighbouring targets needed to seed a cluster
```

These same defaults are what a built radar multiblock uses when it scans.

## Whitelist (what a real radar sees) + KubeJS

A built radar only detects **whitelisted** blocks (from `wfcore-radar.toml`, or backfilled by
`/wfcore_retrofit`). Out of the box the whitelist is `gtceu:electric_blast_furnace=10`,
`gtceu:large_chemical_reactor=25`, `minecraft:furnace=1`.

Packs can reshape it from a KubeJS **startup** script via the `WFRadar` binding (overrides are applied on
top of the config and survive `/reload`):

```js
// kubejs/startup_scripts/wfcore_radar.js
WFRadar
    .whitelistMachinesAtLeast('hv')          // every GregTech machine of tier HV+ becomes a target
    .whitelist('gtceu:fusion_reactor', 50)   // add/override a specific block's richness
    .removeFromWhitelist('minecraft:furnace')
    .eps(160).minPts(12)                     // override the default DBSCAN tuning
```

A ready-made "whitelist everything HV+" script is in [`kubejs/wfcore_radar.js`](kubejs/wfcore_radar.js) —
copy it into your pack's `kubejs/startup_scripts/`.

The exports live under `run/` (git-ignored), e.g. in dev:
`run/saves/‹world›/wfcore_radar_exports/`.

## JSON format

```jsonc
{
  "dimension": "minecraft:overworld",
  "gameTime": 123456,
  "generatedAtEpochMs": 1700000000000,
  "eps": 200,
  "minPts": 10,
  "targetCount": 342,
  "clusterCount": 3,
  "points": [                                  // every collected target, clustered or not
    { "x": 100, "z": -50, "type": "PLAYER",    "value": 0,  "clustered": true },
    { "x": 120, "z": -40, "type": "STRUCTURE", "value": 10, "clustered": true },
    { "x": 900, "z": 900, "type": "STRUCTURE", "value": 1,  "clustered": false }  // noise
  ],
  "clusters": [
    {
      "index": 0,
      "center": { "x": 110, "z": -45 },
      "bounds": { "min": { "x": 90, "z": -60 }, "max": { "x": 130, "z": -30 } },
      "clusterValue": 340,                     // summed structure richness
      "playerPopulation": 2,                   // players inside the box
      "pointCount": 57,
      "points":  [ { "x": 100, "z": -50, "type": "PLAYER", "value": 0, "clustered": true }, ... ],
      "players": [ { "x": 100, "z": -50 }, ... ]
    }
  ]
}
```

## Python visualiser

Opens an **interactive** 2D (X, Z) map of a scan: datapoints (noise greyed out), each cluster's bounding
box and centre, and players highlighted with red stars. Z is drawn north-up to match the in-game map.

Interactions:

* **drag** to pan, **scroll** to zoom around the cursor, and the matplotlib toolbar for box-zoom / reset;
* **hover** any datapoint or cluster centre to see its coordinates, type, richness and cluster.

```bash
python -m pip install -r requirements.txt        # matplotlib + numpy only

python radar_visualizer.py path/to/scan.json                 # open interactive window
python radar_visualizer.py scan.json --out map.png           # also save a PNG
python radar_visualizer.py scan.json --no-window             # just save, no window (headless/CI)
python radar_visualizer.py scan.json --hide-noise            # drop DBSCAN noise points
```

The window needs a GUI backend (Tk/Qt/etc.); with no display it automatically falls back to saving a PNG.
