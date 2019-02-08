package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.ccsds.TmManagedParameters.FrameType;

/**
 * 
 * @author nm
 * TM Transfer Frame as per 
 * 
 * CCSDS RECOMMENDED STANDARD FOR TM SPACE DATA LINK PROTOCOL 
 * CCSDS 132.0-B-2 September 2015 
 *
 */
public class TmTransferFrame extends AbstractTransferFrame {
    FrameType frameType;
    public TmTransferFrame(int masterChannelId, int virtualChannelId) {
        super(masterChannelId, virtualChannelId);
    }

    
    public static TmTransferFrame parseData(byte[] data) throws CorruptedFrameException {
        throw new UnsupportedOperationException("not yet implemented");
    }


    @Override
    public boolean containsOnlyIdleData() {
        return frameType==FrameType.PACKET;
    }


    @Override
    int getSeqCountWrapArround() {
        return 0xFF;
    }


    @Override
    int getSeqInterruptionDelta() {
        return 100;
    }

}
