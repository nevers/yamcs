package org.yamcs.simulation.simulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.ByteArrayUtils;

import com.google.common.util.concurrent.AbstractScheduledService;

/**
 * Link implementing the TM frames using 
 *  AOS CCSDS 732.0-B-3 
 *  TM CCSDS 132.0-B-2 (TODO)
 *  USLP CCSDS 732.1-B-1 (TODO)
 *  
 *  
 * Sends frames of predefined size at a configured frequency. If there is no data to send, it sends idle frames.
 * 
 * 
 * @author nm
 *
 */
public class UdpFrameLink extends AbstractScheduledService {
    final String name;
    final String host;
    final int port;
    final int frameSize;

    DatagramSocket socket;
    static final int NUM_VC = 3;
    static final int MASTER_CHANNEL_ID = 0x1AB;
    final int framesPerSec;
    
    VcSender[] senders = new VcSender[NUM_VC]; 
    
    private static final Logger log = LoggerFactory.getLogger(UdpFrameLink.class);

    int lastVcSent; // switches between 0 and 1 so we don't send always from the same vc

    public UdpFrameLink(String name, String host, int port, int frameSize, int framesPerSec) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.frameSize = frameSize;
        this.framesPerSec = framesPerSec;

        for (int i = 0; i < NUM_VC; i++) {
            senders[i] = new VcSender(i, frameSize);
        }
    }

    @Override
    protected void startUp() throws Exception {
        socket = new DatagramSocket();
    }
    
    
    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, (long) (1e6 / framesPerSec), TimeUnit.MICROSECONDS);
    }

    @Override
    protected void runOneIteration() throws Exception {
        // send one frame with packets from vc 0 and 1 having priority - when they are empty send from vc 2
        int vc = (lastVcSent + 1) & 1;
        if (senders[vc].sendFrame()) {
            lastVcSent = vc;
            return;
        }
        vc = (vc + 1) & 1;
        
        if (senders[vc].sendFrame()) {
            lastVcSent = vc;
            return;
        }
        if (senders[2].sendFrame()) {
            return;
        }
        
        sendIdle();
    }

    // send an idle frame
    private void sendIdle() {
        System.out.println("TODO send idle frame");
    }

    /**
     * send packet on virtual channel
     * it puts it on the corresponding queue from where will be send in the other thread
     * 
     * @param vcId
     * @param packet
     */
    public void sendPacket(int vcId, byte[] packet) {
        VcSender s = senders[vcId];
        if (!s.queue.offer(packet)) {
            log.warn("dropping packet for virtual channel {} because the queue is full", vcId);
        }
    }

    
    class VcSender {
        final byte[] data;
        static final int HDR_SIZE = 8;
        int offset = HDR_SIZE;
        int vcSeqCount = 0;

        ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(10);
        
        byte[] pendingPacket;
        int pendingPacketOffset;

        public VcSender(int vcId, int frameSize) {
            if ((vcId < 0) || (vcId > 63)) {
                throw new IllegalArgumentException("Invalid virtual channel id " + vcId);
            }
            this.data = new byte[frameSize];
            ByteArrayUtils.encodeShort((MASTER_CHANNEL_ID << 6) + vcId, data, 0);

        }

        /**
         * Send data from the queue if any and return true if something has been sent
         * 
         * @param q
         * @return
         * @throws IOException
         */
        boolean sendFrame() throws IOException {
            if (pendingPacket != null) {
                copyPendingToBuffer();
                if (pendingPacket != null) {// not yet fully copied but the frame is full
                    writeFhp(0x7FF);
                    sendToSocket();
                    return true;
                }
            }
            boolean first = true;
            while ((pendingPacket = queue.poll()) != null) {
                if (first) {
                    writeFhp(offset - HDR_SIZE);
                } else {
                    first = false;
                }
                pendingPacketOffset = 0;
                copyPendingToBuffer();
                if (pendingPacket != null) {// not yet fully copied but the frame is full
                    sendToSocket();
                    return true;
                }
            }
            // if the frame is at least half full, fill it up with an idle packet and send it
            if (offset > data.length / 2) {
                fillIdlePacket();
                sendToSocket();
                return true;
            }
            return false;
        }

        private void fillIdlePacket() {
            int n = data.length - offset;
            if (n == 0) {
                return;
            } else if (n == 1) {
                
            }
        }

        private void writeFhp(int fhp) {
            ByteArrayUtils.encodeShort(fhp, data, HDR_SIZE - 2);
        }

        void copyPendingToBuffer() {
            int length = Math.min(pendingPacket.length - pendingPacketOffset, data.length - offset);
            System.arraycopy(pendingPacket, pendingPacketOffset, data, offset, length);
            offset += length;
            pendingPacketOffset += length;
            if (length == pendingPacketOffset) {
                pendingPacket = null;
            }
        }

        void sendToSocket() throws IOException {
            // set the frame sequence count
            data[2] = (byte) (vcSeqCount >>> 16);
            ByteArrayUtils.encodeShort(vcSeqCount & 0xFFFF, data, 3);
            data[5] = (byte) ((1 << 6) + (vcSeqCount >> 24) & 0xF);

            socket.send(new DatagramPacket(data, data.length));
            offset = HDR_SIZE;
            vcSeqCount++;
        }

        boolean isEmpty() {
            return offset == -1;
        }

        void reset() {
            vcSeqCount++;
        }
    }
}
