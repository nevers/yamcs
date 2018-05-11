package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.BucketDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

/**
 * Stores users objects in rocksdb
 * 
 * Each bucket has associated a TablespaceRecord with the corresponding tbsIndex.
 * 
 * Each object in the bucket has an 4 bytes objectId
 * 
 * The rocksdb key is formed by either one of:
 * 
 * 4 bytes    1 byte             variable size
 * tbsIndex   0 = bucket info
 * tbsIndex   1 = metadata       objectName (up to 1000 bytes)
 * tbsIndex   2 = data           objectId (4 bytes)
 * 
 * 
 * The rocksdb value is formed by:
 * in case of metadata:
 * protobuf representation of ObjectProperties (contains the objectId and key,value metadata)
 * in case of user object:
 * binary user object
 * 
 * To retrieve an object based on the bucket name and object name,
 * 1. retrieve the tbsIndex based on the bucket name
 * 2. retrieve the ObjectProperties based on the tbsIndex and object name
 * 3. retrieve the object data based on the tbsIndex and objectId
 * 
 * @author nm
 *
 */
public class RdbBucketDatabase implements BucketDatabase {
    private final Tablespace tablespace;
    private final String yamcsInstance;
    Map<String, RdbBucket> buckets = new HashMap<>();
    
    final static byte TYPE_BUCKET_INFO = 0;
    final static byte TYPE_OBJ_METADATA = 1;
    final static byte TYPE_OBJ_DATA = 2;
    
    RdbBucketDatabase(String yamcsInstance, Tablespace tablespace) throws RocksDBException, IOException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        loadBuckets();
    }

    private void loadBuckets() throws RocksDBException, IOException {
        List<TablespaceRecord> l = tablespace.filter(Type.BUCKET, yamcsInstance, x->true);
        for(TablespaceRecord tr: l) {
            RdbBucket b = new RdbBucket(tablespace, tr.getTbsIndex(), tr.getBucketName());
            buckets.put(b.getName(), b);
        }
    }

    @Override
    public RdbBucket createBucket(String bucketName) throws IOException {
        try {
            synchronized (buckets) {
                if (buckets.containsKey(bucketName)) {
                    throw new IllegalArgumentException("Bucket already exists");
                }
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.BUCKET)
                        .setBucketName(bucketName);
                TablespaceRecord tr = tablespace.createMetadataRecord(yamcsInstance, trb);
                RdbBucket bucket = new RdbBucket(tablespace, tr.getTbsIndex(), bucketName);
                buckets.put(bucketName, bucket);
                return bucket;
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to create bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public RdbBucket getBucket(String bucketName) {
        synchronized (buckets) {
            return buckets.get(bucketName);
        }
    }

    @Override
    public List<String> listBuckets() {
        synchronized (buckets) {
            return new ArrayList<>(buckets.keySet());
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws IOException {
        try {
            synchronized (buckets) {
                RdbBucket b = buckets.get(bucketName);
                if (b == null) {
                    throw new IllegalArgumentException("No bucket by this name");
                }
                tablespace.removeTbsIndex(Type.BUCKET, b.getTbsIndex());
                buckets.remove(b.getName());
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to delete bucket: " + e.getMessage(), e);
        }
    }

}
