package com.norwood.wfcore;

public interface IWFCoreVehicleUI {

    /**
     * Client-side: store the size/columns received in the UI-open packet so {@code createUI} rebuilds an identical
     * grid.
     */
    void wfcore$setSyncedUiSize(int slots, int cols);

    /** Server: configured columns from the override; client: the synced columns. Falls back to 9. */
    int wfcore$uiColumns();
}
