package org.yamcs.tctm.ccsds;
/**
 * Common properties for the three supported transfer frame types AOS/TM/USLP
 * 
 * @author nm
 *
 */
public abstract class AbstractTransferFrame implements TransferFrame {
    final protected int masterChannelId;
    final protected int virtualChannelId;
    final protected byte[] data;
    
    long vcFrameSeq;
    
    int dataStart;
    int dataEnd;
    int ocf;
    int fps;
    
    public AbstractTransferFrame(byte[] data, int masterChannelId, int virtualChannelId) {
        this.data = data;
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

    void setDataStart(int ds) {
        this.dataStart = ds;
    }
    
    
    @Override
    public int getDataStart() {
        return dataStart;
    }

    @Override
    public int getFirstHeaderPointer() {
        return fps;
    }

    void setFirstHeaderPointer(int fps) {
        this.fps = fps;
    }
    
    void setDataEnd(int offset) {
        this.dataEnd = offset;
    }

    @Override
    public int getDataEnd() {
        return dataEnd;
    }

    public void setOcf(int ocf) {
        this.ocf = ocf;
    }

    
    abstract int getSeqCountWrapArround();
    abstract int getSeqInterruptionDelta();
}
