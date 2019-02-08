package org.yamcs.tctm.ccsds;

import java.util.function.Consumer;

import org.yamcs.tctm.PacketTooLongException;
import org.yamcs.tctm.TcTmException;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Receives data chunk by chunk and assembles it into packets. Two types of packets are supported:
 * <p>
 * <strong>Space Packet Protocol CCSDS 133.0-B-1 September 2003.</strong>
 * <p>
 * The first 3 bits of these packets are 000.
 * <p>
 * The header is 6 bytes and the last two bytes of the header represent the
 * size of the packet (including the header) - 7.
 * <p>
 * The minimum packet length is 7 bytes.
 * 
 * <p>
 * <p>
 * <strong>Encapsulation Service. CCSDS 133.1-B-2. October 2009.</strong>
 * <p>
 * The first 3 bits of these packets are 111.
 * <p>
 * The minimum packet length is 1 byte.
 * <p>
 * Depending on the last 2 bits of the first byte, the size of the header can be 1,2,4 or 8 bytes with the length of the
 * packet read from the last 0,1,2 or 4 header bytes respectively
 *
 * <p>
 * <p>
 * The two types can be both present on the same stream.
 * 
 * <p>
 * The objects of this class can processes one "stream" at a time and they are not thread safe!
 * 
 * @author nm
 *
 */
public class PacketDecoder {
    private final int maxPacketLength;

    // length of the encapsulated packet header based on the last 2 bits of the first byte
    static final byte[] ENCAPSULATION_HEADER_LENGTH = { 1, 2, 4, 8 };

    static final int PACKET_VERSION_CCSDS = 0;
    static final int PACKET_VERSION_ENCAPSULATION = 7;

    // the actual header length (valid when the headerOffset>0)
    private int headerLength;
    // max header length for the encapsulation packets is 8 bytes
    final byte[] header = new byte[8];

    private int headerOffset;

    // the packetOffset and packet will be valid when the header is completely read (i.e.
    // headerOffset==header.length==lengthFieldEndOffset)
    private int packetOffset;
    private byte[] packet;

    final Consumer<byte[]> consumer;

    public PacketDecoder(int maxPacketLength, Consumer<byte[]> consumer) {
        this.maxPacketLength = maxPacketLength;
        this.consumer = consumer;
    }

    public void process(byte[] data, int offset, int length) throws TcTmException {
        while (length > 0) {
            if (headerOffset == 0) { // read the first byte of the header to know what kind of packet it is as well as
                                     // the legnth of the header
                byte d0 = data[offset];
                offset++;
                length--;
                headerLength = getHeaderLength(d0);
                if (headerLength == 1) {
                    // special case, encapsulation packet of size 1, send the packet and reset the header
                    consumer.accept(new byte[] { d0 });
                    headerOffset = 0;
                } else {
                    header[0] = d0;
                    headerOffset++;
                }
            } else if (headerOffset < headerLength) { // reading the header
                int n = Math.min(length, headerLength - headerOffset);
                System.arraycopy(data, offset, header, headerOffset, n);
                offset += n;
                headerOffset += n;
                length -= n;
                if (headerOffset == headerLength) {
                    allocatePacket();
                }
            } else {// reading the packet
                int n = Math.min(packet.length - packetOffset, length);
                System.arraycopy(data, offset, packet, packetOffset, n);
                offset += n;
                packetOffset += n;
                length -= n;
                if (packetOffset == packet.length) {
                    consumer.accept(packet);
                    packet = null;
                    headerOffset = 0;
                }
            }
        }
    }

    // get headerLength based on the first byte of the packet
    private static int getHeaderLength(byte b0) throws UnsupportedPacketVersionException {
        int pv = (b0 & 0xFF) >>> 5;
        if (pv == PACKET_VERSION_CCSDS) {
            return 6;
        } else if (pv == PACKET_VERSION_ENCAPSULATION) {
            return ENCAPSULATION_HEADER_LENGTH[b0 & 3];
        } else {
            throw new UnsupportedPacketVersionException(pv);
        }
    }

    private void allocatePacket() throws TcTmException {
        int packetLength = getPacketLength(header);
        if (packetLength > maxPacketLength) {
            throw new PacketTooLongException(maxPacketLength, packetLength);
        } else if (packetLength < headerLength) {
            throw new TcTmException("Invalid packet length " + packetLength + " (it is smaller than the header length)");
        }
        packet = new byte[packetLength];
        System.arraycopy(header, 0, packet, 0, headerLength);
        if (packetLength == headerLength) {
            consumer.accept(packet);
            headerOffset = 0;
        } else {
            packetOffset = headerLength;
        }
    }

    // decodes the packet length from the header
    private static int getPacketLength(byte[] header) throws UnsupportedPacketVersionException {
        int h0 = header[0] & 0xFF;
        int pv = h0 >>> 5;
        if (pv == PACKET_VERSION_CCSDS) {
            return 7 + ByteArrayUtils.decodeShort(header, 4);
        } else if (pv == PACKET_VERSION_ENCAPSULATION) {
            int l = h0 & 3;
            if (l == 0) {
                return 1;
            } else if (l == 1) {
                return header[1] & 0xFF;
            } else if (l == 2) {
                return ByteArrayUtils.decodeShort(header, 2);
            } else {
                return ByteArrayUtils.decodeInt(header, 4);
            }
        } else {
            throw new UnsupportedPacketVersionException(pv);
        }
    }

    /**
     * Removes a partial packet if any
     */
    public void reset() {
        headerOffset = 0;
        packet = null;
    }

    /**
     * 
     * @return true of the decoder is in the middle of a packet decoding
     */
    public boolean hasIncompletePacket() {
        return (headerOffset > 0) && ((headerOffset < headerLength) || (packetOffset < packet.length));
    }

}
