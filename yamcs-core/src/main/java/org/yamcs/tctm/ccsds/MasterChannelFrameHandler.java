package org.yamcs.tctm.ccsds;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.TransferFrameDecoder.CcsdsFrameType;

/**
 * Handles incoming TM frames by distributing them to different VirtualChannelHandlers
 * 
 * @author nm
 *
 */
public class MasterChannelFrameHandler {
    CcsdsFrameType frameType;
    TransferFrameDecoder frameFactory;
    Map<Integer, VirtualChannelHandler> handlers = new HashMap<>();
    int idleFrameCount;
    int frameCount;
    ManagedParameters params;
    
    String yamcsInstance;
    /**
     * Constructs based on the configuration
     * @param config
     */
    public MasterChannelFrameHandler(String yamcsInstance, String linkName, YConfiguration config) {
        frameType = config.getEnum("frameType", CcsdsFrameType.class);
        switch(frameType) {
        case AOS:
            AosManagedParameters amp = AosManagedParameters.parseConfig(config);
            frameFactory = new AosFrameDecoder(amp);
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
        handlers = params.createVcHandlers(yamcsInstance, linkName);
    }

    public void handleFrame(byte[] data, int offset, int length) throws TcTmException {
        TransferFrame frame = frameFactory.decode(data, offset, length);
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

    public Collection<VirtualChannelHandler> getVcHandlers() {
        return handlers.values();
    }
}
