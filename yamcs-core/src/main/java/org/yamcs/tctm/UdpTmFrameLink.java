package org.yamcs.tctm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.ccsds.MasterChannelFrameHandler;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Receives telemetry fames via UDP. One UDP datagram = one TM frame.
 * 
 * 
 * @author nm
 *
 */
public class UdpTmFrameLink extends AbstractExecutionThreadService implements AggregatedDataLink {
    private volatile int validDatagramCount = 0;
    private volatile int invalidDatagramCount = 0;
    private volatile boolean disabled = false;

    private DatagramSocket tmSocket;
    private int port;

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());
    final DatagramPacket datagram;
    final int maxLength;
    String packetPreprocessorClassName;
    Object packetPreprocessorArgs;
    String yamcsInstance;
    String name;
    MasterChannelFrameHandler frameHandler;
    EventProducer eventProducer;
    
    /**
     * Creates a new UDP Frame Data Link
     * 
     * @throws ConfigurationException
     *             if port is not defined in the configuration
     */
    public UdpTmFrameLink(String instance, String name, Map<String, Object> args) throws ConfigurationException {
        this.yamcsInstance = instance;
        this.name = name;
        port = YConfiguration.getInt(args, "port");
        maxLength = 1500;
        datagram = new DatagramPacket(new byte[maxLength], maxLength);
    }

    @Override
    public void startUp() throws IOException {
        tmSocket = new DatagramSocket(port);
    }

    @Override
    public void shutDown() {
        tmSocket.close();
    }

    @Override
    public void run() {
        while (isRunning()) {
            try {
                tmSocket.receive(datagram);
                int length = datagram.getLength();
                if (length < frameHandler.getMinFrameSize()) {
                    eventProducer.sendWarning("Error processing frame: size "+length+" shorter than minimum allowed "+frameHandler.getMinFrameSize());    
                } else if (length > frameHandler.getMaxFrameSize()) {
                    eventProducer.sendWarning("Error processing frame: size "+length+" longer than maximum allowed "+frameHandler.getMaxFrameSize());
                } else {
                    frameHandler.handleFrame(datagram.getData(), datagram.getOffset(), length);
                }
            } catch (IOException e) {
                log.warn("exception {} thrown when reading from the UDP socket at port {}", port, e);
            } catch (TcTmException e) {
                eventProducer.sendWarning("Error processing frame: "+e.toString());
            }
            while (disabled) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public Status getLinkStatus() {
        return disabled ? Status.DISABLED : Status.OK;
    }

    /**
     * returns statistics with the number of datagram received and the number of invalid datagrams
     */
    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return "DISABLED";
        } else {
            return String.format("OK (%s) %nValid datagrams received: %d%nInvalid datagrams received: %d",
                    port, validDatagramCount, invalidDatagramCount);
        }
    }

    /**
     * Sets the disabled to true such that getNextPacket ignores the received datagrams
     */
    @Override
    public void disable() {
        disabled = true;
    }

    /**
     * Sets the disabled to false such that getNextPacket does not ignore the received datagrams
     */
    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataInCount() {
        return validDatagramCount;
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }
}
