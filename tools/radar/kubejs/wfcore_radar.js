// priority: 0
//
// WFCore radar target whitelist — startup script.
// Copy this into your pack at:  kubejs/startup_scripts/wfcore_radar.js
//
// Whitelist every GregTech machine of tier HV and above (HV, EV, IV, LuV, ZPM, UV, ...) as a radar target.
// Lower-tier (ULV/LV/MV) machines and non-machine blocks (casings, pipes, ores) are left out.
WFRadar.whitelistMachinesAtLeast('hv')

// --- optional extras -------------------------------------------------------
// Give a few standout multiblocks a higher richness so their bases rank up:
// WFRadar
//     .whitelist('gtceu:fusion_reactor', 50)
//     .whitelist('gtceu:large_chemical_reactor', 25)
//
// Drop something you don't want the radar to see:
// WFRadar.removeFromWhitelist('minecraft:furnace')
//
// Retune the default DBSCAN clustering (still overridable per scan with /wfcore_radar scan <eps> <minPts>):
// WFRadar.eps(160).minPts(12)
