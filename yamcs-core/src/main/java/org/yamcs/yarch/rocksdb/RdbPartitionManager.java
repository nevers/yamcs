package org.yamcs.yarch.rocksdb;

import java.io.IOException;

import org.rocksdb.RocksDBException;
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
     * @throws IOException 
     * @throws RocksDBException 
     */
    public void readPartitions() throws RocksDBException, IOException {
        ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
        for(TablespaceRecord tr: tablespace.getTablePartitions(ydb.getName(), tableDefinition.getName())) {

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
        Partition p = new RdbPartition(tbsIndex, pinfo.partitionStart, pinfo.partitionEnd, null, pinfo.dir);
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
        Partition p = new RdbPartition(tbsIndex, pinfo.partitionStart, pinfo.partitionEnd, value, pinfo.dir);
        intv.add(value, p);
    }	

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByValue(int tbsIndex, Object value) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, value,  null);             
        pcache.add(value, p);
    }   

    /** 
     * Called at startup when reading existing partitions from disk
     */
    private void addPartitionByNone(int tbsIndex) {
        Partition p = new RdbPartition(tbsIndex, Long.MIN_VALUE, Long.MAX_VALUE, null, null);             
        pcache.add(null, p);
    }   

    @Override
    protected Partition createPartitionByTime(PartitionInfo pinfo, Object value) throws IOException {
        byte[] bvalue = null;
        if(value!=null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);               
        }
        TablespaceRecord tr;
        try {
            tr = tablespace.createTablePartitionRecord(ydb.getName(), tableDefinition.getName(), pinfo.dir, bvalue);
        } catch (RocksDBException e) {
           throw new IOException(e);
        }
        return new  RdbPartition(tr.getTbsIndex(), pinfo.partitionStart, pinfo.partitionEnd, value, pinfo.dir);
    }

    @Override
    protected Partition createPartition(Object value) throws IOException {
        String tblName = tableDefinition.getName();
        byte[] bvalue = null;
        if(value!=null) {
            ColumnValueSerializer cvs = new ColumnValueSerializer(tableDefinition);
            bvalue = cvs.objectToByteArray(value);
        }
        TablespaceRecord tr;
        try {
            tr = tablespace.createTablePartitionRecord(ydb.getName(), tblName, null, bvalue);
            return new RdbPartition(tr.getTbsIndex(), Long.MIN_VALUE, Long.MAX_VALUE, value, null);
        } catch (RocksDBException e) {
           throw new IOException(e);
        }
        			
    }

    @Override
    protected HistogramInfo createHistogramByTime(PartitionInfo pinfo, String columnName) throws IOException {
        try {
            TablespaceRecord tr = tablespace.createHistogramRecord(ydb.getName(), tableDefinition.getName(), columnName, pinfo.dir);
            return new RdbHistogramInfo(tr.getTbsIndex(), columnName, pinfo.dir);                       
        } catch (RocksDBException e) {
           throw new IOException(e);
        }       
    }

    @Override
    protected HistogramInfo createHistogram(String columnName)  throws IOException {
        try {
            TablespaceRecord tr = tablespace.createHistogramRecord(ydb.getName(), tableDefinition.getName(), columnName, null);
            return new RdbHistogramInfo(tr.getTbsIndex(), columnName, null);                       
        } catch (RocksDBException e) {
           throw new IOException(e);
        }      
    }
}
