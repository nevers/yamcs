package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class FileDataPacket extends CfdpPacket {

    private long offset;
    private byte[] filedata;

    public FileDataPacket(byte[] data, long offset) {
        super();
        this.offset = offset;
        this.filedata = data;
    }

    public FileDataPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.offset = Utils.getUnsignedInt(buffer);
        this.filedata = new byte[buffer.limit() - buffer.position()];
        buffer.get(this.filedata);

    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        Utils.writeUnsignedInt(buffer, offset);
        buffer.put(filedata);
    }

    @Override
    protected CfdpHeader createHeader() {
        // the '+4' originates from the length of the offset bytes
        return new CfdpHeader(false, false, false, false, this.filedata.length + 4, 1, 1, 123, 111, 246);
    }

}
