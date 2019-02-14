package org.yamcs.tctm.ccsds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.UslpManagedParameters.FrameErrorCorrection;
import org.yamcs.tctm.ccsds.UslpManagedParameters.ServiceType;
import org.yamcs.tctm.ccsds.UslpManagedParameters.UslpVcManagedParameters;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

/**
 * Decodes frames as per CCSDS 732.0-B-3
 * 
 * @author nm
 *
 */
public class UslpFrameDecoder implements TransferFrameDecoder {
    UslpManagedParameters uslpParams;
    CrcCciitCalculator crc;
    static Logger log = LoggerFactory.getLogger(TransferFrameDecoder.class.getName());

    public UslpFrameDecoder(UslpManagedParameters uslpParams) {
        this.uslpParams = uslpParams;
        if (uslpParams.errorCorrection == FrameErrorCorrection.CRC16) {
            crc = new CrcCciitCalculator();
        } else if (uslpParams.errorCorrection == FrameErrorCorrection.CRC32) {
            // TODO crc =
        }
    }

    @Override
    public TransferFrame decode(byte[] data, int offset, int length) throws TcTmException {
        log.trace("decoding frame buf length: {}, dataOffset: {} , dataLength: {}", data.length, offset, length);

        if (uslpParams.frameLength!=-1 && length != uslpParams.frameLength) {
            throw new TcTmException("Bad frame length " + length + "; expected fixed length " + uslpParams.frameLength);
        }
        
        if (uslpParams.errorCorrection == FrameErrorCorrection.CRC16) {
            length -= 2;
            int c1 = crc.compute(data, offset, length);
            int c2 = ByteArrayUtils.decodeShort(data, offset + length);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + c1 + " in the frame: " + c2);
            }
        } else if (uslpParams.errorCorrection == FrameErrorCorrection.CRC32) {
            length -= 4;
            int c1 = crc.compute(data, offset, length);
            int c2 = ByteArrayUtils.decodeInt(data, offset + length);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + Integer.toUnsignedString(c1)
                        + " in the frame: " + Integer.toUnsignedString(c2));
            }
        }

        int dataOffset = offset + 4;
        int dataLength = length - 4;

        int f4b = ByteArrayUtils.decodeInt(data, offset);// first four bytes

        int vn = f4b >>> 28;
        if (vn != 12) {
            throw new TcTmException("Invalid USLP frame version number " + vn + "; expected " + 12);
        }
        int masterChannelId = f4b >>> 12;
        int virtualChannelId = (f4b >> 5) & 0x3F;
        int mapId = (f4b >> 1) & 0xF;
        boolean truncatedFrame = (f4b & 1) == 1;

        UslpVcManagedParameters vmp = uslpParams.vcParams.get(virtualChannelId);
        if (vmp == null) {
            throw new TcTmException("Received data for unknown VirtualChannel " + virtualChannelId);
        }

        if (truncatedFrame) {
            if (length != vmp.truncatedTransferFrameLength) {
                throw new TcTmException("Received truncated frame on VC " + virtualChannelId + " whose length ("
                        + length + ") does not match the configured truncatedTranferFrameLength("
                        + vmp.truncatedTransferFrameLength + ")");
            }

        } else {
            
            int encodedFrameLength = ByteArrayUtils.decodeShort(data, 4) + 1;
            if (encodedFrameLength != length - 1) {
                throw new TcTmException("Encoded frame length does not match received data length" + encodedFrameLength
                        + " != (" + length + "-1)");
            }
            byte b6 = data[offset+6];
            //bit 48 Bypass/Sequence Control Flag  - don't care for TM
            //bit 49 Protocol Control Command Flag  - don't care for TM
            boolean ocfPresent = ((b6>>3)&1)==1;
            if(ocfPresent) {
                dataLength-=4;
            }
            int vcfCountLength = b6&0x7;
            long vcfCount = ByteArrayUtils.decodeLong(data, offset+6) & ;
            
            dataOffset+=vcfCountLength;
            dataLength-=vcfCountLength;
            
            dataOffset += uslpParams.insertZoneLength;
            dataLength -= uslpParams.insertZoneLength;
        }

        UslpTransferFrame atf = new UslpTransferFrame(data, masterChannelId, virtualChannelId);
        atf.setVcFrameSeq(ByteArrayUtils.decode3Bytes(data, offset + 2));

        if (vmp.ocfPresent) {
            dataLength -= 4;
            atf.setOcf(ByteArrayUtils.decodeInt(data, dataOffset + dataLength));
        }

        if (vmp.service == ServiceType.PACKET) {
            int fhp = ByteArrayUtils.decodeShort(data, dataOffset) & 0x7FF;
            dataOffset += 2;
            dataLength -= 2;
            if (fhp == 0x7FF) {
                fhp = -1;
            } else {
                fhp += dataOffset;
                if (fhp > dataLength) {
                    throw new TcTmException(
                            "First header pointer in the date header part of USLP frame is outside the data "
                                    + (fhp - dataOffset) + ">" + (dataLength - dataOffset));
                }
            }
            atf.setFirstHeaderPointer(fhp);
        }

        atf.setDataStart(dataOffset);
        atf.setDataEnd(dataOffset + dataLength);
        return atf;
    }

}
