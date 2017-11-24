package org.yamcs.parameterarchive;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Future;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.PartitionedTimeInterval;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.TimePartitionInfo;
import org.yamcs.yarch.TimePartitionSchema;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.oldrocksdb.StringColumnFamilySerializer;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TimeBasedPartition;
import org.yamcs.yarch.streamsql.NotSupportedException;

import com.google.common.util.concurrent.AbstractService;
import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

public class ParameterArchive  extends AbstractService {
    static final String CF_NAME_DATA_PREFIX = "data_";

    public static final boolean STORE_RAW_VALUES = true; 

    private final Logger log = LoggerFactory.getLogger(ParameterArchive.class);
    private ParameterIdDb parameterIdMap;
    private ParameterGroupIdDb parameterGroupIdMap;
    private Tablespace tablespace;

    //if partitioned by time the spec specifies which schema to use (e.g. YYYY or YYYY/DOY)
    TimePartitionSchema partitioningSchema;
    //the tbsIndex used to encode partition information
    //even if it's not partitioned by time, we still store one partition
    int partitionTbsIndex;

    private final String yamcsInstance;

    private PartitionedTimeInterval<Partition> partitions = new PartitionedTimeInterval<>();
    SegmentEncoderDecoder vsEncoder = new SegmentEncoderDecoder();


    final TimeService timeService;
    private BackFiller backFiller;
    private RealtimeArchiveFiller realtimeFiller;
    static StringColumnFamilySerializer cfSerializer = new StringColumnFamilySerializer();

    public ParameterArchive(String instance, Map<String, Object> args) throws IOException, RocksDBException {
        this.yamcsInstance = instance;
        this.timeService = YamcsServer.getTimeService(instance);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        tablespace = RdbStorageEngine.getInstance().getTablespace(ydb.getTablespaceName());

        TablespaceRecord.Type trType = TablespaceRecord.Type.PARCHIVE_PINFO;
        List<TablespaceRecord> trl = tablespace.filter(trType, yamcsInstance, trb-> true);
        if(trl.size()>1) {
            throw new DatabaseCorruptionException("More than one tablespace record of type "
                    +trType.name()+" for instance "+instance);
        }
        parameterIdMap = new ParameterIdDb(yamcsInstance, tablespace);
        parameterGroupIdMap = new ParameterGroupIdDb(yamcsInstance, tablespace);

        TablespaceRecord tr;
        if(trl.isEmpty()) { //new database
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(trType);
            tr = tablespace.createRecord(yamcsInstance, trb);
            partitionTbsIndex = tr.getTbsIndex();

        } else {
            tr = trl.get(0);
            partitionTbsIndex = tr.getTbsIndex();
            readPartitions();
        }

        if(args!=null) {
            processConfig(args);
        } else {
            backFiller = new BackFiller(this, null);
        }
    }

    public ParameterArchive(String instance) throws RocksDBException, IOException {
        this(instance, null);
    }


    private void processConfig(Map<String, Object> args) {
        for(String s:args.keySet()) {
            if("backFiller".equals(s)) {
                Map<String, Object> backFillerConfig = YConfiguration.getMap(args, s);
                boolean backFillerEnabled = true;
                log.debug("backFillerConfig: {}", backFillerConfig);
                if(backFillerConfig.containsKey("enabled")) {
                    backFillerEnabled  = YConfiguration.getBoolean(backFillerConfig, "enabled");
                }
                if(backFillerEnabled) {
                    backFiller = new BackFiller(this, backFillerConfig);
                }
            } else if("realtimeFiller".equals(s)) {
                Map<String, Object> realtimeFillerConfig = YConfiguration.getMap(args, s);
                boolean realtimeFillerEnabled = YConfiguration.getBoolean(realtimeFillerConfig, "enabled", false);
                log.debug("realtimeFillerConfig: {}", realtimeFillerConfig);
                if(realtimeFillerEnabled) {
                    realtimeFiller = new RealtimeArchiveFiller(this, realtimeFillerConfig);
                }
            } else {
                throw new ConfigurationException("Unkwnon keyword '"+s+"' in parameter archive configuration: "+args);
            }
        }
    }

    private void readPartitions() throws IOException, RocksDBException {
        YRDB db= tablespace.getRdb();
        byte[] range = new byte[TBS_INDEX_SIZE];
        ByteArrayUtils.encodeInt(partitionTbsIndex, range, 0);

        try(AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, false, range, false)) {
            while(it.isValid()) {
                TimeBasedPartition tbp = TimeBasedPartition.parseFrom(it.value());
                Partition p = new Partition(tbp.getPartitionStart(), tbp.getPartitionEnd(), tbp.getPartitionDir());
                p = partitions.insert(p, 0);
                if(p==null) {
                    throw new DatabaseCorruptionException("Partition "+p+" overlaps with existing partitions");
                }
            }
        }
    }

    static long decodePartitionId(String prefix, String cfname) {
        try {
            return Long.parseLong(cfname.substring(prefix.length()), 16);
        } catch (NumberFormatException e) {
            throw new ParameterArchiveException("Cannot decode partition id from column family: "+cfname);
        }
    }

    public ParameterIdDb getParameterIdDb() {
        return parameterIdMap;
    }

    public ParameterGroupIdDb getParameterGroupIdDb() {
        return parameterGroupIdMap;
    }


    public String getYamcsInstance() {
        return yamcsInstance;
    }
    
    public void writeToArchive(PGSegment pgs) throws RocksDBException, IOException {
        Partition p = createAndGetPartition(pgs.getSegmentStart());
        try (WriteBatch writeBatch = new WriteBatch();
                WriteOptions wo = new WriteOptions()) {
            writeToBatch(writeBatch, p, pgs);
            tablespace.getRdb(p.partitionDir, false).getDb().write(wo, writeBatch);
        }
    }


    public void writeToArchive(long segStart, Collection<PGSegment> pgList) throws RocksDBException, IOException {
        Partition p = createAndGetPartition(segStart);
        try (WriteBatch writeBatch = new WriteBatch();
                WriteOptions wo = new WriteOptions()) {

            for(PGSegment pgs: pgList) {
                assert(segStart==pgs.getSegmentStart());
                writeToBatch(writeBatch, p, pgs);
            }
            tablespace.getRdb(p.partitionDir, false).getDb().write(wo, writeBatch);
        }
    }

    private void writeToBatch(WriteBatch writeBatch, Partition p, PGSegment pgs) throws RocksDBException {
        //write the time segment
        SortedTimeSegment timeSegment = pgs.getTimeSegment();
        byte[] timeKey = new SegmentKey(ParameterIdDb.TIMESTAMP_PARA_ID, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] timeValue = vsEncoder.encode(timeSegment);
        writeBatch.put(timeKey, timeValue);

        //and then the consolidated value segments
        List<BaseSegment> consolidated = pgs.getConsolidatedValueSegments();
        List<BaseSegment> consolidatedRawValues = pgs.getConsolidatedRawValueSegments();
        List<ParameterStatusSegment> satusSegments = pgs.getConsolidatedParameterStatusSegments();

        for(int i=0; i<consolidated.size(); i++) {
            BaseSegment vs = consolidated.get(i);
            int parameterId = pgs.getParameterId(i);
            String pname = parameterIdMap.getParameterbyId(parameterId);
            if(vs.size()!=timeSegment.size()) {
                throw new IllegalArgumentException("Trying to write to archive an engineering value segment whose size ("+vs.size()+") is different than the time segment ("+timeSegment.size()+") "
                        +"for parameterId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
            }
            byte[] engKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_ENG_VALUE).encode();
            byte[] engValue = vsEncoder.encode(vs);
            writeBatch.put(engKey, engValue);

            if(STORE_RAW_VALUES && consolidatedRawValues!=null) {
                BaseSegment rvs = consolidatedRawValues.get(i);
                if(rvs!=null) {
                    if(rvs.size()!=timeSegment.size()) {
                        throw new IllegalArgumentException("Trying to write to archive an raw value segment whose size ("+rvs.size()+") is different than the time segment ("+timeSegment.size()+") "
                                +"for parameterId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
                    }
                    byte[] rawKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_RAW_VALUE).encode();
                    byte[] rawValue = vsEncoder.encode(rvs);
                    writeBatch.put(rawKey, rawValue);

                }
            }
            ParameterStatusSegment pss = satusSegments.get(i);
            if(pss.size()!=timeSegment.size()) {
                throw new IllegalArgumentException("Trying to write to archive an parameter status segment whose size ("+pss.size()+") is different than the time segment ("+timeSegment.size()+") "
                        +"for parameterId: "+parameterId+"("+pname+") and segment: ["+TimeEncoding.toString(timeSegment.getSegmentStart())+" - " + TimeEncoding.toString(timeSegment.getSegmentEnd())+"]");
            }
            byte[] pssKey = new SegmentKey(parameterId, pgs.getParameterGroupId(), pgs.getSegmentStart(), SegmentKey.TYPE_PARAMETER_STATUS).encode();
            byte[] pssValue = vsEncoder.encode(pss);
            writeBatch.put(pssKey, pssValue);
        }
    }


    /**
     * get partition for id, creating it if it doesn't exist
     * @param partitionId
     * @return
     * @throws RocksDBException 
     */
    private Partition createAndGetPartition(long segStart) throws RocksDBException {        
        synchronized(partitions) {
            Partition p = partitions.getFit(segStart);
            if(p==null) {
                TimePartitionInfo pinfo = partitioningSchema.getPartitionInfo(segStart);
                p = new Partition(pinfo.getStart(), pinfo.getEnd(), pinfo.getDir());
                p = partitions.insert(p, 60000L);
                assert p!=null;
            }
            return p;
        }
    }

    public Future<?> reprocess(long start, long stop) {
        log.debug("Scheduling a reprocess for interval [{} - {}]", TimeEncoding.toString(start), TimeEncoding.toString(stop));
        if(backFiller==null) {
            throw new ConfigurationException("backFilling is not enabled");
        }
        return backFiller.scheduleFillingTask(start, stop);
    }


    /** 
     * a copy of the partitions from start to stop inclusive
     * @param startPartitionId
     * @param stopPartitionId
     * @return a sorted list of partitions 
     */
    public List<Partition> getPartitions(long start, long stop, boolean ascending) {
        List<Partition> r = new ArrayList<>();
        Iterator<Partition> it;
        if(ascending) {
            it = partitions.overlappingIterator(new TimeInterval(start, stop));
        } else {
            it = partitions.overlappingReverseIterator(new TimeInterval(start, stop));
        }
        while(it.hasNext()) {
            r.add(it.next());
        }
        return r;
    }


    @Override
    protected void doStart() {
        if(backFiller!=null) {
            backFiller.start();
        }
        if(realtimeFiller!=null) {
            realtimeFiller.start();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        log.debug("Stopping ParameterArchive service for instance {}", yamcsInstance);

        if(backFiller!=null) {
            backFiller.stop();
        }

        if(realtimeFiller!=null) {
            realtimeFiller.stop();
        }
        notifyStopped();
    }


    public void printKeys(PrintStream out) throws DecodingException, RocksDBException, IOException {
        out.println("pid\t pgid\t type\tSegmentStart\tcount\tsize\tstype");
        SegmentEncoderDecoder decoder = new SegmentEncoderDecoder();
        for(Partition p:partitions) {
            try(RocksIterator it = getIterator(p)) {
                it.seekToFirst();
                while(it.isValid()) {
                    SegmentKey key = SegmentKey.decode(it.key());
                    byte[] v = it.value();
                    BaseSegment s;
                    s = decoder.decode(it.value(), key.segmentStart);
                    out.println(key.parameterId+"\t "+key.parameterGroupId+"\t "+key.type+"\t"+TimeEncoding.toString(key.segmentStart)+"\t"+s.size()+"\t"+v.length+"\t"+s.getClass().getSimpleName());
                    it.next();
                }
            }
        }
    }

    /**
     * Delete all partitions that overlap with [start, stop) segment.
     * @param start
     * @param stop
     * @throws RocksDBException 
     * @return all the partitions removed
     */
    public List<Partition> deletePartitions(long start, long stop) throws RocksDBException {
        //List<Partition> parts = getPartitions(start, stop, true);
        throw new UnsupportedOperationException("operation not supported");
    }


    public RocksIterator getIterator(Partition p) throws RocksDBException, IOException {
        return tablespace.getRdb(p.partitionDir, false).newIterator();
    }

    public SortedTimeSegment getTimeSegment(Partition p, long segmentStart,  int parameterGroupId) throws RocksDBException, IOException {
        byte[] timeKey = new SegmentKey(ParameterIdDb.TIMESTAMP_PARA_ID, parameterGroupId, segmentStart, SegmentKey.TYPE_ENG_VALUE).encode();
        byte[] tv = tablespace.getRdb(p.partitionDir, false).get(timeKey);
        if(tv==null) {
            return null;
        }
        try {
            return (SortedTimeSegment) vsEncoder.decode(tv, segmentStart);
        } catch (DecodingException e) {
            throw new DatabaseCorruptionException(e);
        }
    }

    Partition getPartitions(long partitionId) {
        synchronized(partitions) {
            return partitions.getFit(partitionId);
        }
    }

    static class Partition extends TimeInterval {
        public static final int NUMBITS_MASK=31; //2^31 millisecons =~ 24 days per partition    
        public static final long TIMESTAMP_MASK = (0xFFFFFFFF>>>(32-NUMBITS_MASK));
        public static final long PARTITION_MASK = ~TIMESTAMP_MASK;

        final String partitionDir;

        Partition(long start, long end, String dir) {
            super(start, end);
            this.partitionDir = dir;
        }        


        static long getPartitionId(long instant) {
            return instant & PARTITION_MASK;
        }

        static long getPartitionStart(long instant) {
            return getPartitionId(instant);
        }

        public static long getPartitionEnd(long partitionId) {
            return partitionId  | TIMESTAMP_MASK;
        }

        public String toString() {
            return "partition: "+partitionDir+"["+TimeEncoding.toString(getStart())+" - "+TimeEncoding.toString(getPartitionEnd(getEnd()))+"]";
        }
    }
}
