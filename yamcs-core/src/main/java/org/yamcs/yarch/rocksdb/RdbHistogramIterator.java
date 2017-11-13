package org.yamcs.yarch.rocksdb;


import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.yamcs.TimeInterval;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.HistogramSegment;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

import static org.yamcs.yarch.HistogramSegment.segmentStart;
/**
 * 
 * @author nm
 *
 */
class RdbHistogramIterator implements Iterator<HistogramRecord> {

    private Iterator<PartitionManager.Interval> intervalIterator;
    private AscendingRangeIterator segmentIterator;

    private PriorityQueue<HistogramRecord> records = new PriorityQueue<>();

    private final TimeInterval interval;
    private final long mergeTime;
    private final Tablespace tablespace;

    YarchDatabaseInstance ydb;
    TableDefinition tblDef;
    YRDB rdb;


    Logger log;
    String colName;
    boolean stopReached = false;

    //FIXME: mergeTime does not merge records across partitions or segments
    public RdbHistogramIterator(Tablespace tablespace, YarchDatabaseInstance ydb, TableDefinition tblDef, String colName, TimeInterval interval, long mergeTime) throws RocksDBException {
        this.interval = interval;
        this.mergeTime = mergeTime;
        this.ydb = ydb;
        this.tblDef = tblDef;
        this.colName = colName;
        this.tablespace = tablespace;

        PartitionManager partMgr = RdbStorageEngine.getInstance().getPartitionManager(tblDef);
        intervalIterator = partMgr.intervalIterator(interval.getStart());
        log = LoggingUtils.getLogger(this.getClass(), ydb.getName(), tblDef);
        readNextPartition();
    }

    private void readNextPartition() throws RocksDBException {
        while(intervalIterator.hasNext()) {
            PartitionManager.Interval intv = intervalIterator.next();
            if(interval.hasStop() && intv.getStart()>interval.getStop()) {
                break; //finished
            }

            if(rdb!=null) {
                tablespace.dispose(rdb);
            }

            RdbHistogramInfo hist = (RdbHistogramInfo) intv.getHistogram(colName);
            rdb = tablespace.getRdb(hist, false);
            long segStart = interval.hasStart()?segmentStart(interval.getStart()):0;
            byte[] dbKeyStart = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
            ByteArrayUtils.encodeLong(segStart, dbKeyStart, 4);

            boolean strictEnd;
            byte[] dbKeyStop;
            if(interval.hasStop()) {
                strictEnd = false;
                dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex, new byte[12], 0);
                ByteArrayUtils.encodeLong(segStart, dbKeyStop, 4);
            } else {
                dbKeyStop = ByteArrayUtils.encodeInt(hist.tbsIndex+1, new byte[12], 0);
                strictEnd = true;
            }



            segmentIterator = new AscendingRangeIterator(rdb.newIterator(), dbKeyStart, false, dbKeyStop, strictEnd);

            if(segmentIterator.isValid()) {
                readNextSegments();
                break;
            }
            tablespace.dispose(rdb);
        }
    }

    //reads all the segments with the same sstart time
    private void readNextSegments() throws RocksDBException {
        ByteBuffer bb = ByteBuffer.wrap(segmentIterator.key());
        long sstart = bb.getLong();
        if(sstart==Long.MAX_VALUE) {
            readNextPartition();
            return;
        }

        while(true) {
            boolean beyondStop = addRecords(segmentIterator.key(), segmentIterator.value());
            if(beyondStop) {
                stopReached = true;
            }

            segmentIterator.next();
            if(!segmentIterator.isValid()) {
                readNextPartition();
                break;
            }
            bb = ByteBuffer.wrap(segmentIterator.key());
            long g = bb.getLong();
            if(g!=sstart) {
                break;
            }
        }
    }       

    public void close() {
        if(rdb!=null) {
            tablespace.dispose(rdb);
            rdb = null;
        }
    }

    //add all records from this segment into the queue 
    // if the stop has been reached add only partially the records, return true
    private boolean addRecords(byte[] key, byte[] val) {
        ByteBuffer kbb = ByteBuffer.wrap(key);
        long sstart = kbb.getLong();
        byte[] columnv = new byte[kbb.remaining()];
        kbb.get(columnv);
        ByteBuffer vbb = ByteBuffer.wrap(val);
        HistogramRecord r = null;
        while(vbb.hasRemaining()) {
            long start = sstart*HistogramSegment.GROUPING_FACTOR + vbb.getInt();
            long stop = sstart*HistogramSegment.GROUPING_FACTOR + vbb.getInt();              
            int num = vbb.getShort();
            if((interval.hasStart()) && (stop<interval.getStart())) {
                continue;
            }
            if((interval.hasStop()) && (start>interval.getStop())) {
                if(r!=null) {
                    records.add(r);
                }
                return true;
            }
            if(r==null) {
                r = new HistogramRecord(columnv, start, stop, num);
            } else {
                if(start-r.getStop()<mergeTime) {
                    r = new HistogramRecord(r.getColumnv(), r.getStart(), stop, r.getNumTuples()+num);
                } else {
                    records.add(r);
                    r = new HistogramRecord(columnv, start, stop, num);
                }
            }
        }
        if(r!=null) {
            records.add(r);
        }
        return false;
    }

    @Override
    public boolean hasNext() {
        return !records.isEmpty();
    }

    @Override
    public HistogramRecord next() {
        if(records.isEmpty()) {
            throw new NoSuchElementException();
        }
        HistogramRecord r = records.poll();
        if(records.isEmpty() && !stopReached) {
            try {
                readNextSegments();
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
        return r;
    }
}
