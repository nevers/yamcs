package org.yamcs.http.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.ByteString;

/**
 * Provides realtime event subscription via web.
 */
public class PacketResource implements WebSocketResource {

    private String streamName;
    private Stream stream;
    private StreamSubscriber streamSubscriber;

    private ConnectedWebSocketClient client;

    private String yamcsInstance;

    public PacketResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            yamcsInstance = processor.getInstance();
        }
    }

    @Override
    public String getName() {
        return "packets";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        // TODO add websocket message for this
        throw new UnsupportedOperationException();
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder, String argument)
            throws WebSocketException {
        if (streamSubscriber != null) {
            throw new WebSocketException(ctx.getRequestId(), "Already subscribed to a stream");
        }

        this.streamName = argument;
        if (yamcsInstance != null) {
            YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
            stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new WebSocketException(ctx.getRequestId(),
                        "Invalid request. No stream named '" + streamName + "'");
            }
        } else {
            throw new WebSocketException(ctx.getRequestId(), "Invalid request. Instance unspecified");
        }

        doUnsubscribe(); // Only one subscription at a time
        doSubscribe();
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void unselectProcessor() {
        doUnsubscribe();
        yamcsInstance = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        yamcsInstance = processor.getInstance();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        stream = ydb.getStream(streamName);
        doSubscribe();
    }

    @Override
    public void socketClosed() {
        doUnsubscribe();
    }

    private void doSubscribe() {
        if (stream != null) {
            streamSubscriber = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    byte[] pktData = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                    long genTime = (Long) tuple.getColumn(StandardTupleDefinitions.GENTIME_COLUMN);
                    long receptionTime = (Long) tuple.getColumn(StandardTupleDefinitions.TM_RECTIME_COLUMN);
                    int seqNumber = (Integer) tuple.getColumn(StandardTupleDefinitions.SEQNUM_COLUMN);
                    TmPacketData tm = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(pktData))
                            .setGenerationTime(TimeEncoding.toProtobufTimestamp(genTime))
                            .setReceptionTime(TimeEncoding.toProtobufTimestamp(receptionTime))
                            .setSequenceNumber(seqNumber)
                            .setYamcsReceptionTime(receptionTime)
                            .setYamcsGenerationTime(genTime)
                            .build();
                    client.sendData(ProtoDataType.TM_PACKET, tm);
                }

                @Override
                public void streamClosed(Stream stream) {
                }
            };
            stream.addSubscriber(streamSubscriber);
        }
    }

    private void doUnsubscribe() {
        if (streamSubscriber != null) {
            stream.removeSubscriber(streamSubscriber);
        }
        streamSubscriber = null;
    }
}
