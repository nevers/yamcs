package org.yamcs.tctm.ccsds;

import java.util.Map;

public interface ManagedParameters {

    int getMaxFrameLength();

    int getMinFrameLength();

    Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance);

}
