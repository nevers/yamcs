package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;
import com.google.protobuf.ByteString;

import static org.yamcs.utils.ByteArrayUtils.decodeInt;
import static org.yamcs.utils.ByteArrayUtils.encodeInt;

public class Tablespace {
    static Logger log = LoggerFactory.getLogger(Tablespace.class.getName());
    
    //used as the first bye of the tbsIndex 
    // it doesn't need to be unique but if it is, it will make it easier to move tables from one tablespace to another
    // (basically a table can be moved if the tbsIndex doesn't overlap with anothe record from the table)
    private final byte id;
    
    //unique name for this tablespace
    private final String name;
    
    private String customDataDir;
    private static String CF_METADATA = "metadata";
    YRDB db;
    ColumnFamilyHandle cfMetadata;
    private AtomicLong maxTbsIndex = new AtomicLong();
    RDBFactory rdbFactory;
    
    public Tablespace(String name, byte id) {
        this.name = name;
        this.id = id;
        rdbFactory = RDBFactory.getInstance(name);
    }
    
    public void loadDb(boolean readonly) throws IOException, RocksDBException {
        String dbDir = getDataDir();
        
        File f = new File(dbDir);
        if(f.exists()) {
            log.info("Opening existing database {}", dbDir);
            db = rdbFactory.getRdb(dbDir, readonly);
            cfMetadata = db.getColumnFamilyHandle(CF_METADATA);
            if(cfMetadata == null) {
                throw new IOException("Existing tablespace database '"+dbDir+"' does not contain a column family named '"+CF_METADATA);
            }
            try (RocksIterator it = db.newIterator(cfMetadata)) {
                it.seekToLast();
                if(it.isValid()) {
                    maxTbsIndex.set(Integer.toUnsignedLong(decodeInt(it.key(), 0)));
                } else {
                    initMaxTbsIndex();
                }
            }
            
        } else {
            if(readonly) {
                throw new IllegalStateException("Cannot create a new db when readonly is set to true");
            }
            initMaxTbsIndex();
            log.info("Creating database at {}", dbDir);
            db = RDBFactory.getInstance(name).getRdb(dbDir, readonly);
            cfMetadata = db.createColumnFamily(CF_METADATA);
        }
    }

    public String getName() {
        return name;
    }

    public byte getId() {
        return id;
    }
    
    /**
     * Iterates over all records of type TABLE_PARTITION for a given instance and table
     * 
     * If instanceName = tablespace name, it returns also records which do not have an instanceName specified. 
     */
    public List<TablespaceRecord> getTablePartitions(String instanceName, String tableName) throws RocksDBException, IOException {
        List<TablespaceRecord> r = new ArrayList<>();
        //this is not very efficient 
        try (RocksIterator it = db.newIterator(cfMetadata)) {
            it.seekToFirst();
            while(it.isValid()) {
                TablespaceRecord.Builder tr = TablespaceRecord.newBuilder().mergeFrom(it.value());
                if(tableName.equals(tr.getTableName())) {
                    if(tr.hasInstanceName()) {
                        if(instanceName.equals(tr.getInstanceName())) {
                            r.add(tr.build());
                        }
                    } else {
                        if(instanceName.equals(name)) {
                    
                        tr.setInstanceName(name);
                        r.add(tr.build());
                    }}
                }
                it.next();
            }
        }
        return r;
    }


    /**
     * Create a tablespace record for a table partition. 
     * If the tablespace name is the same with yamcsInstance, do not store the name inside the record 
     * (such that the instance/tablespace names can change without changing the metadata) 
     * 
     */
    public TablespaceRecord createTablePartitionRecord(String yamcsInstance, String tblName, String dir, byte[] bvalue) throws RocksDBException {
        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.TABLE_PARTITION)
                .setTableName(tblName);
        if(dir!=null) {
            trb.setPartitionDir(dir);
        }
        if(bvalue!=null) {
            trb.setPartitionValue(ByteString.copyFrom(bvalue));
        }
        
        if(!yamcsInstance.equals(name)) {
            trb.setInstanceName(yamcsInstance);
        }
        int tbsIndex = (int)maxTbsIndex.incrementAndGet();
        trb.setTbsIndex(tbsIndex);
        
        TablespaceRecord tr = trb.build();
        log.debug("Adding table partition {}", tr);
        
        db.put(cfMetadata, getKey(tbsIndex), tr.toByteArray());
        return tr;
    }

    public String getCustomDataDir() {
        return customDataDir;
    }
    private void initMaxTbsIndex() {
        maxTbsIndex.set(id<<24);
    }
    
    
    public YRDB getRdb(RdbPartition partition, boolean readOnly)  throws IOException {
        if(partition.dir==null) {
            return db;
        } else {
            return rdbFactory.getRdb(partition.dir, readOnly);
        }
    }
    public YRDB getRdb(RdbHistogramInfo histo, boolean readOnly)  throws RocksDBException {
        return null;
    }


    public void dispose(YRDB rdb) {
        if(db==rdb){
            return;
        } else {
            rdbFactory.dispose(rdb);
        }
    }

    public void setCustomDataDir(String dataDir) {
        this.customDataDir = dataDir;
    }

 
    
    String getDataDir() {
        String dir = customDataDir;
        if(dir==null) {
            dir = YarchDatabase.getDataDir()+"/"+name+".rdb";
        }
        return dir;
    }



    public void removeTbsIndex(int tbsIndex) throws RocksDBException {
        db.getDb().delete(getKey(tbsIndex));
    }

    
    /**
     * the key to use in the metadata table. We currently use 5 bytes, the first is 0xFF and the next 4 are tbsIndex. 
     * in the future we may use a byte different than 0xFF to store the same data sorted differently
     *
     * the reason for using 0xFF as the first byte is to get the highest tbsIndex by reading the last record.
     */ 
    private byte[] getKey(int tbsIndex) {
        byte[] key = new byte[5];
        key[0] = (byte) 0xFF;
        encodeInt(tbsIndex, key, 1);
        return key;
    }

    private int getTbsIndex(byte[] key) {
        return decodeInt(key, 1);
    }
    public void close() {
        rdbFactory.close(db);
    }
}
