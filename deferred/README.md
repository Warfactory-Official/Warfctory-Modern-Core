# Deferred, not ported

Source parked here is **excluded from the build** and kept verbatim as it stood on
1.20.1 / Forge 47.4.22, so it can be picked up in a later pass without re-deriving it.
See `WFCORE-PORT-PLAN.md` §3.2 ("Deferred, not scheduled").

## `diagnostics/` (~5.0k LOC)

Class dump, screenshot capture, frame verification, mod audit, and their client viewers.

**Already carried over, do not re-port from here:**

| Old | New |
|---|---|
| `diagnostics/DiagNet` | `net/WFNet` — the channel itself, rebuilt on NeoForge payloads |
| id 5 `DarknessEnforceMessage` | `common/darkness/DarknessEnforceMessage` (payload) |
| id 6 `ShaderEnforceMessage` | `common/shader/ShaderEnforceMessage` (payload) |
| id 12 `ResearchSyncMessage` | `common/research/ResearchSyncMessage` (payload) |

The other ~16 message types (ids 0–4, 7–11, 13–18) defer with the package.
