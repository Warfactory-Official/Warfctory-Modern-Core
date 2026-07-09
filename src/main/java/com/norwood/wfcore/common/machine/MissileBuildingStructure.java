package com.norwood.wfcore.common.machine;

/**
 * <p>
 * Converted from {@code missile_building_multi.litematic} via {@code processing/litematic2gtmb.py}.
 * <p>
 * Symbol legend (predicate mapping in {@code WFMachines}):
 * <ul>
 * <li>S = controller</li>
 * <li>A = Solid Machine Casing (steel)</li>
 * <li>B = Industrial Steam Casing (GCYM)</li>
 * <li>C = Atomic Casing (GCYM)</li>
 * <li>D = HSSE Sturdy Casing</li>
 * <li>E = Galvanized Steel frame</li>
 * <li>F = Black Steel frame</li>
 * <li>G = Light Gray Metal Sheet</li>
 * </ul>
 */
public final class MissileBuildingStructure {

    private MissileBuildingStructure() {}

    public static final String[][] AISLES = {
            { "      AAAAA      ", "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "    AAAAAAAAA    ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ",
                    "      C   C      ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ",
                    "      E   E      ", "      E   E      ", "      E   E      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "   AAABBBBBAAA   ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ",
                    "      GGGGG      ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ",
                    "      GGGGG      ", "      GGGGG      ", "      E   E      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "  AABBBBBBBBBAA  ", "   D         D   ", "   D         D   ", "   D         D   ", "   D         D   ",
                    "    GG     GG    ", "   D         D   ", "   D         D   ", "   D         D   ",
                    "    GG     GG    ", "    GG     GG    ", "      DDDDD      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { " AABBBBBBBBBBBAA ", "  D           D  ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "   G         G   ", "    DDDDDDDDD    ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { " AABBBBBBBBBBBAA ", "  D           D  ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "   G         G   ", "    DDDDDDDDD    ", "       GGG       ",
                    "        E        ", "        E        ", "        E        ", "        E        ",
                    "       EEE       ", "       AAA       " },
            { "AABBBBBBBBBBBBBAA", " CE   AAAAA   EC ", " CE     F     EC ", " CE     F     EC ", " CE     F     EC ",
                    " CG    FFF    GC ", " CE     F     EC ", " CE     F     EC ", " CE     F     EC ",
                    " EG           GE ", " EG           GE ", " EEDDDD   DDDDEE ", "      GCCCG      ",
                    "       DCD       ", "       DCD       ", "       DCD       ", "       DCD       ",
                    "      ECCCE      ", "      AEEEA      " },
            { "AABBBBBBBBBBBBBAA", " D    ABBBA      ", " D               ", " D               ", " D             D ",
                    "  G   F   F   G  ", " D             D ", " D             D ", " D             D ",
                    "  G           G  ", "  G           G  ", "   DDD     DDD   ", "     GC   CG     ",
                    "      D   D      ", "      D   D      ", "      D   D      ", "      D   D      ",
                    "     EC   CE     ", "     AE   EA     " },
            { "AABBBBBBBBBBBBBAS", " D    ABEBA      ", " D    F   F      ", " D    F   F      ", " D    F   F    D ",
                    "  G   F   F   G  ", " D    F   F    D ", " D    F   F    D ", " D    F   F    D ",
                    "  G           G  ", "  G           G  ", "   DDD     DDD   ", "     GC   CG     ",
                    "     EC   CE     ", "     EC   CE     ", "     EC   CE     ", "     EC   CE     ",
                    "     EC   CE     ", "     AE   EA     " },
            { "AABBBBBBBBBBBBBAA", " D    ABBBA      ", " D               ", " D               ", " D             D ",
                    "  G   F   F   G  ", " D             D ", " D             D ", " D             D ",
                    "  G           G  ", "  G           G  ", "   DDD     DDD   ", "     GC   CG     ",
                    "      D   D      ", "      D   D      ", "      D   D      ", "      D   D      ",
                    "     EC   CE     ", "     AE   EA     " },
            { "AABBBBBBBBBBBBBAA", " CE   AAAAA   EC ", " CE     F     EC ", " CE     F     EC ", " CE     F     EC ",
                    " CG    FFF    GC ", " CE     F     EC ", " CE     F     EC ", " CE     F     EC ",
                    " EG           GE ", " EG           GE ", " EEDDDD   DDDDEE ", "      GCCCG      ",
                    "       DCD       ", "       DCD       ", "       DCD       ", "       DCD       ",
                    "      ECCCE      ", "      AEEEA      " },
            { " AABBBBBBBBBBBAA ", "  D           D  ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "   G         G   ", "    DDDDDDDDD    ", "       GGG       ",
                    "        E        ", "        E        ", "        E        ", "        E        ",
                    "       EEE       ", "       AAA       " },
            { " AABBBBBBBBBBBAA ", "  D           D  ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "  D           D  ", "  D           D  ", "  D           D  ",
                    "   G         G   ", "   G         G   ", "    DDDDDDDDD    ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "  AABBBBBBBBBAA  ", "   D         D   ", "   D         D   ", "   D         D   ", "   D         D   ",
                    "    GG     GG    ", "   D         D   ", "   D         D   ", "   D         D   ",
                    "    GG     GG    ", "    GG     GG    ", "      DDDDD      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "   AAABBBBBAAA   ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ",
                    "      GGGGG      ", "    DDE   EDD    ", "    DDE   EDD    ", "    DDE   EDD    ",
                    "      GGGGG      ", "      GGGGG      ", "      E   E      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "    AAAAAAAAA    ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ",
                    "      C   C      ", "      CDDDC      ", "      CDDDC      ", "      CDDDC      ",
                    "      E   E      ", "      E   E      ", "      E   E      ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
            { "      AAAAA      ", "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 ", "                 ", "                 ",
                    "                 ", "                 " },
    };
}
