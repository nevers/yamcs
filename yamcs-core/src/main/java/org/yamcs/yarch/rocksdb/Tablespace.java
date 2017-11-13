package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

public class Tablespace {
    static Logger log = LoggerFactory.getLogger(Tablespace.class.getName());
    
    //used as the first bye of the tbsIndex 
    // it doesn't need to be unique but if it is, it will make it easier to move tables from one tablespace to another
    // (basically a table can be moved if the tbsIndex doesn't overlap with anothe record from the table)
    private final byte id;
    
    //unique name for this tablespace
    private final String name;
    
    private String dataDir;
    private static String CF_METADATA = "metadata";
    YRDB db;
    ColumnFamilyHandle cfMetadata;
    
    public Tablespace(String name, byte id) {
        this.name = name;
        this.id = id;
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
    public Iterator<TablespaceRecord> getTablePartitionIterator(String instanceName, String tableName) {
        return null;
    }



    public void removeTable(String yamcsInstance, String name2) {
        // TODO Auto-generated method stub
        
    }



    public TablespaceRecord createTablePartitionRecord(String yamcsNamespace, String tblName, String dir, byte[] bvalue) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }



    public String getDataDir() {
        return dataDir;
    }

    
    
    public YRDB getRdb(RdbPartition partition, boolean readOnly)  throws RocksDBException {
        return null;
    }
    public YRDB getRdb(RdbHistogramInfo histo, boolean readOnly)  throws RocksDBException {
        return null;
    }


    public void dispose(YRDB db) {
        // TODO Auto-generated method stub
        
    }



    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }



    public void loadDb(boolean readonly) throws IOException, RocksDBException {
        String dbDir = dataDir;
        if(dbDir==null) {
            dbDir = YarchDatabase.getDataDir()+"/"+name+".rdb";
        }
        File f = new File(dbDir);
        if(f.exists()) {
            log.info("Opening existing database {}", dbDir);
            db = RDBFactory.getInstance(name).getRdb(dbDir, readonly);
            cfMetadata = db.getColumnFamilyHandle(CF_METADATA);
            if(cfMetadata == null) {
                throw new IOException("Existing tablespace database '"+"'");
            }
        } else {
            if(readonly) {
                throw new IllegalStateException("Cannot create a new db when readonly is set to true");
            }
            log.info("Creating database at {}", dbDir);
            db = RDBFactory.getInstance(name).getRdb(dbDir, readonly);
            cfMetadata = db.createColumnFamily(CF_METADATA);
        }
     
       
    }
}
