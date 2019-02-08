package org.yamcs.tctm.ccsds;
/**
 * Common properties for the three supported transfer frame types
 * 
 * @author nm
 *
 */
public abstract class AbstractTransferFrame implements TransferFrame {
    final protected int masterChannelId;
    final protected int virtualChannelId;
    
    long vcFrameSeq;
    byte[] data;
    
    public AbstractTransferFrame(int masterChannelId, int virtualChannelId) {
        this.masterChannelId = masterChannelId;
        this.virtualChannelId = virtualChannelId;
    }
    
    @Override
    public boolean matchesFilter(FrameFilter filter) {
        if ((filter.masterChannelId != -1) && (filter.masterChannelId != masterChannelId)) {
            return false;
        }
        
        if ((filter.virtualChannelId != -1) && (filter.virtualChannelId != virtualChannelId)) {
            return false;
        }
        
        return true;
    }
    @Override
    public int getMasterChannelId() {
        return masterChannelId;
    }

    @Override
    public int getVirtualChannelId() {
        return virtualChannelId;
    }
    
    @Override
    public int lostFramesCount(long prevFrameSeq) {
        long delta = prevFrameSeq < vcFrameSeq ? vcFrameSeq - prevFrameSeq :  vcFrameSeq + getSeqCountWrapArround() - prevFrameSeq;
        delta--;
        if (delta > getSeqInterruptionDelta()) {
            return -1;
        } else {
            return (int)delta;
        }
    }
    @Override
    public long getVcFrameSeq() {
        return vcFrameSeq;
    }
    
    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public int getDataStart() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public int getFirstSduStart() {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public int getDataEnd() {
        // TODO Auto-generated method stub
        return 0;
    }


    
    abstract int getSeqCountWrapArround();
    abstract int getSeqInterruptionDelta();
}
