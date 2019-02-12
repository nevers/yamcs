package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.yamcs.YConfiguration;

public class UslpManagedParameters implements ManagedParameters {
    String physicalChannelName;
    boolean fixedLength; //or variable length
    int frameLength; //if fixedLength=true
    int frameVersionNumber = 12;
    
    int insertZoneLength; //0 means not present
    int frameErrorControlLength;//0 means not present, otherwise 2 or 4
    boolean generateOidFrame;
    
    int fshLength; //0 means not present
    boolean ocfPresent;
    int spacecraftId;
    
   
    
    enum COPType {COP_1, COP_P, NONE};
    
    static class VcManagedParameters {
        int vcId;
        COPType copInEffect;
        boolean fixedLength; //or variable length
        int vcCountLengthForSeqControlQos;
        int vcCountLengthForExpeditedQos;
        int truncatedTransferFrameLength;
        
        boolean ocfAllowed; //only if fixedLength = true
        boolean ocfRequired; //only if fixedLength = true
        
    }
    enum SduType {PACKET, MAPA_SDU, OCTET_STREAM }
    static class MapManagedParameters {
        int mapId;
        SduType sduType;
        int maxPacketLength;
    }
    public static UslpManagedParameters parseConfig(YConfiguration config) {
        // TODO Auto-generated method stub
        return null;
    }
    
    
    @Override
    public int getMaxFrameLength() {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public int getMinFrameLength() {
        // TODO Auto-generated method stub
        return 0;
    }
    @Override
    public Map<Integer, VirtualChannelHandler> createVcHandlers(String yamcsInstance, String linkName) {
        // TODO Auto-generated method stub
        return null;
    }
}
