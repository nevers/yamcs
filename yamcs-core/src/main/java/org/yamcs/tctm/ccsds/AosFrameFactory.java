package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

public class AosFrameFactory implements TransferFrameFactory {
    AosManagedParameters aosParams;

    public AosFrameFactory(AosManagedParameters aosParams) {
        this.aosParams = aosParams;
    }

    @Override
    public TransferFrame parse(byte[] data, int offset, int length) throws TcTmException {
        int vn = data[offset] >>> 6;
        if (vn != 2) {
            throw new TcTmException("Invalid version number " + vn + " expected " + 2);
        }
        if (data.length != aosParams.frameLength) {
            throw new TcTmException("Bad frame length " + data.length + " expected " + aosParams.frameLength);
        }

        return null;
    }

}
