package org.yamcs.cli;


import java.util.concurrent.CompletableFuture;

import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.BulkRestDataSender;
import org.yamcs.api.rest.RestClient;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

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
    
    @Parameters(commandDescription = "Load data to table")    
    class TablesLoad extends Command {
        @Parameter(names="-t", description="table name", required=true)
        String tableName;

        @Parameter(names="-f", description="Name of the file containing data to be loaded. The data has to be in gzipped protobuf TableRecord messages.", required=true)
        String fileName;

        public TablesLoad() {
            super("load", TablesCli.this);
        }
        
        @Override
        public void execute() throws Exception {
            YamcsConnectionProperties ycp = getYamcsConnectionProperties();
            RestClient restClient = new RestClient(ycp);
            CompletableFuture<BulkRestDataSender> cf = restClient.doBulkSendRequest("/api/archive/"+ycp.getInstance()+"/tables/"+tableName+"/data", HttpMethod.POST);
            BulkRestDataSender bds = cf.get();
            System.out.println("got bds: "+bds);
        }
        
    }

}

