package org.yamcs.tctm.ccsds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.rs.ReedSolomonException;
import org.yamcs.tctm.TcTmException;
import org.yamcs.tctm.ccsds.AosManagedParameters.ServiceType;
import org.yamcs.tctm.ccsds.AosManagedParameters.VcManagedParameters;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr;
import org.yamcs.tctm.ccsds.error.AosFrameHeaderErrorCorr.DecoderResult;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;
import org.yamcs.utils.ByteArrayUtils;

public class AosFrameDecoder implements TransferFrameDecoder {
    AosManagedParameters aosParams;
    CrcCciitCalculator crc;
    static Logger log = LoggerFactory.getLogger(TransferFrameDecoder.class.getName());
    
    public AosFrameDecoder(AosManagedParameters aosParams) {
        this.aosParams = aosParams;
        if (aosParams.frameErroControlPresent) {
            crc = new CrcCciitCalculator();
        }
    }

    @Override
    public TransferFrame decode(byte[] data, int offset, int length) throws TcTmException {
        log.trace("decoding frame buf length: {}, dataOffset: {} , dataLength: {}", data.length, offset, length);
        
        if (length != aosParams.frameLength) {
            throw new TcTmException("Bad frame length " + length + "; expected " + aosParams.frameLength);
        }
        if (aosParams.frameErroControlPresent) {
            length -=2;
            int c1 = crc.compute(data, offset, length);
            int c2 = ByteArrayUtils.decodeShort(data, offset + length);
            if (c1 != c2) {
                throw new CorruptedFrameException("Bad CRC computed: " + c1 + " in the frame: " + c2);
            }
        }
        int gvcid;
        int dataOffset = offset + 6;
        int dataLength = length - 6;
        if (aosParams.frameHeaderErrorControlPresent) {
            try {
                DecoderResult dr = AosFrameHeaderErrorCorr.decode(ByteArrayUtils.decodeShort(data, offset),
                        data[offset + 5], ByteArrayUtils.decodeShort(data, offset + 6));
                gvcid = dr.gvcid;
            } catch (ReedSolomonException e) {
                throw new CorruptedFrameException("Failed to Reed-Solomon verify/correct the AOS frame header fields");
            }
            dataOffset += 2;
            dataLength -= 2;
        } else {
            gvcid = ByteArrayUtils.decodeShort(data, offset);
        }

        int vn = gvcid >> 14;
        if (vn != 1) {
            throw new TcTmException("Invalid AOS frame version number " + vn + "; expected " + 1);
        }
        int masterChannelId = gvcid >> 6;
        int virtualChannelId = gvcid & 0x3F;

        VcManagedParameters vmp = aosParams.vcParams.get(virtualChannelId);
        if (vmp == null) {
            throw new TcTmException("Received data for unknown VirtualChannel " + virtualChannelId);
        }

        dataOffset += aosParams.insertZoneLength;
        dataLength -= aosParams.insertZoneLength;

        AosTransferFrame atf = new AosTransferFrame(data, masterChannelId, virtualChannelId);
        if (vmp.ocfPresent) {
            dataLength -= 4;
            atf.setOcf(ByteArrayUtils.decodeInt(data, dataOffset + dataLength));
        }

        if (vmp.service == ServiceType.M_PDU) {
            int fhp = ByteArrayUtils.decodeShort(data, dataOffset) & 0x7FF;
            dataOffset += 2;
            dataLength -= 2;
            if (fhp == 0x7FF) {
                fhp = -1;
            } else {
                fhp += dataOffset;
                if (fhp > dataLength) {
                    throw new TcTmException("First header pointer in the M_PDU part of AOS frame is outside the data "
                            + fhp + ">" + dataLength);
                }
            }
            atf.setFirstHeaderPointer(fhp);
        }

        atf.setDataStart(dataOffset);
        atf.setDataEnd(dataOffset + dataLength);
        return atf;
    }

}
