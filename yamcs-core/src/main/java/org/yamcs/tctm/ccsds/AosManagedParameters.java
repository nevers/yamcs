package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class AosManagedParameters extends ManagedParameters {
    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        PACKET,
        /** Bitstream Protocol Data Unit */
        B_PDU,
        /** Virtual Channel Access Service Data Unit */
        VCA_SDU,
        /** IDLE frames are those with vcId = 63 */
        IDLE
    };

    final static int VCID_IDLE = 63;

    int frameLength;
   
    boolean frameHeaderErrorControlPresent;
    int insertZoneLength; // 0 means insert zone not present
    boolean frameErroControlPresent;
    Map<Integer, AosVcManagedParameters> vcParams = new HashMap<>();


    public static AosManagedParameters parseConfig(YConfiguration config) {
        AosManagedParameters amp = new AosManagedParameters();

        if (config.containsKey("physicalChannelName")) {
            amp.physicalChannelName = config.getString("physicalChannelName");
        }
        amp.frameLength = config.getInt("frameLength");
        if (amp.frameLength < 8 || amp.frameLength > 0xFFFF) {
            throw new ConfigurationException("Invalid frame length " + amp.frameLength);
        }
        amp.frameHeaderErrorControlPresent = config.getBoolean("frameHeaderErrorControlPresent");
        amp.frameErroControlPresent = config.getBoolean("frameErroControlPresent");
        amp.insertZoneLength = config.getInt("insertZoneLength");

        if (amp.insertZoneLength < 0 || amp.insertZoneLength > amp.frameLength - 6) {
            throw new ConfigurationException("Invalid insert zone length " + amp.insertZoneLength);
        }

        List<YConfiguration> l = config.getConfigList("virtualChannels");
        for (YConfiguration yc : l) {
            AosVcManagedParameters vmp = new AosVcManagedParameters(yc);
            if (amp.vcParams.containsKey(vmp.vcId)) {
                throw new ConfigurationException("duplicate configuration of vcId " + vmp.vcId);
            }
            amp.vcParams.put(vmp.vcId, vmp);
        }

        if (!amp.vcParams.containsKey(VCID_IDLE)) {
            AosVcManagedParameters vmp = new AosVcManagedParameters();
            vmp.vcId = 63;
            vmp.service = ServiceType.IDLE;
            vmp.ocfPresent = false;
            amp.vcParams.put(VCID_IDLE, vmp);
        }
        return amp;
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
        Map<Integer, VirtualChannelHandler> m = new HashMap<>();
        for (Map.Entry<Integer, AosVcManagedParameters> me : vcParams.entrySet()) {
            AosVcManagedParameters vmp = me.getValue();
            switch (vmp.service) {
            case B_PDU:
                throw new UnsupportedOperationException("B_PDU not supported (TODO)");
            case PACKET:
                VirtualChannelPacketHandler vcph = new VirtualChannelPacketHandler(yamcsInstance,
                        linkName + ".vc" + vmp.vcId, vmp);
                m.put(vmp.vcId, vcph);
                break;
            case VCA_SDU:
                throw new UnsupportedOperationException("VCA_SDU not supported (TODO)");
            case IDLE:
                m.put(vmp.vcId, new IdleFrameHandler());
                break;
            }
        }
        return m;
    }
    
    
    
    
    
    
    
    static class AosVcManagedParameters extends VcManagedParameters {
        ServiceType service;

        public AosVcManagedParameters(YConfiguration config) {
            super(config);

            if (vcId < 0 || vcId > 63) {
                throw new ConfigurationException("Invalid vcId: " + vcId+". Allowed values are from 0 to 63.");
            }
            service = config.getEnum("service", ServiceType.class);
            if (vcId == VCID_IDLE && service != ServiceType.IDLE) {
                throw new ConfigurationException(
                        "vcid " + VCID_IDLE + " is reserved for IDLE frames (please set service: IDLE)");
            }

            ocfPresent = config.getBoolean("ocfPresent");
            if (service == ServiceType.PACKET) {
                parsePacketConfig();
               
            }
        }

        AosVcManagedParameters() {
            super(YConfiguration.emptyConfig());
        }
    }

}
