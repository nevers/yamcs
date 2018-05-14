package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.security.Privilege;
import org.yamcs.security.SystemPrivilege;
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

    
    @Route(path = "/api/buckets/:instance?", method = "GET")
    public void listBuckets(RestRequest req) throws HttpException {
        checkPrivileges(req);
        BucketDatabase bdb = getBucketDb(req);
        bdb.listBuckets();
        
    }

    

    @Route(path = "/api/buckets/:instance/:bucketName", method = { "POST" })
    public void createBucket(RestRequest req) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), SystemPrivilege.MayCreateBucket)) {
            throw new ForbiddenException("No privilege for this operation");
        }
        String instance = verifyInstance(req, req.getRouteParam("instance"));
    
    }
    @Route(path = "/api/buckets/:instance/:bucketName", method = { "DELETE" })
    public void deleteBucket(RestRequest req) throws HttpException {
        checkPrivileges(req);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "POST" })
    public void uploadObject(RestRequest req) throws HttpException {
        checkPrivileges(req);
    }
    @Route(path = "/api/buckets/:instance/:bucketName", method = { "GET" })
    public void listObjects(RestRequest req) throws HttpException {
        checkPrivileges(req);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "GET" })
    public void getObject(RestRequest req) throws HttpException {
        checkPrivileges(req);
    }
    
    @Route(path = "/api/buckets/:instance/:bucketName/:objectName", method = { "DELETE" })
    public void deleteObject(RestRequest req) throws HttpException {
        checkPrivileges(req);
    }
    
    
    private void checkPrivileges(RestRequest req) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), SystemPrivilege.MayCreateBucket)) {
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
}
