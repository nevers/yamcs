package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;

public class TmManagedParameters implements ManagedParameters {
    String physicalChannelName;
    int frameLength;
    boolean frameErroControlPresent;
    int fshLength; //0 means not present
    
    enum ServiceType {
        PACKET,
        VCA_SDU
    };
    
    Map<Integer, VcManagedParameters> vcParams = new HashMap<>();
    
    
    static class VcManagedParameters {
        int vcId;
        ServiceType service;
        int fshLength;//0 means not present
        
        //if type = M_PDU
        int maxPacketSize;
        String packetPreprocessorClassName;
        Map<String, Object> packetPreprocessorArgs;
    }
    public static TmManagedParameters parseConfig(YConfiguration config) {
        TmManagedParameters tmp = new TmManagedParameters();
        return tmp;
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
    public Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance, String linkName) {
        // TODO Auto-generated method stub
        return null;
    }
}
