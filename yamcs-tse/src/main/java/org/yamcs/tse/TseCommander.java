package org.yamcs.tse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

import org.yamcs.ProcessRunner;
import org.yamcs.YConfiguration;
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;
import org.yamcs.api.ValidationException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.YObjectLoader;

import com.beust.jcommander.JCommander;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

public class TseCommander extends ProcessRunner {

    @Override
    public Spec getSpec() {
        Spec telnetSpec = new Spec();
        telnetSpec.addOption("port", OptionType.INTEGER);

        Spec tmtcSpec = new Spec();
        tmtcSpec.addOption("port", OptionType.INTEGER);

        Spec spec = new Spec();
        spec.addOption("telnet", OptionType.MAP).withSpec(telnetSpec);
        spec.addOption("tctm", OptionType.MAP).withSpec(tmtcSpec);
        spec.addOption("instruments", OptionType.LIST).withElementType(OptionType.ANY);

        return spec;
    }

    @Override
    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        YConfiguration telnetArgs = config.getConfig("telnet");
        int telnetPort = telnetArgs.getInt("port");

        YConfiguration yamcsArgs = config.getConfig("tctm");
        int tctmPort = yamcsArgs.getInt("port");

        try {
            Map<String, Object> processRunnerConfig = new HashMap<>();
            processRunnerConfig.put("command", Arrays.asList(
                    new File(System.getProperty("java.home"), "bin/java").toString(),
                    "-cp", System.getProperty("java.class.path"),
                    TseCommander.class.getName(),
                    "--telnet-port", "" + telnetPort,
                    "--tctm-port", "" + tctmPort));
            processRunnerConfig.put("logPrefix", "");
            processRunnerConfig = super.getSpec().validate(processRunnerConfig);
            super.init(yamcsInstance, YConfiguration.wrap(processRunnerConfig));
        } catch (ValidationException e) {
            throw new InitException(e.getMessage());
        }
    }

    public static void main(String[] args) {
        TseCommanderArgs runtimeOptions = new TseCommanderArgs();
        new JCommander(runtimeOptions).parse(args);

        configureLogging();
        TimeEncoding.setUp();

        YConfiguration yconf = YConfiguration.getConfiguration("tse");
        List<Service> services = createServices(yconf, runtimeOptions);

        ServiceManager serviceManager = new ServiceManager(services);
        serviceManager.addListener(new ServiceManager.Listener() {
            @Override
            public void failure(Service service) {
                // Stop entire process as soon as one service fails.
                System.exit(1);
            }
        });

        // Allow services to shutdown gracefully
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    serviceManager.stopAsync().awaitStopped(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // ignore
                }
            }
        });

        serviceManager.startAsync();
    }

    private static void configureLogging() {
        try {
            LogManager logManager = LogManager.getLogManager();
            try (InputStream in = TseCommander.class.getResourceAsStream("/tse-logging.properties")) {
                logManager.readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Failed to set up logging configuration: " + e.getMessage());
        }
    }

    private static List<Service> createServices(YConfiguration yconf, TseCommanderArgs runtimeOptions) {
        List<Service> services = new ArrayList<>();

        InstrumentController instrumentController = new InstrumentController();
        if (yconf.containsKey("instruments")) {
            for (YConfiguration instrumentConfig : yconf.getConfigList("instruments")) {
                String name = instrumentConfig.getString("name");
                try {
                    InstrumentDriver instrument = YObjectLoader.loadObject(instrumentConfig.toMap(), name);
                    instrumentController.addInstrument(instrument);
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }
        services.add(instrumentController);

        TelnetServer telnetServer = new TelnetServer(runtimeOptions.telnetPort, instrumentController);
        services.add(telnetServer);

        if (runtimeOptions.tctmPort != null) {
            services.add(new TcTmServer(runtimeOptions.tctmPort, instrumentController));
        }

        return services;
    }
}
