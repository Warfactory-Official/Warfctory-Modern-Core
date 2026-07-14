package com.norwood.wfcore.common.machine;

/**
 * <p>
 * Converted from {@code missile_launcher_multi.litematic} via {@code litematic2gtmb.py}
 * (controller marker: oak planks, facing south; round-trip verified by the script).
 * <p>
 * Symbol legend (predicate mapping in {@code WFMachines}):
 * <ul>
 * <li>S = controller</li>
 * <li>A = Solid Machine Casing (steel) - hosts the energy hatches</li>
 * <li>B = Industrial Steam Casing (GCYM)</li>
 * <li>C = Galvanized Steel frame</li>
 * <li>D = Atomic Casing (GCYM)</li>
 * <li>E = HSSE Sturdy Machine Casing</li>
 * <li>F = Steel Firebox Casing (unlit)</li>
 * <li>G = Black Steel frame</li>
 * </ul>
 * AISLES: 9 chars (FRONT) x 20 strings (UP) x 9 aisles (RIGHT); first string = bottom layer.
 */
public final class MissileLauncherStructure {

    private MissileLauncherStructure() {}

    public static final String[][] AISLES = {
            { " AAAAAAA ", "         ", "         ", "         ", "         ", "         ", "         ", "         ",
                    "         ", "         ", "         ", "         ", "         ", "         ", "         ",
                    "         ", "         ", "         ", "         ", "         " },
            { "AABBBBBAA", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ", "  C D C  ", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ",
                    "  C D C  ", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ", "  C D C  ", "  EEDEE  ", "  EEDEE  ",
                    "  EEDEE  ", "  C D C  ", "  DDDDD  ", "  C D C  ", "  CCCCC  " },
            { "ABBBBBBBA", " D     D ", " D     D ", " D     D ", " DFFFFFD ", " DC C CD ", " DC C CD ", " DC C CD ",
                    " DFFFFFD ", " DC C CD ", " DC C CD ", " DC C CD ", " DFFFFFD ", " DC C CD ", " DC C CD ",
                    " DC C CD ", " DFFFFFD ", " DGGGGGD ", " DAAAAAD ", " CC   CC " },
            { "ABBCCCBBA", " E       ", " E       ", " E       ", "  FG GF  ", " E     E ", " E     E ", " E     E ",
                    "  FG GF  ", " E     E ", " E     E ", " E     E ", "  FG GF  ", " E     E ", " E     E ",
                    " E     E ", "  F   F  ", " DG   GD ", "  A   A  ", " C     C " },
            { "ABBCCCBBS", " E       ", " E       ", " E       ", "  F   F  ", " EC   CE ", " EC   CE ", " EC   CE ",
                    "  F   F  ", " EC   CE ", " EC   CE ", " EC   CE ", "  F   F  ", " EC   CE ", " EC   CE ",
                    " EC   CE ", "  F   F  ", " DG   GD ", "  A   A  ", " C     C " },
            { "ABBCCCBBA", " E       ", " E       ", " E       ", "  FG GF  ", " E     E ", " E     E ", " E     E ",
                    "  FG GF  ", " E     E ", " E     E ", " E     E ", "  FG GF  ", " E     E ", " E     E ",
                    " E     E ", "  F   F  ", " DG   GD ", "  A   A  ", " C     C " },
            { "ABBBBBBBA", " D     D ", " D     D ", " D     D ", " DFFFFFD ", " DC C CD ", " DC C CD ", " DC C CD ",
                    " DFFFFFD ", " DC C CD ", " DC C CD ", " DC C CD ", " DFFFFFD ", " DC C CD ", " DC C CD ",
                    " DC C CD ", " DFFFFFD ", " DGGGGGD ", " DAAAAAD ", " CC   CC " },
            { "AABBBBBAA", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ", "  C D C  ", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ",
                    "  C D C  ", "  EEDEE  ", "  EEDEE  ", "  EEDEE  ", "  C D C  ", "  EEDEE  ", "  EEDEE  ",
                    "  EEDEE  ", "  C D C  ", "  DDDDD  ", "  C D C  ", "  CCCCC  " },
            { " AAAAAAA ", "         ", "         ", "         ", "         ", "         ", "         ", "         ",
                    "         ", "         ", "         ", "         ", "         ", "         ", "         ",
                    "         ", "         ", "         ", "         ", "         " },
    };
}
