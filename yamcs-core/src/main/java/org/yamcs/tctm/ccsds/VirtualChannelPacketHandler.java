package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.slf4j.Logger;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.LoggingUtils;

import com.google.common.util.concurrent.AbstractService;

/**
 * Handles packets from one VC
 * 
 * @author nm
 *
 */
public class VirtualChannelPacketHandler extends AbstractService implements TmPacketDataLink, VirtualChannelHandler {
    TmSink tmSink;
    private long numPackets;
    volatile boolean disabled = false;
    volatile Status status = Status.UNAVAIL;
    int lastFrameSeq = -1;
    EventProducer eventProducer;
    int packetLostCount;
    private final Logger log;
    int maxPacketLength;
    PacketDecoder packetDecoder;
    long idleFrameCount = 0;
    PacketPreprocessor packetPreproc;
    
    public VirtualChannelPacketHandler(String yamcsInstance, String packetPreprocessorClassName, Map<String, Object> packetPreprocessorArgs) {
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        packetDecoder = new PacketDecoder(maxPacketLength, p -> sendPacket(p));
    }


    public void handle(TransferFrame frame) {
        if (frame.containsOnlyIdleData()) {
            idleFrameCount++;
            return;
        }

        int dataStart = frame.getDataStart();
        int sduStart = frame.getFirstSduStart();
        int dataEnd = frame.getDataEnd();
        byte[] data = frame.getData();

        try {
            int frameLoss = frame.lostFramesCount(lastFrameSeq);
            if (packetDecoder.hasIncompletePacket()) {
                if (frameLoss != 0) {
                    log.warn("Incomplete SDU dropped because of frame loss");
                    packetDecoder.reset();
                } else {
                    if (sduStart != -1) {
                        packetDecoder.process(data, dataStart, sduStart);
                    } else {
                        packetDecoder.process(data, dataStart, dataEnd);
                    }
                }
            }
            if (sduStart != -1) {
                if (packetDecoder.hasIncompletePacket()) {
                    eventProducer.sendWarning("Incomplete SDU decoded when reaching the beginning of another SDU");
                    packetDecoder.reset();
                }
                packetDecoder.process(data, sduStart, dataEnd);
            }
        } catch (TcTmException e) {
            packetDecoder.reset();
            eventProducer.sendWarning(e.toString());
        }
    }
    
    private void sendPacket(byte[] p) {
        PacketWithTime pwt = packetPreproc.process(p);
        if(pwt!=null) {
            tmSink.processPacket(pwt);
        }
    }

    @Override
    public Status getLinkStatus() {
        return status;
    }

    @Override
    public String getDetailedStatus() {
        return null;
    }

    @Override
    public void enable() {
        this.disabled = false;
    }

    @Override
    public void disable() {
        this.disabled = true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return numPackets;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
