package com.norwood.wfcore.common.machine;

/**
 * Aisle layout of the drilling rig, adapted from the 1.12.2 SuSy drilling-rig pattern. The first string of each
 * aisle is the bottom layer (built up along {@code UP}); 'F' is the drill head, 'M' is the muffler hatch at the
 * dead-centre top, and 'H'/' ' are unconstrained (the deposit is detected at runtime rather than being part of
 * the structure). See {@code WFMachines.DRILL_RIG} for the symbol → block mapping.
 */
public final class DrillRigStructure {

    private DrillRigStructure() {}

    public static final String[][] AISLES = {
            { "               ", "     DDDDD     ", "     DDDDD     ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "   DDDDDDDDD   ", "   DDDDDDDDD   ", "    BB   BB    ", "    BB   BB    ",
                    "    BB   BB    ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "  DDDDDDDDDDD  ", "  DDDDDDDDDDD  ", "    BB   BB    ", "    BB   BB    ",
                    "    BB   BB    ", "     BB BB     ", "     BB BB     ", "     BB BB     ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", " DDDDDDDDDDDDD ", " DDDDDDDDDDDDD ", "               ", "               ",
                    "               ", "     BB BB     ", "     BB BB     ", "     BB BB     ", "     BB BB     ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", " DDDDDDDDDDDDD ", " DDDDDDDDDDDDD ", " BB         BB ", " BB         BB ",
                    " BB         BB ", "               ", "       E       ", "     BBEBB     ", "     AAEAA     ",
                    "       E       ", "       E       ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "DDDDDD   DDDDDD", "DDDDDD   DDDDDD", " BB         BB ", " BB         BB ",
                    " BB         BB ", "  BB       BB  ", "  BB   E   BB  ", "  BBBAAAAABBB  ", "   BAAACAAAB   ",
                    "     GGCGG     ", "     BGEGB     ", "     BGAGB     ", "     B   B     ", "     B   B     ",
                    "     BB  B     ", "     B B B     ", "     B  BB     ", "     B   B     " },
            { "               ", "DDDDD     DDDDD", "DDDDD     DDDDD", "      AAA      ", "      BAB      ",
                    "      BAB      ", "  BB  BAB  BB  ", "  BB  BAB  BB  ", "  BBBAAAAABBB  ", "   BAACCCAAB   ",
                    "     GCCCG     ", "     GCCCG     ", "     GGCGG     ", "               ", "               ",
                    "         B     ", "      ACA      ", "     BAAA      ", "               " },
            { "       H       ", "DDDDD  F  DDDDD", "DDDDD  C  DDDDD", "      ACA      ", "      ACA      ",
                    "      ACA      ", "      ACA      ", "    EEACAEE    ", "    EAACAAE    ", "    ECCCCCE    ",
                    "    ECCCCCE    ", "    EECCCEE    ", "     AGGGA     ", "       C       ", "       C       ",
                    "       C       ", "     BCCCB     ", "      AMA      ", "               " },
            { "               ", "DDDDD     DDDDD", "DDDDD     DDDDD", "      AAA      ", "      BSB      ",
                    "      BAB      ", "  BB  BAB  BB  ", "  BB  BAB  BB  ", "  BBBAAAAABBB  ", "   BAACCCAAB   ",
                    "     GCCCG     ", "     GCCCG     ", "     GGCGG     ", "               ", "               ",
                    "     B         ", "      ACA      ", "      AAAB     ", "               " },
            { "               ", "DDDDDD   DDDDDD", "DDDDDD   DDDDDD", " BB         BB ", " BB         BB ",
                    " BB         BB ", "  BB       BB  ", "  BB   E   BB  ", "  BBBAAAAABBB  ", "   BAAACAAAB   ",
                    "     GGCGG     ", "     BGEGB     ", "     BGAGB     ", "     B   B     ", "     B   B     ",
                    "     B  BB     ", "     B B B     ", "     BB  B     ", "     B   B     " },
            { "               ", " DDDDDDDDDDDDD ", " DDDDDDDDDDDDD ", " BB         BB ", " BB         BB ",
                    " BB         BB ", "               ", "       E       ", "     BBEBB     ", "     AAEAA     ",
                    "       E       ", "       E       ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", " DDDDDDDDDDDDD ", " DDDDDDDDDDDDD ", "               ", "               ",
                    "               ", "     BB BB     ", "     BB BB     ", "     BB BB     ", "     BB BB     ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "  DDDDDDDDDDD  ", "  DDDDDDDDDDD  ", "    BB   BB    ", "    BB   BB    ",
                    "    BB   BB    ", "     BB BB     ", "     BB BB     ", "     BB BB     ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "   DDDDDDDDD   ", "   DDDDDDDDD   ", "    BB   BB    ", "    BB   BB    ",
                    "    BB   BB    ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
            { "               ", "     DDDDD     ", "     DDDDD     ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               ", "               ",
                    "               ", "               ", "               ", "               " },
    };
}
