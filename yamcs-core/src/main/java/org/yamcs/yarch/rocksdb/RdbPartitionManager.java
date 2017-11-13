package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.HistogramInfo;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TimePartitionSchema.PartitionInfo;
import org.yamcs.yarch.oldrocksdb.ColumnValueSerializer;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.utils.TimeEncoding;

/**
 * Handles partitions for one table. All partitions are stored as records in the tablespace.
 *  
 */
public class RdbPartitionManager extends PartitionManager {
    final Tablespace tablespace;
    static Logger log = LoggerFactory.getLogger(RdbPartitionManager.class.getName());
    YarchDatabaseInstance ydb;

    public RdbPartitionManager(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tableDefinition) {
        super(tableDefinition);
        this.tablespace = tablespace;
        this.ydb = ydb;
    }

    /** 
     * Called at startup to read existing partitions
     */
    public void readPartitions() {
        Iterator<TablespaceRecord> it = tablespace.getTablePartitionIterator(ydb.getName(), tableDefinition.getName());
        ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
        while(it.hasNext()) {
            TablespaceRecord tr = it.next();

            if(tr.hasPartitionValue()) {
                if(tr.hasPartitionDir()) {
                    PartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().parseDir(tr.getPartitionDir());
                    addPartitionByTimeAndValue(tr.getTbsIndex(), pinfo, cvs.byteArrayToObject(tr.getPartitionValue().toByteArray()));
                } else {
                    addPartitionByValue(tr.getTbsIndex(), cvs.byteArrayToObject(tr.getPartitionValue().toByteArray()));
                }
            } else {
                if(tr.hasPartitionDir()) {
                    PartitionInfo pinfo = partitioningSpec.getTimePartitioningSchema().parseDir(tr.getPartitionDir());
                    addPartitionByTime(tr.getTbsIndex(), pinfo);
                } else {
                    addPartitionByNone(tr.getTbsIndex());
                }
            }
        }
    }


    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTime(int tbsIndex, PartitionInfo pinfo) {               
        Interval intv = intervals.get(pinfo.partitionStart);      

        if(intv==null) {            
            intv=new Interval(pinfo.partitionStart, pinfo.partitionEnd);
            intervals.put(pinfo.partitionStart, intv);
        }
        Partition p = new RdbPartition(tbsIndex, pinfo.partitionStart, pinfo.partitionEnd, null, pinfo.dir+"/"+tableDefinition.getName());
        intv.addTimePartition(p);
    }   

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByTimeAndValue(int tbsIndex, PartitionInfo pinfo, Object value) {	   	   
        Interval intv = intervals.get(pinfo.partitionStart);	  

        if(intv==null) {	    
            intv = new Interval(pinfo.partitionStart, pinfo.partitionEnd);
            intervals.put(pinfo.partitionStart, intv);
        }
        Partition p = new RdbPartition(tbsIndex, pinfo.partitionStart, pinfo.partitionEnd, value, pinfo.dir+"/"+tableDefinition.getName());
        intv.add(value, p);
    }	

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByValue(int tbsIndex, Object value) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, value,  tableDefinition.getName());             
        pcache.add(value, p);
    }   

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByNone(int tbsIndex) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, null, tableDefinition.getName());             
        pcache.add(null, p);
    }   

    @Override
    protected Partition createPartitionByTime(PartitionInfo pinfo, Object value) throws IOException {
        String tblName = tableDefinition.getName();
        RDBFactory rdbFactory = RDBFactory.getInstance(ydb.getName());
        File f= new File(tablespace.getDataDir()+"/"+pinfo.dir+"/");

        if(!f.exists()) {
            f.mkdirs();
        }

        YRDB rdb = rdbFactory.getRdb(f.getAbsolutePath(), true);
        byte[] bvalue = null;
        if(value!=null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);               
        }
        TablespaceRecord tr = tablespace.createTablePartitionRecord(ydb.getName(), tblName, pinfo.dir, bvalue);

        rdbFactory.dispose(rdb);
        return new RdbPartition(tr.getTbsIndex(), pinfo.partitionStart, pinfo.partitionEnd, value, pinfo.dir);			
    }

    @Override
    protected Partition createPartition(Object value) throws IOException {
        String tblName = tableDefinition.getName();
        byte[] bvalue = null;
        if(value!=null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);
        }
        TablespaceRecord tr = tablespace.createTablePartitionRecord(ydb.getName(), tblName, null, bvalue);
        return new RdbPartition(tr.getTbsIndex(), Long.MIN_VALUE, Long.MAX_VALUE, value, tableDefinition.getName());			
    }

    @Override
    protected HistogramInfo createHistogramByTime(PartitionInfo pinfo, String columnName) throws IOException {
        return null;
    }

    @Override
    protected HistogramInfo createHistogram(String columnName)  throws IOException {
        return null;
    }
}

class Interval {
    long start;
    long end;
    String dir;
    Set<Object> values=Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());

    public Interval(long start, long end) {
        this.start=start;
        this.end=end;
    }

    @Override
    public String toString() {
        return "["+TimeEncoding.toString(start)+" - "+TimeEncoding.toString(end)+"] dir:"+dir+" values: "+values;
    }
}
