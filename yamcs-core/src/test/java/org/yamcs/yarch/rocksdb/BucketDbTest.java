package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Bucket;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.ObjectProperties;

public class BucketDbTest {
    static String testDir = "/tmp/BucketDbTest";
    Random random = new Random();
    @BeforeClass
    static public void beforeClass() {
        TimeEncoding.setUp();
    }
    @Before
    public void cleanup() throws Exception {
        FileUtils.deleteRecursively(testDir);
    }
    
    @Test
    public void test1() throws Exception {
        String dir = testDir+"/tablespace1";
        Tablespace tablespace = new Tablespace("tablespace1", (byte)0);
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        
        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());
        
        RdbBucket bucket = bucketDb.createBucket("bucket1");
        assertNotNull(bucket);
        Exception e = null;
        try {
            bucketDb.createBucket("bucket1");
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        
        assertEquals("[bucket1]", bucketDb.listBuckets().toString());
        
        assertTrue(bucket.listObjects(x -> true).isEmpty());
        Map<String, String> props = new HashMap<>();
        props.put("prop1", "value1");
        props.put("prop2", "value2");
        byte[] objectData = new byte[1000];
        random.nextBytes(objectData);
        bucket.uploadObject("object1", null, props, objectData);
        List<ObjectProperties> l = bucket.listObjects(x -> true);
        assertEquals(1, l.size());
        assertEquals("object1", l.get(0).getName());
        
        byte[] b = bucket.getObject("object1");
        assertArrayEquals(objectData, b);
        
        //closing and reopening
        tablespace.close();
        tablespace = new Tablespace("tablespace2", (byte)0);
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        bucketDb = new RdbBucketDatabase("test", tablespace);
         
        assertEquals("[bucket1]", bucketDb.listBuckets().toString());
        bucket = bucketDb.getBucket("bucket1");
        assertEquals(1, bucket.maxObjectId.get());
        
        l = bucket.listObjects(x -> true);
        assertEquals(1, l.size());
        assertEquals("object1", l.get(0).getName());
        
        b = bucket.getObject("object1");
        assertArrayEquals(objectData, b);
        
        
        bucket.deleteObject("object1");
        assertTrue(bucket.listObjects(x -> true).isEmpty());
        
        bucketDb.deleteBucket("bucket1");
        assertTrue(bucketDb.listBuckets().isEmpty());
        tablespace.close();
    }    

    void testDelete() throws Exception {
        String dir = testDir+"/tablespace";
        Tablespace tablespace = new Tablespace("tablespace1", (byte)0);
        tablespace.setCustomDataDir(dir);
        tablespace.loadDb(false);
        
        RdbBucketDatabase bucketDb = new RdbBucketDatabase("test", tablespace);
        assertTrue(bucketDb.listBuckets().isEmpty());
        
        Bucket bucket = bucketDb.createBucket("bucket1");
        bucket.uploadObject("object1", null, new HashMap<>(), new byte[100]);
        bucket.uploadObject("object2", "plain/text", new HashMap<>(), new byte[100]);
        
        assertEquals(2, tablespace.getRdb().getApproxNumRecords());
        bucketDb.deleteBucket("bucket1");
        assertEquals(0, tablespace.getRdb().getApproxNumRecords());
        tablespace.close();
    }

}
