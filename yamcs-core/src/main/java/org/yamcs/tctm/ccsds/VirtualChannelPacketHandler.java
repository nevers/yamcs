package org.yamcs.tctm.ccsds;

import java.io.IOException;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.tctm.PacketPreprocessor;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.TmPacketDataLink;
import org.yamcs.tctm.TmSink;
import org.yamcs.tctm.ccsds.AosManagedParameters.VcManagedParameters;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.YObjectLoader;

/**
 * Handles packets from one VC
 * 
 * @author nm
 *
 */
public class VirtualChannelPacketHandler implements TmPacketDataLink, VirtualChannelHandler {
    TmSink tmSink;
    private long numPackets;
    volatile boolean disabled = false;
    int lastFrameSeq = -1;
    EventProducer eventProducer;
    int packetLostCount;
    private final Logger log;
    PacketDecoder packetDecoder;
    long idleFrameCount = 0;
    PacketPreprocessor packetPreprocessor;
    final String name;
    final VcManagedParameters vmp;
    
    AggregatedDataLink parent;

    public VirtualChannelPacketHandler(String yamcsInstance, String name, VcManagedParameters vmp) {
        this.vmp = vmp;
        this.name = name;
        
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10000);
        log = LoggingUtils.getLogger(this.getClass(), yamcsInstance);
        
        packetDecoder = new PacketDecoder(vmp.maxPacketLength, p -> handlePacket(p));
        packetDecoder.stripEncapsulationHeader(vmp.stripEncapsulationHeader);
        
        try {
            if (vmp.packetPreprocessorArgs != null) {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance,
                        vmp.packetPreprocessorArgs);
            } else {
                packetPreprocessor = YObjectLoader.loadObject(vmp.packetPreprocessorClassName, yamcsInstance);
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packet preprocessor", e);
            throw e;
        } catch (IOException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw new ConfigurationException(e);
        }
    }

    public void handle(TransferFrame frame) {
        log.warn("Processing packet frame VC: {} SEQ: {}, FHP: {}", frame.getVirtualChannelId(), frame.getVcFrameSeq(), frame.getFirstHeaderPointer());
        if (frame.containsOnlyIdleData()) {
            idleFrameCount++;
            return;
        }

        int dataStart = frame.getDataStart();
        int sduStart = frame.getFirstHeaderPointer();
        int dataEnd = frame.getDataEnd();
        byte[] data = frame.getData();

        System.out.println(StringConverter.arrayToHexString(data));
        System.out.println("dataStart: "+dataStart);
        System.out.println("sduStart: "+sduStart);
        System.out.println("dataEnd: "+dataEnd);
        System.out.println("hasIncomp: "+packetDecoder.hasIncompletePacket());
        
        
        try {
            int frameLoss = frame.lostFramesCount(lastFrameSeq);
            if (packetDecoder.hasIncompletePacket()) {
                if (frameLoss != 0) {
                    log.warn("Incomplete SDU dropped because of frame loss");
                    packetDecoder.reset();
                } else {
                    if (sduStart != -1) {
                        packetDecoder.process(data, dataStart, sduStart-dataStart);
                    } else {
                        packetDecoder.process(data, dataStart, dataEnd-dataStart);
                    }
                }
            }
            System.out.println("2 dataStart: " + dataStart + " sduStart: " + sduStart);
            if (sduStart != -1) {
                if (packetDecoder.hasIncompletePacket()) {
                    eventProducer.sendWarning("Incomplete SDU decoded when reaching the beginning of another SDU");
                    packetDecoder.reset();
                }
                packetDecoder.process(data, sduStart, dataEnd-sduStart);
            }
        } catch (TcTmException e) {
            packetDecoder.reset();
            eventProducer.sendWarning(e.toString());
        }
    }

    private void handlePacket(byte[] p) {
        System.out.println("----------------------- handling packet of size "+p.length);
        numPackets++;
        PacketWithTime pwt = packetPreprocessor.process(p);
        if (pwt != null) {
            tmSink.processPacket(pwt);
        }
    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
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
    public YConfiguration getConfig() {
        return vmp.config;
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public AggregatedDataLink getParent() {
        return parent;
    }
    
    @Override
    public void setParent(AggregatedDataLink parent) {
        this.parent = parent;
    }
}
