package org.yamcs.yarch;

import java.io.IOException;
import java.util.List;

public interface BucketDatabase {
    Bucket createBucket(String bucketName) throws IOException;
    Bucket getBucket(String bucketName);
    List<String> listBuckets();
    void deleteBucket(String bucketName) throws IOException;
}
