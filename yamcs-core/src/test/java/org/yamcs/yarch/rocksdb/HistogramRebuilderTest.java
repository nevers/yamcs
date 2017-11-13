package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yamcs.TimeInterval;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.TableWriter.InsertMode;


public class HistogramRebuilderTest  extends YarchTestCase {
    String tblName = "HistogramRebuilderTest";
    TableDefinition tblDef;
    RdbStorageEngine rse;
    long t1 = TimeEncoding.parse("2016-12-16T00:00:00");
    
    @Before
    public void populate() throws Exception {
        String query="create table "+tblName+"(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name) partition by time(gentime) table_format=compressed";
        ydb.execute(query);
        
        tblDef= ydb.getTable(tblName);
        rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{1000L, 10, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{2000L, 20, "p1"}));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{3000L, 30, "p2"}));
        
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[]{t1, 30, "p2"}));
        tw.close();
    }
    
    @After
    public void dropTable() throws Exception {
        ydb.execute("drop table "+tblName);
    }
    
    @Test
    public void testDeleteValues() throws Exception {
        Tablespace tablespace = new Tablespace(ydb.getName(), (byte)1);
        TimeInterval interval = new TimeInterval();
        Iterator<HistogramRecord> iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        
        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb,tblName);
        
        rebuilder.deleteHistograms(new TimeInterval(1000L, 1000L));
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 1);
        
        rebuilder.rebuild(new TimeInterval(0, 2000)).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
    }

    @Test
    public void testRebuildAll() throws Exception {
        Tablespace tablespace = new Tablespace(ydb.getName(), (byte)1);
        TimeInterval interval = new TimeInterval();
        Iterator<HistogramRecord> iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 3);
        
        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb,  tblName);
        rebuilder.deleteHistograms(new TimeInterval());
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        assertNumElementsEqual(iter, 0);
        

        rebuilder.rebuild(new TimeInterval()).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval, 0);
        
        assertNumElementsEqual(iter, 3);
    }
    
    private void assertNumElementsEqual(Iterator<HistogramRecord> iter, int k) {
        int num =0;
        while(iter.hasNext()) {
            num++;
            iter.next();
        }
        assertEquals(k, num);
    }
    
    
    private RdbPartition getFirstPartition(long start) {
        Iterator<List<Partition>> it = rse.getPartitionManager(tblDef).iterator(start, null);
        if(!it.hasNext()) return null;
        return (RdbPartition) it.next().get(0);
    }
    
}
