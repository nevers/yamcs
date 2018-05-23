package org.yamcs.web.rest;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest.ListBucketsResponse;
import org.yamcs.security.Privilege;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Implements object storage
 */
public class BucketRestHandler extends RestHandler {
    static String GLOBAL_INSTANCE = "_global";
    private static final Logger log = LoggerFactory.getLogger(BucketRestHandler.class);
    static final Pattern BUCKET_NAME_REGEXP = Pattern.compile("\\w+");
    static final Pattern OBJ_NAME_REGEXP = Pattern.compile("[\\w/]+");
    
    @Route(path = "/api/buckets/:instance?", method = "GET")
    public void listBuckets(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
        BucketDatabase bdb = getBucketDb(req);
        ListBucketsResponse lbr = ListBucketsResponse.newBuilder().addAllName(bdb.listBuckets()).build();
        completeOK(req, lbr);
    }

    @Route(path = "/api/buckets/:instance/:bucketName", method = { "POST" })
    public void createBucket(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayCreateBucket);
        BucketDatabase bdb = getBucketDb(req);
        String bucketName = verifyAndGetBucketName(req);
        try {
            bdb.createBucket(bucketName);
        } catch (IOException e) {
            log.warn("Error when creating bucket", e);
            throw new InternalServerErrorException("Error when creating bucket: "+e.getMessage());
        }
        completeOK(req);
        
    }
    @Route(path = "/api/buckets/:instance/:bucketName", method = { "DELETE" })
    public void deleteBucket(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayCreateBucket);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "POST" })
    public void uploadObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayWriteToBucket);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName", method = { "GET" })
    public void listObjects(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "GET" })
    public void getObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayReadBucket);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "DELETE" })
    public void deleteObject(RestRequest req) throws HttpException {
        checkPrivileges(req, SystemPrivilege.MayWriteToBucket);
    }
    
    
    private void checkPrivileges(RestRequest req, SystemPrivilege priv) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), priv)) {
            throw new ForbiddenException("No privilege for this operation");
        }
    }
    
    private BucketDatabase getBucketDb(RestRequest req) throws HttpException {
        String yamcsInstance = verifyInstance(req, req.getRouteParam("instance"));
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
    
    static String verifyAndGetBucketName(RestRequest req) throws HttpException {
        String bucketName = req.getRouteParam("bucketName");
        if(bucketName==null) {
            throw new BadRequestException("No bucketName specified");
        }
        if(!BUCKET_NAME_REGEXP.matcher(bucketName).matches()) {
            throw new BadRequestException("Invalid bucketName specified");
        }
        return bucketName;
    }
}
