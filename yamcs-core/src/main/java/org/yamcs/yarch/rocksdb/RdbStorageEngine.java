package org.yamcs.yarch.rocksdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsServer;
import org.yamcs.archive.TagDb;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.StorageEngine;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.rocksdb.RdbHistogramIterator;
import org.yaml.snakeyaml.Yaml;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;

/**
 * Storage Engine based on RocksDB.
 * 
 * Tables are mapped to multiple RocksDB databases - one for each time based partition.
 * 
 * 
 */
public class RdbStorageEngine implements StorageEngine {
    Map<TableDefinition, RdbPartitionManager> partitionManagers = new HashMap<>();
    Map<String, Tablespace> tablespaces = new HashMap<>();

    static {
        RocksDB.loadLibrary();
    }
    static Logger log = LoggerFactory.getLogger(RdbStorageEngine.class.getName());
    RdbTagDb rdbTagDb = null;
    boolean ignoreVersionIncompatibility = false;
    static RdbStorageEngine instance = new RdbStorageEngine();

    RdbStorageEngine() {
    }

    public void loadTablespaces(boolean readOnly) throws YarchException {
        File dir=new File(YarchDatabase.getDataDir());
        if(dir.exists() ) {
            File[] dirFiles = dir.listFiles();
            if(dirFiles==null) {
                return; //no tables found
            }
            for(File f:dirFiles) {
                String fn=f.getName();
                if(fn.endsWith(".tbs")) {
                    try {
                        Tablespace tablespace = deserializeTablespace(f);
                        tablespace.loadDb(readOnly);
                        tablespaces.put(tablespace.getName(), tablespace);
                    } catch (IOException|RocksDBException e) {
                        log.warn("Got exception when reading the table definition from {}: ", f, e);
                        throw new YarchException("Got exception when reading the table definition from "+f+": ", e);
                    }  
                }
            }
        } 
    }

    @Override
    public void loadTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        Tablespace tablespace = getTablespace(ydb, tblDef);
        RdbPartitionManager pm = new RdbPartitionManager(tablespace, ydb, tblDef);
        partitionManagers.put(tblDef, pm);
        pm.readPartitions();
    }


    @Override
    public void dropTable(YarchDatabaseInstance ydb, TableDefinition tbl) throws YarchException {
        RdbPartitionManager pm = partitionManagers.remove(tbl);
        Tablespace tablespace = getTablespace(ydb, tbl);
        tablespace.removeTable(ydb.getYamcsInstance(), tbl.getName());

        for(Partition p:pm.getPartitions()) {
            RdbPartition rdbp = (RdbPartition)p;


            RDBFactory rdbFactory = RDBFactory.getInstance(tablespace.getName());
            File f=new File(tablespace.getCustomDataDir()+"/"+rdbp.dir);
            try {
                YRDB db = tablespace.getRdb(rdbp, false);
                byte[] b = dbKey(rdbp.tbsIndex);
                db.deleteAllWithPrefix(b);
            } catch (IOException e) {
                log.error("Error when removing partition", e);
            }
            rdbFactory.closeIfOpen(f.getAbsolutePath());
        }

    }

    @Override
    public TableWriter newTableWriter(YarchDatabaseInstance ydb, TableDefinition tblDef, InsertMode insertMode) throws YarchException {
        if(!partitionManagers.containsKey(tblDef)) {
            throw new IllegalStateException("Do not have a partition manager for this table");
        }
        checkFormatVersion(ydb, tblDef);

        try {
            return new RdbTableWriter(getTablespace(ydb, tblDef), ydb, tblDef, insertMode, partitionManagers.get(tblDef));
        } catch (IOException e) {
            throw new YarchException("Failed to create writer", e);
        } 
    }


    @Override
    public AbstractStream newTableReaderStream(YarchDatabaseInstance ydb, TableDefinition tbl, boolean ascending, boolean follow) {
        if(!partitionManagers.containsKey(tbl)) {
            throw new IllegalStateException("Do not have a partition manager for this table");
        }

        return new RdbTableReaderStream(getTablespace(ydb, tbl), ydb, tbl, partitionManagers.get(tbl), ascending, follow);
    }

    @Override
    public void createTable(YarchDatabaseInstance ydb, TableDefinition def) {		
        RdbPartitionManager pm = new RdbPartitionManager(getTablespace(ydb, def), ydb, def);
        partitionManagers.put(def, pm);
    }

    public static synchronized RdbStorageEngine getInstance() {
        return instance;
    }

    public RdbPartitionManager getPartitionManager(TableDefinition tdef) {      
        return partitionManagers.get(tdef);
    }




    @Override
    public synchronized TagDb getTagDb(YarchDatabaseInstance ydb) throws YarchException {
        if(rdbTagDb==null) {
            try {
                rdbTagDb = new RdbTagDb(ydb);
            } catch (RocksDBException e) {
                throw new YarchException("Cannot create tag db",e);
            }
        }
        return rdbTagDb;
    }

    private synchronized Tablespace getTablespace(YarchDatabaseInstance ydb, TableDefinition tbl) {
        String tablespaceName = tbl.getTablespaceName();
        if(tablespaceName==null) {
            tablespaceName = ydb.getName();
        }
        if(tablespaces.containsKey(tablespaceName)) {
            return tablespaces.get(tablespaceName);
        } else {
            createTablespace(tablespaceName);
        }
        return tablespaces.get(tablespaceName);
    }

    private void createTablespace(String tablespaceName) {
        log.info("Creating tablespace {}", tablespaceName);
        int id = tablespaces.values().stream().mapToInt(t->t.getId()).max().orElse(0);
        Tablespace t = new Tablespace(tablespaceName, (byte)(id+1));

        String fn = YarchDatabase.getDataDir()+"/"+tablespaceName+".tbs";
        try (FileOutputStream fos = new FileOutputStream(fn)) {
            Yaml yaml = new Yaml(new TablespaceRepresenter());
            Writer w = new BufferedWriter(new OutputStreamWriter(fos));
            yaml.dump(t, w);
            w.flush();
            fos.getFD().sync();
            w.close();
            t.loadDb(false);
        } catch (IOException|RocksDBException e) {
            YamcsServer.getGlobalCrashHandler().handleCrash("RdbStorageEngine", "Cannot write tablespace definition to "+fn+" :"+e);
            log.error("Got exception when writing tablespapce definition to {} ",fn, e);
            throw new RuntimeException(e);
        }
        
        tablespaces.put(tablespaceName, t);
    }


    private Tablespace deserializeTablespace(File f) throws IOException {
        String fn = f.getName();
        
        try(FileInputStream fis = new FileInputStream(f)) {
            String tablespaceName=fn.substring(0,fn.length()-6);
            Yaml yaml = new Yaml(new TablespaceConstructor(tablespaceName));
            Object o = yaml.load(fis);
            if(!(o instanceof Tablespace)) {
                fis.close();
                throw new IOException("Cannot load tablespace definition from "+f+": object is "+o.getClass().getName()+"; should be "+Tablespace.class.getName());
            }
            Tablespace tablespace = (Tablespace) o;
            fis.close();
            return tablespace;
        }
    }
    /**
     * set to ignore version incompatibility - only used from the version upgrading functions to allow loading old tables.
     * 
     * @param b
     */
    public void setIgnoreVersionIncompatibility(boolean b) {
        this.ignoreVersionIncompatibility = b;
    }

    private void checkFormatVersion(YarchDatabaseInstance ydb, TableDefinition tblDef) throws YarchException {
        if(ignoreVersionIncompatibility) {
            return;
        }

        if(tblDef.getFormatVersion()!=TableDefinition.CURRENT_FORMAT_VERSION) {
            throw new YarchException("Table "+ydb.getName()+"/"+tblDef.getName()+" format version is "+tblDef.getFormatVersion()
            + " instead of "+TableDefinition.CURRENT_FORMAT_VERSION+", please upgrade (use the \"yamcs archive upgrade\" command).");
        }
    }
    @Override
    public Iterator<HistogramRecord> getHistogramIterator(YarchDatabaseInstance ydb, TableDefinition tblDef, String columnName, TimeInterval interval, long mergeTime) throws YarchException {
        checkFormatVersion(ydb, tblDef);
        try {
            return new RdbHistogramIterator(getTablespace(ydb, tblDef), ydb, tblDef, columnName, interval, mergeTime);
        } catch (RocksDBException e) {
            throw new YarchException(e);
        }
    }
    static byte[] dbKey(int tbsIndex) {
        return ByteArrayUtils.encodeInt(tbsIndex, new byte[4], 0);
    }
    static byte[] dbKey(int tbsIndex, byte[] key) {
        byte[] dbKey = ByteArrayUtils.encodeInt(tbsIndex, new byte[key.length+4], 0);
        System.arraycopy(key, 0, dbKey, 4, key.length);
        return dbKey;
    }
}
