"""Convert a Litematica (.litematic) structure into a GregTech Modern (GTCEu
Modern, MC 1.20.1) multiblock pattern, ready to paste into Warfactory Modern
Core source -- no manual flipping/rotating required.

Output is emitted in the exact orientation the codebase uses, i.e. for

    FactoryBlockPattern.start(RelativeDirection.FRONT,   // char  axis (k)
                              RelativeDirection.UP,       // string axis (j)
                              RelativeDirection.RIGHT)    // aisle  axis (i)

The mapping (k -> +FRONT, j -> +UP, i -> +RIGHT=facing.clockwise) is the exact
inverse of GTCEu's BlockPattern.setActualRelativeOffset for the normal case
(horizontal front facing, upwardsFacing = NORTH, not flipped). The script then
round-trip-verifies its own output by re-porting that GTCEu method and checking
it reproduces every block of the litematic; it refuses to emit on a mismatch.

The controller is found automatically (the lone palette block carrying a
`facing` state) or named with --controller, and its world facing comes from that
`facing` state (override with --facing). The litematic is world-aligned
(+x east, +y up, +z south); since the whole pattern is controller-relative, the
min-corner translation is irrelevant.

Controller marker & facing
--------------------------
The controller is located by a *marker* block in the litematic, and the pattern
is built relative to its front facing. Provide these any of three ways:
  * Place the real controller -- or any block with a `facing` state (an observer,
    furnace, ...) -- where the controller goes, pointing the way it should face.
    It is auto-detected and BOTH its position and facing are read from it.
  * Pass --controller <registry_id> for whatever block you used as a marker
    (e.g. minecraft:oak_planks) and, if it has no facing state, --facing <dir>.
  * Run interactively (default on a terminal): the palette is listed and you are
    asked which block marks the controller and which way it faces.
Use --list to just dump the palette (ids + counts) and exit.

Usage:
    python litematic2gtmb.py doxxer2.litematic --controller-char A --var AISLES
    python litematic2gtmb.py research.litematic --controller minecraft:oak_planks \
        --facing south --controller-char S --var RESEARCH_AISLES
    python litematic2gtmb.py mystructure.litematic --list
"""

import argparse
import string
import sys

import nbtlib

# World/Minecraft direction helpers. World axes: +x east, +y up, +z south.
DIR_VEC = {
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "east": (1, 0, 0),
    "west": (-1, 0, 0),
}
# Direction.getClockWise() for horizontal directions (viewed from above).
CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}

AIR_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

PREDICATE_MAP = {
    "gtceu:solid_machine_casing": "blocks(GTBlocks.CASING_STEEL_SOLID.get())",
    "gtceu:steel_machine_casing": "blocks(GTBlocks.STEEL_HULL.get())",
    "gtceu:steam_machine_casing": "blocks(GTBlocks.CASING_BRONZE_BRICKS.get())",
    "gtceu:bronze_machine_casing": "blocks(GTBlocks.BRONZE_HULL.get())",
    "gtceu:firebricks": "blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())",
    "gtceu:bronze_firebox_casing": "blocks(GTBlocks.FIREBOX_BRONZE.get())",
    "gtceu:bronze_frame": "frames(GTMaterials.Bronze)",
    "gtceu:tempered_glass": "blocks(GTBlocks.CASING_TEMPERED_GLASS.get())",
    "gtceu:atomic_casing": "blocks(GCYMBlocks.CASING_ATOMIC.get())",
    "gtceu:industrial_steam_casing": "blocks(GCYMBlocks.CASING_INDUSTRIAL_STEAM.get())",
    "gtceu:steel_firebox_casing": "blocks(GTBlocks.FIREBOX_STEEL.get())",
    "gtceu:cupronickel_coil_block": "blocks(GTBlocks.COIL_CUPRONICKEL.get())",
    "gtceu:reinforced_stone": "blocks(GTBlocks.REINFORCED_STONE.get())",
    "gtceu:light_concrete": "blocks(GTBlocks.LIGHT_CONCRETE.get())",
    "gtceu:laminated_glass": "blocks(GTBlocks.CASING_LAMINATED_GLASS.get())",
    "gtceu:stainless_steel_gearbox": "blocks(GTBlocks.CASING_STAINLESS_STEEL_GEARBOX.get())",
    "gtceu:black_metal_sheet": "blocks(GTBlocks.METAL_SHEETS.get(DyeColor.BLACK).get())",
    "gtceu:yellow_lamp": "blocks(GTBlocks.LAMPS.get(DyeColor.YELLOW).get())",
    # Titanium turbine casing is swapped for WFCore's own CTM casing block.
    "gtceu:titanium_turbine_casing": "blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())",
    "gtceu:aluminium_frame": "frames(GTMaterials.Aluminium)",
    "gtceu:aluminium_block": "blocks(matBlock(GTMaterials.Aluminium))",
    "gtceu:steel_frame": "frames(GTMaterials.Steel)",
    "gtceu:black_steel_frame": "frames(GTMaterials.BlackSteel)",
    "gtceu:polytetrafluoroethylene_frame": "frames(GTMaterials.Polytetrafluoroethylene)",
    "wfcore:galvanized_steel_frame": "frames(WFMaterials.GalvanizedSteel)",
    "wfcore:galvanized_steel_block": "blocks(matBlock(WFMaterials.GalvanizedSteel))",
    "wfcore:aluminium_sheet_casing": "blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())",
    "wfcore:boltable_casing": "blocks(WFBlocks.BOLTABLE_CASING.get())",
}

CHAR_POOL = string.ascii_uppercase + string.ascii_lowercase + string.digits


def palette_id(entry):
    """Registry name of a palette entry, e.g. 'minecraft:oak_planks'."""
    return str(entry["Name"])


def palette_label(entry):
    """Full 'name[prop=val,...]' label of a palette entry, for comments."""
    name = str(entry["Name"])
    props = entry.get("Properties")
    if props:
        return f"{name}[{','.join(f'{k}={v}' for k, v in props.items())}]"
    return name


def unpack_block_states(long_array, bits, count):
    """Unpack Litematica's bit-packed BlockStates (pre-1.16 straddling scheme)."""
    mask = (1 << bits) - 1
    longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in long_array]
    out = []
    for i in range(count):
        start = i * bits
        s_long, e_long, off = start >> 6, ((i + 1) * bits - 1) >> 6, start & 63
        if s_long == e_long:
            out.append((longs[s_long] >> off) & mask)
        else:
            out.append(((longs[s_long] >> off) | (longs[e_long] << (64 - off))) & mask)
    return out


def gtceu_actual_offset(x, y, z, facing):
    actual = (
        facing,
        "up",
        CLOCKWISE[facing],
    )  # FRONT -> facing, UP -> up, RIGHT -> facing.clockwise
    c0 = (x, y, z)
    c1 = [0, 0, 0]
    for i in range(3):
        d, v = actual[i], c0[i]
        if d == "up":
            c1[1] = v
        elif d == "down":
            c1[1] = -v
        elif d == "west":
            c1[0] = -v
        elif d == "east":
            c1[0] = v
        elif d == "north":
            c1[2] = -v
        elif d == "south":
            c1[2] = v
    return tuple(c1)


def load_region(path):
    nbt = nbtlib.load(path)
    regions = nbt["Regions"]
    name = next(iter(regions))
    if len(regions) > 1:
        print(f"// WARNING: {len(regions)} regions; using '{name}'", file=sys.stderr)
    return name, regions[name]


def marker_facing(entry):
    props = entry.get("Properties")
    if props and "facing" in props and str(props["facing"]) in DIR_VEC:
        return str(props["facing"])
    return None


def _ask(prompt):
    print(prompt, end="", file=sys.stderr, flush=True)
    try:
        return input().strip()
    except EOFError:
        return ""


def region_palette_counts(region):
    palette = region["BlockStatePalette"]
    w, h, l = (abs(int(region["Size"][k])) for k in "xyz")
    idxs = unpack_block_states(
        region["BlockStates"], max(2, (len(palette) - 1).bit_length()), w * h * l
    )
    counts = {}
    for p in idxs:
        counts[p] = counts.get(p, 0) + 1
    return palette, counts


def print_palette(palette, counts, file=sys.stderr):
    print("Palette (non-air blocks):", file=file)
    for i, e in enumerate(palette):
        if counts.get(i) and palette_id(e) not in AIR_BLOCKS:
            tag = "  <-- has facing state" if marker_facing(e) else ""
            print(f"  [{i}] {palette_label(e)}  x{counts[i]}{tag}", file=file)


def resolve_controller(palette, ids, counts, controller_id, interactive):
    if controller_id is not None:
        idx = next((i for i, rid in enumerate(ids) if rid == controller_id), None)
        if idx is None:
            sys.exit(
                f"Controller block '{controller_id}' is not in the palette (try --list)."
            )
        return idx
    facers = [i for i, e in enumerate(palette) if counts.get(i) and marker_facing(e)]
    if len(facers) == 1:
        print(
            f"// auto-detected controller marker: {palette_label(palette[facers[0]])}",
            file=sys.stderr,
        )
        return facers[0]
    if interactive:
        print_palette(palette, counts)
        while True:
            ans = _ask("Which block marks the controller? [index or registry id]: ")
            if ans.isdigit() and counts.get(int(ans)):
                return int(ans)
            idx = next((i for i, rid in enumerate(ids) if rid == ans), None)
            if idx is not None:
                return idx
            if not ans:
                sys.exit("No controller selected.")
            print("  not a valid choice; try again", file=sys.stderr)
    sys.exit(
        f"Could not auto-detect a controller ({len(facers)} blocks carry a 'facing' state). "
        "Pass --controller <id> (see --list), or run with --interactive."
    )


def resolve_facing(palette, ctrl_idx, facing_arg, interactive):
    if facing_arg:
        return facing_arg
    mf = marker_facing(palette[ctrl_idx])
    if mf:
        print(
            f"// facing read from the controller marker's state: {mf}", file=sys.stderr
        )
        return mf
    if interactive:
        ans = _ask(
            "Controller front facing [north/south/east/west] (default south): "
        ).lower()
        if ans in DIR_VEC:
            return ans
        if ans:
            print(
                f"  '{ans}' is not a horizontal direction; using south", file=sys.stderr
            )
    print(
        "// facing not given and no marker facing state; defaulting to south (use --facing to override)",
        file=sys.stderr,
    )
    return "south"


def build(region, controller_id, controller_char, facing, var_name, interactive=False):
    palette = region["BlockStatePalette"]
    ids = [palette_id(e) for e in palette]
    labels = [palette_label(e) for e in palette]
    is_air = [i.split("[")[0] in AIR_BLOCKS for i in ids]

    w, h, l = (abs(int(region["Size"][k])) for k in "xyz")  # x,y,z dims
    indices = unpack_block_states(
        region["BlockStates"], max(2, (len(palette) - 1).bit_length()), w * h * l
    )

    grid = {}
    for ly in range(h):
        for lz in range(l):
            for lx in range(w):
                grid[(lx, ly, lz)] = indices[(ly * l + lz) * w + lx]

    counts = {}
    for p in grid.values():
        if not is_air[p]:
            counts[p] = counts.get(p, 0) + 1

    ctrl_idx = resolve_controller(palette, ids, counts, controller_id, interactive)
    ctrl_cells = [pos for pos, p in grid.items() if p == ctrl_idx]
    if len(ctrl_cells) != 1:
        print(
            f"// WARNING: controller marker {palette_label(palette[ctrl_idx])} appears x{len(ctrl_cells)}; "
            f"using {ctrl_cells[0]} (use a unique marker block for an exact position)",
            file=sys.stderr,
        )
    cx, cy, cz = ctrl_cells[0]

    facing = resolve_facing(palette, ctrl_idx, facing, interactive)

    fvx, _, fvz = DIR_VEC[facing]  # FRONT  (char axis)
    rvx, _, rvz = DIR_VEC[CLOCKWISE[facing]]  # RIGHT  (aisle axis)

    def coords(pos):
        dx, dy, dz = pos[0] - cx, pos[1] - cy, pos[2] - cz
        return (
            dx * fvx + dz * fvz,
            dy,
            dx * rvx + dz * rvz,
        )  # char_rel, string_rel, aisle_rel

    cells = [(pos, p) for pos, p in grid.items() if not is_air[p]]
    crs = [coords(pos) for pos, _ in cells]
    cmin = min(c[0] for c in crs)
    smin = min(c[1] for c in crs)
    amin = min(c[2] for c in crs)
    W = max(c[0] for c in crs) - cmin + 1
    H = max(c[1] for c in crs) - smin + 1
    D = max(c[2] for c in crs) - amin + 1

    char_of = {ctrl_idx: controller_char}
    legend_order = [ctrl_idx]
    pool = (c for c in CHAR_POOL if c != controller_char)
    grid_chars = [[[" "] * W for _ in range(H)] for _ in range(D)]
    ctrl_abc = None
    for pos, p in cells:
        cr, sr, ar = coords(pos)
        a, b, c = cr - cmin, sr - smin, ar - amin
        if p not in char_of:
            char_of[p] = next(pool)
            legend_order.append(p)
        grid_chars[c][b][a] = char_of[p]
        if p == ctrl_idx:
            ctrl_abc = (a, b, c)

    ac, bc, cc = ctrl_abc
    for c in range(D):
        for b in range(H):
            for a in range(W):
                ch = grid_chars[c][b][a]
                dx, dy, dz = gtceu_actual_offset(a - ac, b - bc, c - cc, facing)
                wp = (cx + dx, cy + dy, cz + dz)
                expect = grid.get(wp)
                got_air = ch == " "
                exp_air = expect is None or is_air[expect]
                if got_air != exp_air or (not got_air and char_of.get(expect) != ch):
                    sys.exit(
                        f"VERIFY FAILED at aisle={c} string={b} char={a}: "
                        f"emitted '{ch}', GTCEu would read {labels[expect] if expect is not None else 'OOB'}"
                    )

    out = []
    out.append(
        f"// {var_name}: {W} chars (FRONT) x {H} strings (UP) x {D} aisles (RIGHT)"
    )
    out.append(
        f"// controller='{ids[ctrl_idx]}' char='{controller_char}' facing={facing}  "
        f"(start(FRONT, UP, RIGHT); first string = bottom layer)"
    )
    out.append(f"public static final String[][] {var_name} = {{")
    for c in range(D):
        rows = ", ".join(f'"{"".join(grid_chars[c][b])}"' for b in range(H))
        out.append(f"        {{ {rows} }},")
    out.append("};")
    out.append("")
    out.append("// --- legend (.where clauses) ---")
    out.append(
        f".where('{controller_char}', controller(blocks(definition.getBlock())))"
    )
    unknown = []
    for p in legend_order:
        if p == ctrl_idx:
            continue
        rid = ids[p]
        pred = PREDICATE_MAP.get(rid)
        if pred is None:
            pred = f"blocks(/* TODO map {rid} */)"
            unknown.append(rid)
        out.append(f".where('{char_of[p]}', {pred}) // {labels[p]} x{counts[p]}")
    out.append(".where(' ', any())")
    if unknown:
        print(
            f"// WARNING: no predicate mapping for: {sorted(set(unknown))}",
            file=sys.stderr,
        )
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("litematic_file_path")
    ap.add_argument(
        "--controller",
        help="registry id of the block marking the controller "
        "(default: auto-detect the lone block with a 'facing' state)",
    )
    ap.add_argument(
        "--controller-char", default="S", help="char for the controller (default 'S')"
    )
    ap.add_argument(
        "--facing",
        choices=list(DIR_VEC),
        help="controller front facing (default: the marker's facing state, else asked, else south)",
    )
    ap.add_argument(
        "--var", default="AISLES", help="Java constant name (default AISLES)"
    )
    ap.add_argument(
        "--list", action="store_true", help="list the palette (ids + counts) and exit"
    )
    ap.add_argument(
        "--interactive",
        action="store_true",
        help="force prompting for the controller / facing",
    )
    ap.add_argument(
        "--non-interactive", action="store_true", help="never prompt (fail instead)"
    )
    args = ap.parse_args()

    _, region = load_region(args.litematic_file_path)

    if args.list:
        palette, counts = region_palette_counts(region)
        print_palette(palette, counts, file=sys.stdout)
        return

    interactive = args.interactive or (sys.stdin.isatty() and not args.non_interactive)
    print(
        build(
            region,
            args.controller,
            args.controller_char,
            args.facing,
            args.var,
            interactive,
        )
    )


if __name__ == "__main__":
    main()
