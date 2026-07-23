package com.norwood.wfcore.common.ballistics;

public final class SectionSolidity {

    public static final SectionSolidity AIR = new SectionSolidity(null, false);

    public static final SectionSolidity SOLID = new SectionSolidity(null, true);

    private final long[] bits;

    private final boolean uniformSolid;

    private SectionSolidity(long[] bits, boolean uniformSolid) {
        this.bits = bits;
        this.uniformSolid = uniformSolid;
    }

    public static SectionSolidity ofBits(long[] bits64) {
        return new SectionSolidity(bits64, false);
    }

    public boolean isSolid(int lx, int ly, int lz) {
        if (bits == null) {
            return uniformSolid;
        }
        int idx = (ly << 8) | (lz << 4) | lx;
        return (bits[idx >> 6] & (1L << (idx & 63))) != 0L;
    }
}
