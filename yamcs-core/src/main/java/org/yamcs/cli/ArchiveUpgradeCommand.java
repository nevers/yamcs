package org.yamcs.cli;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;
import org.yamcs.YConfiguration;
import org.yamcs.oldparchive.ParameterGroupIdDb;
import org.yamcs.oldparchive.ParameterIdDb;
import org.yamcs.parameterarchive.ParameterArchiveV2;
import org.yamcs.parameterarchive.SegmentKey;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeInterval;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.oldrocksdb.HistogramRebuilder;
import org.yamcs.yarch.oldrocksdb.RdbPartition;
import org.yamcs.yarch.oldrocksdb.RdbPartitionManager;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord.Type;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;


@Parameters(commandDescription = "Upgrade tables to latest format. It can only be done when the Yamcs server is not running.")
public class ArchiveUpgradeCommand extends Command {

    @Parameter(names="--instance", description="yamcs instance", required=true)
    String yamcsInstance;
    FileWriter filesToRemove;
    int filesToRemoveCount;

    public ArchiveUpgradeCommand(ArchiveCli archiveCli) {
        super("upgrade", archiveCli);
    }
    @Override
    public void execute() throws Exception {
        RocksDB.loadLibrary();
        org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance().setIgnoreVersionIncompatibility(true);
        if(yamcsInstance!=null) {
            if(!YarchDatabase.instanceExistsOnDisk(yamcsInstance)) {
                throw new ParameterException("Archive instance '"+yamcsInstance+"' does not exist");
            }
            upgradeInstance(yamcsInstance);
        } else {
            List<String> instances =  YConfiguration.getConfiguration("yamcs").getList("instances");
            for(String instance:instances) {
                upgradeInstance(instance);
            }
        }
    }
    private void upgradeInstance(String instance) throws Exception {
        String f = "/tmp/"+instance+"-cleanup.sh";
        filesToRemove = new FileWriter(f);
        upgradeYarchTables(instance);
        upgradeParameterArchive(instance);
        console.println("\n*************************************\n");
        console.println("Instance "+instance+" has been upgraded");
        if(filesToRemoveCount>0) {
            filesToRemove.write("find '"+YarchDatabase.getInstance(instance).getRoot()+"' -type d -empty -delete\n");
            console.println("A number of files are not required anymore after upgrade and a script to remove them has been created in "+f);
            console.println("Please execute the script after making sure that eveything is ok");
        }
        filesToRemove.close();
    }

    private void upgradeYarchTables(String instance) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance, true);
        for(TableDefinition tblDef: ydb.getTableDefinitions()) {
            if(tblDef.getFormatVersion()==0) {
                upgrade0_1(ydb, tblDef);
                tblDef.changeFormatDefinition(1);
            } 
            if(tblDef.getFormatVersion()==1){
                upgrade1_2(ydb, tblDef);
                tblDef.changeFormatDefinition(TableDefinition.CURRENT_FORMAT_VERSION);
            }
            if(tblDef.getStorageEngineName().equals(YarchDatabase.OLD_RDB_ENGINE_OLD_NAME)) {
                upgradeRocksDBTable(ydb, tblDef);
                tblDef.changeStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);
            } 
        }
    }

    private void upgrade0_1(YarchDatabaseInstance ydb, TableDefinition tblDef) throws Exception  {
        log.info("upgrading table {}/{} from version 0 to version 1", ydb.getName(), tblDef.getName());
        if("pp".equals(tblDef.getName())) {
            changePpGroup(ydb, tblDef);
        }

        if(tblDef.hasHistogram()) {
            rebuildHistogram(ydb, tblDef);
        }
    }

    private void upgrade1_2(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        log.info("upgrading table {}/{} from version 1 to version 2", ydb.getName(), tblDef.getName());
        if("pp".equals(tblDef.getName())) {
            changeParaValueType(tblDef);
        }
    }

    static void changeParaValueType(TableDefinition tblDef) {
        TupleDefinition valueDef = tblDef.getValueDefinition();
        List<ColumnDefinition> l= valueDef.getColumnDefinitions();
        for(int i=0; i<l.size(); i++) {
            ColumnDefinition cd = l.get(i);
            if("org.yamcs.protobuf.Pvalue$ParameterValue".equals(cd.getType().name())) {
                ColumnDefinition cd1 = new ColumnDefinition(cd.getName(), DataType.PARAMETER_VALUE);
                l.set(i,  cd1);
            }
        }
    }

    private void changePpGroup(YarchDatabaseInstance ydb, TableDefinition tblDef) {
        if(tblDef.getColumnDefinition("ppgroup") == null) {
            log.info("Table {}/{} has no ppgroup column", ydb.getName(), tblDef.getName());
            return;
        }
        log.info("Renaming ppgroup -> group column in table {}/{}", ydb.getName(), tblDef.getName());
        tblDef.renameColumn("ppgroup", "group");
    }

    private void rebuildHistogram(YarchDatabaseInstance ydb, TableDefinition tblDef) throws InterruptedException, ExecutionException, YarchException {
        HistogramRebuilder hrb = new HistogramRebuilder(ydb, tblDef.getName());
        hrb.rebuild(new TimeInterval()).get();
    }

    private void upgradeRocksDBTable(YarchDatabaseInstance ydb, TableDefinition tblDef) throws InterruptedException, IOException {
        log.info("Upgrading {} to new RocksDB storage engine", tblDef.getName());
        RdbStorageEngine newRse = RdbStorageEngine.getInstance();
        newRse.createTable(ydb, tblDef);

        org.yamcs.yarch.oldrocksdb.RdbStorageEngine oldRse = org.yamcs.yarch.oldrocksdb.RdbStorageEngine.getInstance();
        Stream stream = oldRse.newTableReaderStream(ydb, tblDef, true, false);

        TableWriter tw = newRse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        stream.addSubscriber(tw);
        Semaphore semaphore = new Semaphore(0);
        AtomicInteger count = new AtomicInteger();
        stream.addSubscriber(new StreamSubscriber() {
            int c = 0;
            @Override
            public void streamClosed(Stream stream) {
                count.set(c);
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                c++;
                if(c%10000 ==0) {
                    log.info("{} saved {} tuples", tblDef.getName(), c);
                }
            }
        });
        stream.start();
        semaphore.acquire();
        log.info("{} upgrade finished: converted {} tuples", tblDef.getName(), count);
        RdbPartitionManager pm = oldRse.getPartitionManager(tblDef);
        for(Partition p: pm.getPartitions()) {
            RdbPartition rp = (RdbPartition) p;
            filesToRemove.write("rm -rf '"+ydb.getRoot()+"/"+rp.getDir()+"'\n");
            filesToRemoveCount++;
        }
    }

    private void upgradeParameterArchive(String instance) throws Exception {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        File f = new File(ydb.getRoot()+"/ParameterArchive");
        if(!f.exists()) {
            return;
        }
        log.info("{}: upgrading parameter archive");
        ParameterArchiveV2 newparch = new ParameterArchiveV2(instance);
        org.yamcs.oldparchive.ParameterArchive oldparch = new org.yamcs.oldparchive.ParameterArchive(instance);
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        Tablespace tablespace = rse.getTablespace(ydb.getTablespaceName());
        ParameterIdDb paraId = oldparch.getParameterIdDb();
        log.debug("creating parameter ids in the tablespace {} ", tablespace.getName());
        Map<Integer, Integer> oldToNewParaId = new HashMap<>();

        TablespaceRecord.Builder trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_DATA)
                .setParameterFqn(org.yamcs.parameterarchive.ParameterIdDb.TIME_PARAMETER_FQN);
        TablespaceRecord tr = tablespace.createMetadataRecord(instance, trb);
        oldToNewParaId.put(ParameterIdDb.TIMESTAMP_PARA_ID, tr.getTbsIndex());
       
        for(Map.Entry<String, Map<Integer, Integer>> e: paraId.getMap().entrySet()) {
            String fqn = e.getKey();
            for(Map.Entry<Integer, Integer> e1: e.getValue().entrySet()) {
                int paraType = e1.getKey();
                int paramId = e1.getValue();
                trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_DATA)
                        .setParameterFqn(fqn).setParameterType(paraType);
                tr = tablespace.createMetadataRecord(instance, trb);
                oldToNewParaId.put(paramId, tr.getTbsIndex());
            }
        }
        log.debug("creating parameter groups in the tablespace {} ", tablespace.getName());
        ParameterGroupIdDb paraGroupId = oldparch.getParameterGroupIdDb();
        Map<SortedIntArray, Integer> oldGroups = paraGroupId.getMap();
        Map<Integer, SortedIntArray> newGroups = new HashMap<>();
        for(Map.Entry<SortedIntArray, Integer> e: oldGroups.entrySet()) {
            SortedIntArray s = new SortedIntArray();
            e.getKey().forEach(x-> s.insert(oldToNewParaId.get(x)));
            newGroups.put(e.getValue(), s);
        }
        trb = TablespaceRecord.newBuilder().setType(Type.PARCHIVE_PGID2PG);
        tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        int pgidTbsIndex = tr.getTbsIndex();
        for(Map.Entry<Integer, SortedIntArray> e: newGroups.entrySet()) {
            byte[] key = new byte[TBS_INDEX_SIZE+4];
            ByteArrayUtils.encodeInt(pgidTbsIndex, key, 0);
            ByteArrayUtils.encodeInt(e.getKey(), key, TBS_INDEX_SIZE);
            tablespace.putData(key, e.getValue().encodeToVarIntArray());
        }            
        log.debug("migrating the data");
        int segCount = 0;
        for(org.yamcs.oldparchive.ParameterArchive.Partition oldpart:oldparch.getPartitions()) {
            try(RocksIterator it=oldparch.getIterator(oldpart)) {
                it.seekToFirst();
                while(it.isValid()) {
                    org.yamcs.oldparchive.SegmentKey oldkey = org.yamcs.oldparchive.SegmentKey.decode(it.key());
                    int newparaid = oldToNewParaId.get(oldkey.getParameterId());
                    SegmentKey newkey = new SegmentKey(newparaid, oldkey.getParameterGroupId(), oldkey.getSegmentStart(), oldkey.getType());
                    byte[] val = it.value();
                    org.yamcs.parameterarchive.ParameterArchiveV2.Partition newpart = newparch.createAndGetPartition(oldkey.getSegmentStart());
                    tablespace.getRdb(newpart.getPartitionDir(), false).getDb().put(newkey.encode(), val);
                    segCount++;
                    if(segCount%1000==0) {
                        log.info("{}:ParameterArchive migrated {} segments", instance, segCount);
                    }
                    it.next();
                }
            }
        }
        log.info("{}:ParameterArchive migration finished, migrated {} segments", instance, segCount);
        
        
        File f1 = new File(ydb.getRoot()+"/ParameterArchive.old");
        f.renameTo(f1);
        filesToRemove.write("rm -rf "+f1.getAbsolutePath()+"\n");
    }
}