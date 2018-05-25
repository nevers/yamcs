package org.yamcs.yarch.rocksdb;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.BucketProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectPropertiesOrBuilder;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import com.google.protobuf.InvalidProtocolBufferException;

import static org.yamcs.utils.ByteArrayUtils.encodeInt;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;
import static org.yamcs.yarch.rocksdb.RdbBucketDatabase.*;

public class RdbBucket implements Bucket {
    final int tbsIndex;
    BucketProperties bucketProps;
    final Tablespace tablespace;
    AtomicInteger maxObjectId;
    final String yamcsInstance;
    private static final Logger log = LoggerFactory.getLogger(RdbBucket.class);
    
    public RdbBucket(String yamcsInstance, Tablespace tablespace, int tbsIndex, BucketProperties bucketProps) throws IOException {
        this.yamcsInstance = yamcsInstance;
        this.tbsIndex = tbsIndex;
        this.bucketProps = bucketProps;
        this.tablespace = tablespace;
        int maxid = findMaxObjectId(tbsIndex, tablespace.getRdb());
        this.maxObjectId = new AtomicInteger(maxid);
    }

    private static int findMaxObjectId(int tbsIndex, YRDB rdb) throws IOException {
        byte[] key = new byte[TBS_INDEX_SIZE+1];
        encodeInt(tbsIndex, key, 0);
        key[TBS_INDEX_SIZE] = TYPE_OBJ_METADATA;
        
        try (DbIterator it = rdb.newDescendingPrefixIterator(key)) {
            ObjectProperties.Builder opb = ObjectProperties.newBuilder();
            int maxId = 0;
            if(it.isValid()) {
                byte[] v = it.value();
                opb.mergeFrom(v);
                maxId = opb.getObjectId();
            }
            return maxId;
        }
    }

    @Override
    public List<ObjectProperties> listObjects(Predicate<ObjectPropertiesOrBuilder> p) throws IOException {
        byte[] start = new byte[TBS_INDEX_SIZE+1];
        encodeInt(tbsIndex, start, 0);
        start[TBS_INDEX_SIZE] = TYPE_OBJ_METADATA;
        List<ObjectProperties> r = new ArrayList<>();
        try (DbIterator it = tablespace.getRdb().newPrefixIterator(start)) {
            ObjectProperties.Builder opb = ObjectProperties.newBuilder();
            while(it.isValid()) {
                byte[] k = it.key();
                byte[] v = it.value();
                String objName = new String(k, TBS_INDEX_SIZE+1, k.length-TBS_INDEX_SIZE-1, StandardCharsets.UTF_8);
                opb.mergeFrom(v);
                opb.setName(objName);
                if(p.test(opb)) {
                    r.add(opb.build());
                    opb = ObjectProperties.newBuilder();
                }
                it.next();
            }
        }
        return r;
    }

    @Override
    public  synchronized void uploadObject(String objectName, String contentType, Map<String, String> metadata, byte[] objectData)  throws IOException {
        log.debug("Uploading object {} to bucket {}; contentType: {}", objectName, bucketProps.getName(), contentType);
        ObjectProperties.Builder props = ObjectProperties.newBuilder();
        if(metadata!=null) {
            props.putAllMetadata(metadata);
        }
        int objectId = maxObjectId.incrementAndGet();
        props.setObjectId(objectId);
        props.setCreated(TimeEncoding.getWallclockTime());
        props.setSize(objectData.length);
        if(contentType!=null) {
            props.setContentType(contentType);
        }
        try(WriteBatch writeBatch = new WriteBatch();
                WriteOptions writeOpts = new WriteOptions()) {
            byte[] mk = getMetaKey(objectName);
            byte[] dk = getDataKey(objectId);
            writeBatch.put(mk, props.build().toByteArray());
            writeBatch.put(dk, objectData);
            long bsize = bucketProps.getSize()+props.getSize();
            if(bsize>bucketProps.getMaxSize()) {
                throw new IOException("Maximum bucket size "+bucketProps.getMaxSize()+" exceeded");
            }
            int numobj = bucketProps.getNumObjects() +1;
            if(numobj>bucketProps.getMaxNumObjects()) {
                throw new IOException("Maximum number of objects in the bucket "+bucketProps.getNumObjects()+" exceeded");
            }
            BucketProperties bucketProps1 = BucketProperties.newBuilder().mergeFrom(bucketProps)
                    .setNumObjects(bucketProps.getNumObjects()+1).setSize(bsize).build();
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.BUCKET).setBucketProperties(bucketProps1).setTbsIndex(tbsIndex);
            tablespace.updateRecord(yamcsInstance, writeBatch, trb);
            
            tablespace.getRdb().getDb().write(writeOpts, writeBatch);
        } catch (RocksDBException e) {
            throw new IOException("Error writing object data: "+e.getMessage(), e);
        } 
    }
    public ObjectProperties findObject(String objectName) throws IOException {
        byte[] k = getMetaKey(objectName);
        try {
            byte[] v = tablespace.getRdb().get(k);
            if(v==null) {
                return null;
            }
            return ObjectProperties.newBuilder().mergeFrom(v).setName(objectName).build();
        } catch (InvalidProtocolBufferException e) {
            throw new DatabaseCorruptionException("Cannot decode data: "+e.getMessage(), e);
        } catch (RocksDBException e1) {
            throw new IOException(e1);
        }
    }

    @Override
    public byte[] getObject(String objectName) throws IOException {
        try {
            ObjectProperties props = findObject(objectName);
            if(props == null) {
                throw new FileNotFoundException("No object by name '"+objectName+"' found");
            }
            byte[] k = getDataKey(props.getObjectId());
            YRDB rdb = tablespace.getRdb();

            return rdb.get(k);
        } catch (RocksDBException e) {
            throw new IOException("Failed to retrieve object: "+e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteObject(String objectName)  throws IOException {
        log.debug("deleting {} from {}", objectName, bucketProps.getName());
        try {
            ObjectProperties props = findObject(objectName);
            if(props == null) {
                throw new IOException("No object by name '"+objectName+"' found");
            }
            try(WriteBatch writeBatch = new WriteBatch();
                    WriteOptions writeOpts = new WriteOptions()) {
                byte[] mk = getMetaKey(objectName);
                byte[] dk = getDataKey(props.getObjectId());
                writeBatch.remove(mk);
                writeBatch.remove(dk);
                BucketProperties bucketProps1 = BucketProperties.newBuilder().mergeFrom(bucketProps)
                        .setNumObjects(bucketProps.getNumObjects()-1).setSize(bucketProps.getSize()-props.getSize()).build();
                TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.BUCKET).setBucketProperties(bucketProps1).setTbsIndex(tbsIndex);
                tablespace.updateRecord(objectName, writeBatch, trb);
                tablespace.getRdb().getDb().write(writeOpts, writeBatch);
                bucketProps = bucketProps1;
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to retrieve object: "+e.getMessage(), e);
        }
    }

    public String getName() {
        return bucketProps.getName();
    }

    public int getTbsIndex() {
        return tbsIndex;
    }

    private byte[] getDataKey(int objectId) {
        byte[] k = new byte[TBS_INDEX_SIZE+5];
        encodeInt(tbsIndex, k, 0);
        k[TBS_INDEX_SIZE] = TYPE_OBJ_DATA;
        encodeInt(objectId, k, TBS_INDEX_SIZE+1);
        return k;
    }

    private byte[] getMetaKey(String objectName) {
        byte[] a  = objectName.getBytes(StandardCharsets.UTF_8);
        byte[] k = new byte[a.length+5];

        encodeInt(tbsIndex, k, 0);
        k[TBS_INDEX_SIZE] = TYPE_OBJ_METADATA;
        System.arraycopy(a, 0, k, TBS_INDEX_SIZE+1, a.length);

        return k;
    }
}
