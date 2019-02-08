package org.yamcs.tctm.ccsds;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameFactory.CcsdsFrameType;

/**
 * Handles incoming TM frames by distributing them to different VirtualChannelHandlers
 * 
 * @author nm
 *
 */
public class MasterChannelFrameHandler {
    CcsdsFrameType frameType;
    TransferFrameFactory frameFactory;
    Map<Integer, VirtualChannelHandler> handlers = new HashMap<>();
    int idleFrameCount;
    int frameCount;
    ManagedParameters params;
    
    String yamcsInstance;
    /**
     * Constructs based on the configuration
     * @param config
     */
    MasterChannelFrameHandler(String yamcsInstance, Map<String, Object> config) {
        frameType = YConfiguration.getEnum(config, "frameType", CcsdsFrameType.class);
        switch(frameType) {
        case AOS:
            AosManagedParameters amp = AosManagedParameters.parseConfig(config);
            frameFactory = new AosFrameFactory(amp);
            params = amp;
            break;
        case TM:
            TmManagedParameters tmp = TmManagedParameters.parseConfig(config);
        //    frameFactory = new TmFrameFactory(amp);
            params = tmp;
            break;
        case USLP:
            UslpManagedParameters ump = UslpManagedParameters.parseConfig(config);
          //  frameFactory = new UslpFrameFactory(amp);
            params = ump;
            default:
                throw new ConfigurationException("Unsupported frame type '"+frameType+"'");
        }
        handlers = params.createVcHandlers(yamcsInstance);
    }

    public void handleFrame(byte[] data, int offset, int length) throws TcTmException {
        TransferFrame frame = frameFactory.parse(data, offset, length);
        frameCount++;
        if(frame.containsOnlyIdleData()) {
            idleFrameCount++;
            return;
        }
        int vcid = frame.getVirtualChannelId();
        VirtualChannelHandler vch = handlers.get(vcid);
        if(vch == null) {
            throw new TcTmException("No handler for vcId: "+vcid);
        }
        vch.handle(frame);
    }

    public int getMaxFrameSize() {
        return params.getMaxFrameLength();
    }
    
    public int getMinFrameSize() {
        return params.getMinFrameLength();
    }
}
