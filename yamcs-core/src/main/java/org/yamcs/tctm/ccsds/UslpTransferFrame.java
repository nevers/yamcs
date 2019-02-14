package org.yamcs.tctm.ccsds;

/**
 * Transfer Frames as per:
 * 
 * CCSDS RECOMMENDED STANDARD FOR UNIFIED SPACE DATA LINK PROTOCOL 
 * CCSDS 732.1-B-1 October 2018
 * 
 * 
 * @author nm
 * 
 */
public class UslpTransferFrame extends AbstractTransferFrame {
    
    public UslpTransferFrame(byte[] data, int masterChannelId, int virtualChannelId) {
        super(data, masterChannelId, virtualChannelId);
    }


    @Override
    protected int getSeqInterruptionDelta() {
        return 10000;
    }


    @Override
    public boolean containsOnlyIdleData() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    int getSeqCountWrapArround() {
        return 0;
    }
}
