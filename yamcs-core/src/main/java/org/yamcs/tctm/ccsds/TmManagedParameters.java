package org.yamcs.tctm.ccsds;

import java.util.Map;

public class TmManagedParameters implements ManagedParameters {
    String physicalChannelName;
    int frameLength;
    int frameVersionNumber = 2;
    boolean frameErroControlPresent;
    int fshLength; //0 means not present
    boolean ocfPresent;
    
    enum FrameType {PACKET, VCA_SDU }
    static class VcManagedParameters {
        int vcId;
        FrameType type;
        int fshLength;//0 means not present
        boolean ocfPresent;
        
        //if type = M_PDU
        int maxPacketSize;
        String packetPreprocessorClassName;
        Map<String, Object> packetPreprocessorArgs;
    }
    public static TmManagedParameters parseConfig(Map<String, Object> config) {
        // TODO Auto-generated method stub
        return null;
    }
    @Override
    public int getMaxFrameLength() {
        return frameLength;
    }
    @Override
    public int getMinFrameLength() {
        return frameLength;
    }
    @Override
    public Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance) {
        // TODO Auto-generated method stub
        return null;
    }
}
