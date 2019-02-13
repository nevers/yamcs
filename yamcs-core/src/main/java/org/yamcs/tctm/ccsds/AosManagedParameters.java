package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class AosManagedParameters implements ManagedParameters {
    enum ServiceType {
        /** Multiplexing Protocol Data Unit */
        M_PDU,
        /** Bitstream Protocol Data Unit */
        B_PDU,
        /** Virtual Channel Access Service Data Unit */
        VCA_SDU,
        /** IDLE frames are those with vcId = 63 */
        IDLE
    };

    final static int VCID_IDLE = 63;

    String physicalChannelName;
    int frameLength;

    int spacecraftId;
    boolean frameHeaderErrorControlPresent;
    int insertZoneLength; // 0 means insert zone not present
    boolean frameErroControlPresent;
    Map<Integer, VcManagedParameters> vcParams = new HashMap<>();

    static class VcManagedParameters {
        int vcId;
        ServiceType service;
        boolean ocfPresent;
        //if set to true, the encapsulation packets sent to the preprocessor will be without the encapsulation header(CCSDS 133.1-B-2)
        boolean stripEncapsulationHeader;

        // if service = M_PDU
        int maxPacketLength;
        String packetPreprocessorClassName;
        Map<String, Object> packetPreprocessorArgs;
        final YConfiguration config;

        public VcManagedParameters(YConfiguration config) {
            this.config = config;
            
            vcId = config.getInt("vcId");
            if (vcId < 0 || vcId > 63) {
                throw new ConfigurationException("Invalid vcId: " + vcId);
            }
            service = config.getEnum("service", ServiceType.class);
            if (vcId == VCID_IDLE && service != ServiceType.IDLE) {
                throw new ConfigurationException(
                        "vcid " + VCID_IDLE + " is reserved for IDLE frames (please set service: IDLE)");
            }
            
            ocfPresent = config.getBoolean("ocfPresent");
            if (service == ServiceType.M_PDU) {
                maxPacketLength = config.getInt("maxPacketLength");
                if (maxPacketLength < 7) {
                    throw new ConfigurationException("invalid maxPacketLength: " + maxPacketLength);
                }
            }
            packetPreprocessorClassName = config.getString("packetPreprocessorClassName");
            if(config.containsKey("packetPreprocessorArgs")) {
                packetPreprocessorArgs = config.getMap("packetPreprocessorArgs");
            }
            stripEncapsulationHeader = config.getBoolean("stripEncapsulationHeader", false);
        }

        VcManagedParameters() {
            config = YConfiguration.emptyConfig();
        }
    }

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
            VcManagedParameters vmp = new VcManagedParameters(yc);
            if (amp.vcParams.containsKey(vmp.vcId)) {
                throw new ConfigurationException("duplicate configuration of vcId " + vmp.vcId);
            }
            amp.vcParams.put(vmp.vcId, vmp);
        }

        if (!amp.vcParams.containsKey(VCID_IDLE)) {
            VcManagedParameters vmp = new VcManagedParameters();
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
        for (Map.Entry<Integer, VcManagedParameters> me : vcParams.entrySet()) {
            VcManagedParameters vmp = me.getValue();
            switch (vmp.service) {
            case B_PDU:
                throw new UnsupportedOperationException("B_PDU not supported (TODO)");
            case M_PDU:
                VirtualChannelPacketHandler vcph = new VirtualChannelPacketHandler(yamcsInstance, linkName+".vc"+vmp.vcId, vmp);
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
}
