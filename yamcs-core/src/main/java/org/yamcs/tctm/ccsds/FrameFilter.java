package org.yamcs.tctm.ccsds;

/**
 * A filter is used by the {@link MasterChannelFrameHandler} to distribute the filters to the individual
 * VirtualChannelFrameHandlers
 * 
 * @author nm
 *
 */
public class FrameFilter {
    /**
     * Master Channel Id
     * -1 means match all
     */
    final int masterChannelId;
    /**
     * Virtual Channel Id
     * -1 means match all.
     */
    final int virtualChannelId;

    public FrameFilter(int masterChannelId, int virtualChannelId) {
        this.masterChannelId = masterChannelId;
        this.virtualChannelId = virtualChannelId;
    }
}
