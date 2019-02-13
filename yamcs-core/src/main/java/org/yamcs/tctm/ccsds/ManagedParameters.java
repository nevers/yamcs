package org.yamcs.tctm.ccsds;

import java.util.Map;

/**
 * Stores configuration related to Master channels
 * @author nm
 *
 */
public abstract class ManagedParameters {
    String physicalChannelName;
    int spacecraftId;
    
    abstract int getMaxFrameLength();
    
    abstract int getMinFrameLength();

    abstract Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance, String parentLinkName);

}
