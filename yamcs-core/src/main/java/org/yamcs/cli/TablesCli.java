package org.yamcs.cli;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.BulkRestDataSender;
import org.yamcs.api.rest.RestClient;
import org.yamcs.protobuf.Archive.TableData.TableRecord;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;


/**
 * Command line utility for doing operations with tables (we preferred this to having tables a sub-command of the archive, otherwise the commadn line was getting too long)
 * 
 * @author nm
 *
 */
@Parameters(commandDescription = "Tables operations")
public class TablesCli extends Command {
    public TablesCli(Command parent) {
        super("tables", parent);
        addSubCommand(new TablesList());
        addSubCommand(new TablesDump());
        addSubCommand(new TablesLoad());
        setYcpRequired(true, true);
    }

    @Parameters(commandDescription = "List existing tables")    
    class TablesList extends Command {
        public TablesList() {
            super("list", TablesCli.this);
        }

        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);
            String resp = new String(restClient.doRequest("/api/archive/"+ycp.getInstance()+"/tables", HttpMethod.GET).get());
            console.print(resp);
        }
    }

    @Parameters(commandDescription = "Dumps table data to file, Connection to a live Yamcs server (-y option) is required.")    
    class TablesDump extends Command {

        @Parameter(names="-d", description="Name of the output directory. If not specified, the current directory will be used. The directory has to exist.")
        String dir;

        @Parameter(description="table1 table2...", required=true)
        List<String> tableList;

        public TablesDump() {
            super("dump", TablesCli.this);
        }

        private void dumpTable(String tableName) throws Exception {
            String fileName = tableName+".dump";
            if(dir!=null) {
                fileName = dir+"/"+fileName;;
            }
            console.println("Dumping data from "+tableName+" table to "+fileName);
            OutputStream outputs = new GZIPOutputStream(new FileOutputStream(fileName));
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);

            CompletableFuture<Void> cf = restClient.doBulkGetRequest("/archive/"+ycp.getInstance()+"/downloads/tables/"+tableName, (data) -> {
                TableRecord tr;
                try {
                    tr = TableRecord.parseFrom(data);
                    tr.writeDelimitedTo(outputs);
                } catch (IOException e) {
                    console.println("Error receiving table record: "+e.getMessage());
                    System.exit(1);
                }
            });
            cf.get();
            outputs.close();
            restClient.close();
            console.println("finished");
        }

        @Override
        public void execute() throws Exception {
            for(String tableName:tableList) {
                dumpTable(tableName);
            }
            System.exit(0);
        }
    }        

    @Parameters(commandDescription = "Load data to table")
    class TablesLoad extends Command {
        @Parameter(names="-d", description="Name of the directory to load files from. If not specified, the current directory will be used. The directory has to contain <tableName>.dump.")
        String dir;

        public TablesLoad() {
            super("load", TablesCli.this);
        }

        @Parameter(description="table1 table2...", required=true)
        List<String> tableList;

        private void loadTable(String tableName) throws Exception {
            String fileName = tableName+".dump";
            if(dir!=null) {
                tableName = dir+"/"+tableName+".dump";
            }
            InputStream is = new GZIPInputStream(new FileInputStream(fileName));
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();

            RestClient restClient = new RestClient(ycp);
            try {
                CompletableFuture<BulkRestDataSender> cf = restClient.doBulkSendRequest("/archive/"+ycp.getInstance()+"/tables/"+tableName+"/data", HttpMethod.POST);
                BulkRestDataSender bds = cf.get();
                ByteBuf buf = Unpooled.buffer();
                ByteBufOutputStream bufstream = new ByteBufOutputStream(buf);
                int c = 0;
                while(true) {
                    TableRecord tr = TableRecord.parseDelimitedFrom(is);
                    if(tr==null) break;
                    tr.writeDelimitedTo(bufstream);
                    if(((c++)&0xF)==0) {
                        bufstream.close();
                        bds.sendData(buf);
                        buf = Unpooled.buffer();
                        bufstream = new ByteBufOutputStream(buf);
                    }
                }  
                bufstream.close();
                if(buf.readableBytes()>0) {
                    bds.sendData(buf);
                }
                is.close();
            } finally {
                restClient.close();
            }
        }


        @Override
        public void execute() throws Exception {
            for(String tn:tableList) {
                loadTable(tn);
            }
        }
    }
}

