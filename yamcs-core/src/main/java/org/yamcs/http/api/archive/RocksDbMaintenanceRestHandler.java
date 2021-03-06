package org.yamcs.http.api.archive;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.protobuf.Archive.ListRocksDbDatabasesResponse;
import org.yamcs.protobuf.Archive.ListRocksDbTablespacesResponse;
import org.yamcs.protobuf.Archive.RocksDbDatabaseInfo;
import org.yamcs.protobuf.Archive.RocksDbTablespaceInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.yarch.BackupUtils;
import org.yamcs.yarch.rocksdb.RDBFactory;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class RocksDbMaintenanceRestHandler extends RestHandler {
    private static final Logger log = LoggerFactory.getLogger(RocksDbMaintenanceRestHandler.class);

    @Route(path = "/api/archive/rocksdb/tablespaces", method = "GET")
    public void listTablespaces(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        List<RocksDbTablespaceInfo> unsorted = new ArrayList<>();
        RdbStorageEngine storageEngine = RdbStorageEngine.getInstance();
        for (Tablespace tblsp : storageEngine.getTablespaces().values()) {
            RocksDbTablespaceInfo.Builder tablespaceb = RocksDbTablespaceInfo.newBuilder()
                    .setName(tblsp.getName())
                    .setDataDir(tblsp.getDataDir());
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String dbPath : rdbf.getOpenDbPaths()) {
                RocksDbDatabaseInfo database = ArchiveHelper.toRocksDbDatabaseInfo(tblsp, dbPath);
                tablespaceb.addDatabase(database);
            }
            unsorted.add(tablespaceb.build());
        }

        ListRocksDbTablespacesResponse.Builder responseb = ListRocksDbTablespacesResponse.newBuilder();
        Collections.sort(unsorted, (t1, t2) -> t1.getName().compareTo(t2.getName()));
        responseb.addAllTablespace(unsorted);
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/archive/rocksdb/databases", method = "GET")
    public void listDatabases(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        List<RocksDbDatabaseInfo> unsorted = new ArrayList<>();
        RdbStorageEngine storageEngine = RdbStorageEngine.getInstance();
        for (Tablespace tblsp : storageEngine.getTablespaces().values()) {
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String dbPath : rdbf.getOpenDbPaths()) {
                RocksDbDatabaseInfo database = ArchiveHelper.toRocksDbDatabaseInfo(tblsp, dbPath);
                unsorted.add(database);
            }
        }

        ListRocksDbDatabasesResponse.Builder responseb = ListRocksDbDatabasesResponse.newBuilder();
        Collections.sort(unsorted, (db1, db2) -> {
            if (db1.getTablespace().equals(db2.getTablespace())) {
                return db1.getDbPath().compareTo(db2.getDbPath());
            } else {
                return db1.getTablespace().compareTo(db2.getTablespace());
            }
        });
        responseb.addAllDatabase(unsorted);
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/archive/rocksdb/:tablespace/properties", method = "GET")
    @Route(path = "/api/archive/rocksdb/:tablespace/properties/:dbpath*", method = "GET")
    public void getProperty(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);
        Tablespace tablespace = verifyTablespace(req);
        String dbpath = req.hasRouteParam("dbpath") ? req.getRouteParam("dbpath") : null;

        RDBFactory rdbFactory = tablespace.getRdbFactory();
        YRDB yrdb;
        if (dbpath == null) {
            yrdb = rdbFactory.getOpenRdb();
        } else {
            yrdb = rdbFactory.getOpenRdb(dbpath);
            if (yrdb == null) {
                yrdb = rdbFactory.getOpenRdb("/" + dbpath);
            }
        }
        if (yrdb == null) {
            if (dbpath == null) {
                throw new BadRequestException("Root database not open for tablespace " + tablespace.getName());
            } else {
                throw new BadRequestException("No open database " + dbpath + " for tablespace " + tablespace.getName());
            }
        }

        try {
            String s = yrdb.getProperites();
            CharBuffer props = CharBuffer.wrap(s);
            ByteBuf buf = ByteBufUtil.encodeString(req.getChannelHandlerContext().alloc(), props,
                    StandardCharsets.UTF_8);
            completeOK(req, MediaType.PLAIN_TEXT, buf);
        } catch (RocksDBException e) {
            log.error("Error when getting database properties", e);
            completeWithError(req, new InternalServerErrorException(e));
        } finally {
            rdbFactory.dispose(yrdb);
        }
    }

    @Route(path = "/api/archive/rocksdb/:tablespace/compact", method = "GET", offThread = true)
    @Route(path = "/api/archive/rocksdb/:tablespace/compact/:dbpath*", method = "GET", offThread = true)
    public void compactDatabase(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);
        Tablespace tablespace = verifyTablespace(req);
        String dbpath = req.hasRouteParam("dbpath") ? req.getRouteParam("dbpath") : null;

        RDBFactory rdbFactory = tablespace.getRdbFactory();
        YRDB yrdb;
        if (dbpath == null) {
            yrdb = rdbFactory.getOpenRdb();
        } else {
            yrdb = rdbFactory.getOpenRdb(dbpath);
            if (yrdb == null) {
                yrdb = rdbFactory.getOpenRdb("/" + dbpath);
            }
        }
        if (yrdb == null) {
            if (dbpath == null) {
                throw new BadRequestException("Root database not open for tablespace " + tablespace.getName());
            } else {
                throw new BadRequestException("No open database " + dbpath + " for tablespace " + tablespace.getName());
            }
        }

        try {
            yrdb.getDb().compactRange();
            completeOK(req);
        } catch (RocksDBException e) {
            log.error("Error when compacting database", e);
            completeWithError(req, new InternalServerErrorException(e));
        } finally {
            rdbFactory.dispose(yrdb);
        }
    }

    @Route(path = "/api/archive/rocksdb/list", method = "GET")
    public void listOpenDbs(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);
        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        StringBuilder sb = new StringBuilder();
        for (Tablespace tblsp : rse.getTablespaces().values()) {
            sb.append("Tablespace: ").append(tblsp.getName()).append("\n");
            sb.append("  dataDir: ").append(tblsp.getDataDir()).append("\n");
            sb.append("  open databases: ").append("\n");
            RDBFactory rdbf = tblsp.getRdbFactory();
            for (String s : rdbf.getOpenDbPaths()) {
                if (s.isEmpty()) {
                    s = "<root>";
                }
                sb.append("    ").append(s).append("\n");
            }
        }

        CharBuffer props = CharBuffer.wrap(sb.toString());
        ByteBuf buf = ByteBufUtil.encodeString(req.getChannelHandlerContext().alloc(), props, StandardCharsets.UTF_8);
        completeOK(req, MediaType.PLAIN_TEXT, buf);
    }

    @Route(path = "/api/archive/rocksdb/backup/:dbpath*", method = "POST")
    public void doBackup(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        Tablespace tablespace = verifyTablespace(req);
        String dbpath = req.hasRouteParam("dbpath") ? req.getRouteParam("dbpath") : null;

        String backupDir = req.getQueryParameter("backupDir");
        if (backupDir == null) {
            throw new BadRequestException("No backup directory specified");
        }
        try {
            BackupUtils.verifyBackupDirectory(backupDir, false);
        } catch (Exception e1) {
            throw new BadRequestException(e1.getMessage());
        }

        RDBFactory rdbFactory = tablespace.getRdbFactory();

        CompletableFuture<Void> cf = (dbpath == null) ? rdbFactory.doBackup(backupDir)
                : rdbFactory.doBackup(dbpath, backupDir);

        cf.whenComplete((r, e) -> {
            if (e != null) {
                completeWithError(req, new InternalServerErrorException(e));
            } else {
                completeOK(req);
            }
        });

    }

    private Tablespace verifyTablespace(RestRequest req) throws HttpException {
        String tablespaceName = req.getRouteParam("tablespace");

        RdbStorageEngine rse = RdbStorageEngine.getInstance();
        Tablespace tablespace = rse.getTablespace(tablespaceName);
        if (tablespace == null) {
            throw new BadRequestException("No tablespace by name '" + tablespaceName + "'");
        }
        return tablespace;
    }
}
