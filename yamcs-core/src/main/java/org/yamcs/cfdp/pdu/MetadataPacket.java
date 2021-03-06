package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.cfdp.FileDirective;
import org.yamcs.utils.CfdpUtils;

public class MetadataPacket extends CfdpPacket implements FileDirective {

    private boolean segmentationControl;
    private long fileSize;
    private LV sourceFileName;
    private LV destinationFileName;
    private List<FileStoreRequest> filestoreRequests = new ArrayList<FileStoreRequest>();
    private List<MessageToUser> messagesToUser = new ArrayList<MessageToUser>();
    private List<FaultHandlerOverride> faultHandlerOverrides = new ArrayList<FaultHandlerOverride>();
    private TLV flowLabel;

    public MetadataPacket(boolean segmentationControl, int fileSize, String source, String destination,
            List<FileStoreRequest> fsrs, List<MessageToUser> mtus, List<FaultHandlerOverride> fhos,
            TLV flowLabel, CfdpHeader header) {
        super(header);
        this.segmentationControl = segmentationControl;
        if (fileSize == 0) {
            throw new java.lang.UnsupportedOperationException("Unbound data size not yet implemented");
        }
        this.fileSize = fileSize;
        this.sourceFileName = new LV(source);
        this.destinationFileName = new LV(destination);
        this.filestoreRequests = fsrs;
        this.messagesToUser = mtus;
        this.faultHandlerOverrides = fhos;
        this.flowLabel = flowLabel;
        finishConstruction();
    }

    public MetadataPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        byte temp = buffer.get();
        this.segmentationControl = (temp & 0x01) == 1;
        this.fileSize = CfdpUtils.getUnsignedInt(buffer);
        if (this.fileSize == 0) {
            throw new java.lang.UnsupportedOperationException("Unbound data size not yet implemented");
        }
        this.sourceFileName = LV.readLV(buffer);
        this.destinationFileName = LV.readLV(buffer);

        while (buffer.hasRemaining()) {
            TLV tempTLV = TLV.readTLV(buffer);
            switch (tempTLV.getType()) {
            case 0:
                filestoreRequests.add(FileStoreRequest.fromTLV(tempTLV));
                break;
            case 2:
                messagesToUser.add(MessageToUser.fromTLV(tempTLV));
                break;
            case 4:
                faultHandlerOverrides.add(FaultHandlerOverride.fromTLV(tempTLV));
                break;
            case 5:
                flowLabel = tempTLV;
                break;
            }
        }
    }

    public long getPacketLength() {
        // TODO, add support for "unbounded data files"
        return this.fileSize;
    }

    @Override
    protected int calculateDataFieldLength() {
        int toReturn = 5 // first byte + File size
                + this.sourceFileName.getValue().length
                + this.destinationFileName.getValue().length;
        for (FileStoreRequest fsr : this.filestoreRequests) {
            toReturn += 2 // first byte of the FileStoreRequest + 1 time a LV length
                    + fsr.getFirstFileName().getValue().length;
            if (fsr.getSecondFileName() != null) {
                toReturn += 1 + fsr.getSecondFileName().getValue().length;
            }
        }
        for (MessageToUser mtu : this.messagesToUser) {
            toReturn += 2 // type and length of a TLV message
                    + mtu.getMessage().length;
        }
        for (FaultHandlerOverride fho : this.faultHandlerOverrides) {
            toReturn += 3;
        }
        toReturn += 3;
        if (flowLabel != null) {
            toReturn += flowLabel.getValue().length;
        }
        return toReturn;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        buffer.put((byte) ((segmentationControl ? 0 : 1) << 7));
        CfdpUtils.writeUnsignedInt(buffer, fileSize);
        sourceFileName.writeToBuffer(buffer);
        destinationFileName.writeToBuffer(buffer);
        filestoreRequests.forEach(x -> x.toTLV().writeToBuffer(buffer));
        messagesToUser.forEach(x -> x.toTLV().writeToBuffer(buffer));
        faultHandlerOverrides.forEach(x -> x.toTLV().writeToBuffer(buffer));
        if (flowLabel != null) {
            flowLabel.writeToBuffer(buffer);
        }
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.Metadata;
    }

    public String getSourceFilename() {
        return new String(sourceFileName.getValue());
    }
    
    public String getDestinationFilename() {
        return new String(destinationFileName.getValue());
    }
}
