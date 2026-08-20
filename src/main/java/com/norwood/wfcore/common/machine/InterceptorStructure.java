package com.norwood.wfcore.common.machine;

/**
 * Converted from {@code interceptor.litematic} via {@code litematic2gtmb.py}
 * (controller marker: oak planks, facing south; round-trip verified by the script).
 * <p>
 * Symbol legend (predicate mapping in {@code WFMachines}):
 * <ul>
 * <li>S = controller</li>
 * <li>A = Solid Machine Casing (steel) - hosts the energy hatch</li>
 * <li>B = Black Steel frame</li>
 * <li>C = Atomic Casing (GCYM)</li>
 * <li>D = Galvanized Steel frame</li>
 * </ul>
 * AISLES: 7 chars (FRONT) x 5 strings (UP) x 5 aisles (RIGHT); first string = bottom layer.
 */
public final class InterceptorStructure {

    private InterceptorStructure() {}

    public static final String[][] AISLES = {
            { "  AAA  ", "       ", "       ", "       ", "       " },
            { "ABAAABA", "CC  D  ", "CCCD   ", " CCC   ", "  CC   " },
            { "AAAAAAS", "CC     ", "CCC    ", " CCC   ", "  CC   " },
            { "ABAAABA", "CC  D  ", "CCCD   ", " CCC   ", "  CC   " },
            { "  AAA  ", "       ", "       ", "       ", "       " },
    };
}
