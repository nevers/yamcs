package org.yamcs.web.rest;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.CreateBucketRequest;
import org.yamcs.protobuf.Rest.ListBucketsResponse;
import org.yamcs.protobuf.Rest.ListObjectsResponse;
import org.yamcs.protobuf.Rest.ObjectInfo;
import org.yamcs.security.Privilege;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.ServiceUnavailableException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * Implements object storage
 */
public class BucketRestHandler extends RestHandler {
    static String GLOBAL_INSTANCE = "_global";
    private static final Logger log = LoggerFactory.getLogger(BucketRestHandler.class);
    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[\\w\\-\\./]+");
    
    @Route(path = "/api/buckets/:instance?", method = "GET")
    public void listBuckets(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
        BucketDatabase bdb = getBucketDb(req);
        ListBucketsResponse lbr = ListBucketsResponse.newBuilder().addAllName(bdb.listBuckets()).build();
        completeOK(req, lbr);
    }

    @Route(path = "/api/buckets/:instance", method = { "POST" })
    public void createBucket(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayCreateBucket);
        CreateBucketRequest crb = req.bodyAsMessage(CreateBucketRequest.newBuilder()).build();
        verifyBucketName(crb.getName());
        BucketDatabase bdb = getBucketDb(req);
        if(bdb.getBucket(crb.getName())!=null) {
            throw new BadRequestException("A bucket with the name '"+crb.getName()+"' already exist");
        }
        
        try {
            bdb.createBucket(crb.getName());
        } catch (IOException e) {
            log.error("Error when creating bucket", e);
            throw new InternalServerErrorException("Error when creating bucket: "+e.getMessage());
        }
        completeOK(req);
    }
    
  

    @Route(path = "/api/buckets/:instance/:bucketName", method = { "DELETE" })
    public void deleteBucket(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayCreateBucket);
        BucketDatabase bdb = getBucketDb(req);
        Bucket b = verifyAndGetBucket(req);
        try {
            bdb.deleteBucket(b.getName());
        } catch (IOException e) {
            log.warn("Error when deleting bucket", e);
            throw new InternalServerErrorException("Error when deleting bucket: "+e.getMessage());
        }
        completeOK(req);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName?", method = { "POST" })
    public void uploadObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayWriteToBucket);
        String contentType = req.getHeader(HttpHeaderNames.CONTENT_TYPE);
        System.out.println("here -------- contentType: '"+contentType+"'");
        if(contentType.startsWith("multipart/form-data")) {
            uploadObjectMultipartFormData(req);
        } else if(contentType.startsWith("multipart/related")) {
            uploadObjectMultipartRelated(req);
        } else {
            uploadObjectSimple(req);
        }
      
    }
    
    private void uploadObjectSimple(RestRequest req) throws HttpException {
        Bucket b = verifyAndGetBucket(req);
        String contentType = req.getHeader(HttpHeaderNames.CONTENT_TYPE);
        
        String objName = req.getRouteParam("objectName");
        if(objName==null) {
            objName = req.getQueryParameter("name");
        }
        saveObject(b, objName, contentType, req.bodyAsBuf());
        completeOK(req);
    }

    private void uploadObjectMultipartFormData(RestRequest req) throws HttpException {
        HttpPostMultipartRequestDecoder decoder = new HttpPostMultipartRequestDecoder(req.getHttpRequest());
        Bucket b = verifyAndGetBucket(req);
        
        for(InterfaceHttpData data: decoder.getBodyHttpDatas()) {
            if(data instanceof FileUpload) {
                FileUpload fup = (FileUpload) data;
                saveObject(b, fup.getFilename(), fup.getContentType(), fup.content());
            }
        }
        completeOK(req);
    }

    private void uploadObjectMultipartRelated(RestRequest req) throws HttpException {
        throw new ServiceUnavailableException("multipart/related uploads not yet implemented");
    }
    
    private void saveObject(Bucket bucket, String objName, String contentType, ByteBuf buf) throws HttpException {
        verifyObjectName(objName);
        byte[] objectData = new byte[buf.readableBytes()];
        buf.readBytes(objectData);
        
        try {
            bucket.uploadObject(objName, contentType, null, objectData);
        } catch (IOException e) {
            log.error("Error when uploading object {} to bucket {} ", objName, bucket.getName(), e);
            throw new InternalServerErrorException("Error when uploading object to bucket: "+e.getMessage());
        }
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName", method = { "GET" })
    public void listObjects(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
        Bucket b = verifyAndGetBucket(req);
        try {
            List<ObjectProperties> objects = b.listObjects(x->true);
            ListObjectsResponse.Builder lor = ListObjectsResponse.newBuilder();
            for(ObjectProperties props: objects) {
                ObjectInfo oinfo = ObjectInfo.newBuilder().setCreated(TimeEncoding.toString(props.getCreated()))
                        .setName(props.getName()).putAllMetadata(props.getMetadataMap()).build();
                lor.addObjects(oinfo);
            }
            completeOK(req, lor.build());
        } catch (IOException e) {
            log.error("Error when retrieving object list from bucket {}",  b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object list: "+e.getMessage());
        }
        
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "GET" })
    public void getObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
        
        String objName = req.getRouteParam("objectName");
        Bucket b = verifyAndGetBucket(req);
        try {
            ObjectProperties props = b.findObject(objName);
            if(props==null) {
                throw new NotFoundException(req);
            }
            byte[] objData = b.getObject(objName);
            String contentType = props.hasContentType()?props.getContentType():"application/octet-stream";
            completeOK(req, contentType, Unpooled.wrappedBuffer(objData));
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: "+e.getMessage());
        }
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "DELETE" })
    public void deleteObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayWriteToBucket);
        
        String objName = req.getRouteParam("objectName");
        Bucket b = verifyAndGetBucket(req);
        try {
            ObjectProperties props = b.findObject(objName);
            if(props==null) {
                throw new NotFoundException(req);
            }
            b.deleteObject(objName);
            completeOK(req);
        } catch (IOException e) {
            log.error("Error when retrieving object {} from bucket {} ", objName, b.getName(), e);
            throw new InternalServerErrorException("Error when retrieving object: "+e.getMessage());
        }
    }
    
    
    private void checkPrivileges(RestRequest req, SystemPrivilege priv) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), priv)) {
            throw new ForbiddenException("No privilege for this operation");
        }
    }
    
    static private BucketDatabase getBucketDb(RestRequest req) throws HttpException {
        String yamcsInstance = verifyInstance(req, req.getRouteParam("instance"), true);
        YarchDatabaseInstance ydi = YarchDatabase.getInstance(yamcsInstance);
        try {
            BucketDatabase bdb = ydi.getBucketDatabase();
            if(bdb==null) {
                throw new NotFoundException(req); 
            }
            return bdb;
        } catch (YarchException e) {
            log.error("Error getting bucket database" ,e);
            throw new InternalServerErrorException("Bucket database not available");
        }
    }
    
    boolean isUserBucket(String username, String bucketName) {
        return bucketName.equals("/user/"+username);
    }
    
    static Bucket verifyAndGetBucket(RestRequest req) throws HttpException {
        BucketDatabase bdb = getBucketDb(req);
        String bucketName = req.getRouteParam("bucketName");
        Bucket bucket = bdb.getBucket(bucketName);
        if(bucket==null) {
            throw new NotFoundException(req);
        }
        
        return bucket;
    }
    
    static private void verifyBucketName(String bucketName) throws BadRequestException {
        if(bucketName==null) {
            throw new BadRequestException("No bucketName specified");
        }
        if(!BUCKET_NAME_REGEXP.matcher(bucketName).matches()) {
            throw new BadRequestException("Invalid bucket name specified");
        } 
    }
    
    static private void verifyObjectName(String objName) throws BadRequestException {
        if(objName==null) {
            throw new BadRequestException("No object name specified");
        }
        if(!OBJ_NAME_REGEXP.matcher(objName).matches()) {
            throw new BadRequestException("Invalid object name specified");
        } 
    }
}
