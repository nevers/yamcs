package org.yamcs.web.rest;

import org.yamcs.security.Privilege;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;

/**
 * Implements object storage
 */
public class BucketRestHandler extends RestHandler {
    static String GLOBAL_INSTANCE = "_global";

    @Route(path = "/api/buckets/:instance?", method = "GET")
    public void listBuckets(RestRequest req) throws HttpException {
        checkPrivileges(req);
        
    }

    @Route(path = "/api/buckets/:instance/:bucketName", method = { "POST" })
    public void createBucket(RestRequest req) throws HttpException {
        if (!Privilege.getInstance().hasPrivilege1(req.getAuthToken(), SystemPrivilege.MayCreateBucket)) {
            throw new ForbiddenException("No privilege for this operation");
        }
    
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
}
