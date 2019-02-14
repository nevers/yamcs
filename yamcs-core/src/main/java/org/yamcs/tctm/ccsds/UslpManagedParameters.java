package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.YConfiguration;

public class UslpManagedParameters extends ManagedParameters {
    enum FrameErrorCorrection {NONE, CRC16, CRC32};
    enum COPType {COP_1, COP_P, NONE};
    
    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        PACKET,
        /** IDLE frames are those with vcId = 63 */
        IDLE
    };
    int frameLength; //frame length if fixed or -1 if not fixed
    int frameVersionNumber = 12;
    
    int insertZoneLength; //0 means not present
    boolean generateOidFrame;
    
    int fshLength; //0 means not present
    boolean ocfPresent;
    FrameErrorCorrection errorCorrection;
    Map<Integer, UslpVcManagedParameters> vcParams = new HashMap<>();

    
    static class UslpVcManagedParameters extends VcManagedParameters {
        public UslpVcManagedParameters(YConfiguration config) {
            super(config);
        }
        ServiceType service;

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
