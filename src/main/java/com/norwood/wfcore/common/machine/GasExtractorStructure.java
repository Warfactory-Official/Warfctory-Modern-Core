package com.norwood.wfcore.common.machine;

/**
 * Aisle layout of the Large Gas Extractor, generated from {@code large_gas_extractor.litematic} via
 * {@code tools/litematic2gtmb.py} (controller = the {@code minecraft:oak_planks} marker, facing south). The
 * first string of each aisle is the bottom layer (built up along {@code UP}); the controller 'S' sits on the
 * front of the tower. See {@code WFMachines.GAS_EXTRACTOR} for the symbol → block mapping.
 */
public final class GasExtractorStructure {

    private GasExtractorStructure() {}

    // 7 chars (FRONT) x 14 strings (UP) x 7 aisles (RIGHT).
    public static final String[][] AISLES = {
            { "AAAAAAA", "CCCBCCC", "   D   ", "   D   ", "   D   ", "       ", "       ", "       ", "       ", "       ", "       ", "       ", "       ", "       " },
            { "AAAAAAA", "CCCCCCC", "       ", "  EEE  ", "  EEE  ", "   D   ", "  FFF  ", "  GFG  ", "   F   ", "   F   ", "   F   ", "  GFG  ", "  FFF  ", "       " },
            { "AA   AA", "CCCCCCC", "  EEE  ", " E   E ", " E   E ", "  EEE  ", " FFBFF ", " GFBFG ", "  FBF  ", "  FBF  ", "  FBF  ", " GFBFG ", " FFBFF ", "  FFF  " },
            { "AA B AA", "BCCBCCB", "D EEE D", "DE   ED", "DE   ED", " DEEED ", " FB BS ", " FB BF ", " FB BF ", " FB BF ", " FB BF ", " FB BF ", " FB BF ", "  FFF  " },
            { "AA   AA", "CCCCCCC", "  EEE  ", " E   E ", " E   E ", "  EEE  ", " FFBFF ", " GFBFG ", "  FBF  ", "  FBF  ", "  FBF  ", " GFBFG ", " FFBFF ", "  FFF  " },
            { "AAAAAAA", "CCCCCCC", "       ", "  EEE  ", "  EEE  ", "   D   ", "  FFF  ", "  GFG  ", "   F   ", "   F   ", "   F   ", "  GFG  ", "  FFF  ", "       " },
            { "AAAAAAA", "CCCBCCC", "   D   ", "   D   ", "   D   ", "       ", "       ", "       ", "       ", "       ", "       ", "       ", "       ", "       " },
    };
}
