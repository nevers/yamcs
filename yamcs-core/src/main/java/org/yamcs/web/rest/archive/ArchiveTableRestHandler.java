package org.yamcs.web.rest.archive;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Archive.TableData;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Rest.ListTablesResponse;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.Privilege.SystemPrivilege;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpContentToByteBufDecoder;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.web.rest.Router.RouteMatch;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.util.CharsetUtil;
import io.netty.channel.SimpleChannelInboundHandler;

public class ArchiveTableRestHandler extends RestHandler {
    
    @Route(path = "/api/archive/:instance/tables", method = "GET")
    public void listTables(RestRequest req) throws HttpException {
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayReadTables);
        
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        ListTablesResponse.Builder responseb = ListTablesResponse.newBuilder();
        for (TableDefinition def : ydb.getTableDefinitions()) {
            responseb.addTable(ArchiveHelper.toTableInfo(def));
        }
        completeOK(req, responseb.build(), SchemaRest.ListTablesResponse.WRITE);
    }
    
    

    @Route(path = "/api/archive/:instance/tables/:name", method = "GET")
    public void getTable(RestRequest req) throws HttpException {
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayReadTables);
        
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));
        
        TableInfo response = ArchiveHelper.toTableInfo(table);
        completeOK(req, response, SchemaArchive.TableInfo.WRITE);
    }
    
    @Route(path = "/api/archive/:instance/tables/:name/data", method = "GET")
    public void getTableData(RestRequest req) throws HttpException {
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayReadTables);
        
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));
        
        List<String> cols = null;        
        if (req.hasQueryParameter("cols")) {
            cols = new ArrayList<>(); // Order, and non-unique
            for (String para : req.getQueryParameterList("cols")) {
                for (String col : para.split(",")) {
                    cols.add(col.trim());
                }
            }
        }
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(table.getName());
        if (cols != null) {
            if (cols.isEmpty()) {
                throw new BadRequestException("No columns were specified");    
            } else {
                cols.forEach(col -> sqlb.select(col));
            }
        }
        sqlb.descend(req.asksDescending(true));
        
        String sql = sqlb.toString();
        TableData.Builder responseb = TableData.newBuilder();
        RestStreams.stream(instance, sql, new RestStreamSubscriber(pos, limit) {
            
            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                responseb.addRecord(rec); // TODO estimate byte size
            }

            @Override
            public void streamClosed(Stream stream) {
                completeOK(req, responseb.build(), SchemaArchive.TableData.WRITE);
            }
        });
    }
    
    @Route(path = "/api/archive/:instance/tables/:name/data", method = "POST", dataLoad = true)
    public void loadTableData(ChannelHandlerContext ctx, HttpRequest req,  RouteMatch match) throws HttpException {
        AuthenticationToken token = ctx.channel().attr(HttpRequestHandler.CTX_AUTH_TOKEN).get();
        verifyAuthorization(token, SystemPrivilege.MayWriteTables);
        String instance = match.getRouteParam("instance");
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException(req, "No instance named '" + instance + "'");
        }
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        String tableName = match.getRouteParam("name");
        TableDefinition table = ydb.getTable(tableName);
        if (table == null) {
            throw new NotFoundException(req, "No table named '" + tableName + "' (instance: '" + ydb.getName() + "')");
        } 
        
        MediaType contentType = RestRequest.deriveSourceContentType(req);
        if(contentType!=MediaType.PROTOBUF) {
            throw new BadRequestException("Invalid Content-Type "+contentType+" for table load; please use "+MediaType.PROTOBUF);
        }
        
        
        ChannelPipeline pipeline = ctx.pipeline();
        
        pipeline.addLast("bytebufextractor", new HttpContentToByteBufDecoder());
        pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());
        pipeline.addLast("protobufDecoder", new ProtobufDecoder(TableRecord.getDefaultInstance()));
        pipeline.addLast("loader", new TableLoader(req));
    }
    
    
    
    private void verifyAuthorization(AuthenticationToken authToken, SystemPrivilege p) throws ForbiddenException {
        if(!Privilege.getInstance().hasPrivilege1(authToken, p)) {
            throw new ForbiddenException("Need "+p+" privilege for this operation");
        }
    }
    
    
    
    static class TableLoader extends SimpleChannelInboundHandler<TableRecord>  {
        private static final Logger log = LoggerFactory.getLogger(TableLoader.class);
        int count =0;
        private HttpRequest req;
        boolean errorState = false;
        
        
        public TableLoader(HttpRequest req) {
            this.req = req;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TableRecord msg)  throws Exception {
            if(errorState) return;
            
            System.out.println("In table loader msg: "+count);
            count++;
        }
        
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if(errorState) return;
            errorState = true;
            
            log.warn("Exception caught in the table load pipeline, closing the connection: {}", cause.getMessage());
            if(cause instanceof DecoderException) {
                Throwable t = cause.getCause();
                sendErrorAndCloseAfter2Seconds(ctx, HttpResponseStatus.BAD_REQUEST, "Error after inserting "+count+" records: "+t.toString());
            } else {
                sendErrorAndCloseAfter2Seconds(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, cause.toString());
            }
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object obj) throws Exception {
            if(obj == HttpRequestHandler.CONTENT_FINISHED_EVENT) {
                log.debug("{} table load finished; inserted {} records ", ctx.channel().toString(), count);
                HttpRequestHandler.sendOK(ctx, req, "inserted "+count+" records\r\n");
            }
        }
        
        
        void sendErrorAndCloseAfter2Seconds(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(msg + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            //schedule close after 2 seconds so the client has the chance to read the error message
            // see https://groups.google.com/forum/#!topic/netty/eVB6SMcXOHI
            ctx.writeAndFlush(response).addListener(f-> {
                ctx.executor().schedule(()-> { ctx.close();}, 2, TimeUnit.SECONDS);
            });
           
        }
    }
}
