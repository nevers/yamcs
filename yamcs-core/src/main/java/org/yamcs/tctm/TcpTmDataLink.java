package org.yamcs.tctm;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.api.Log;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.YObjectLoader;

public class TcpTmDataLink extends AbstractTmDataLink {

    protected Socket tmSocket;
    protected String host = "localhost";
    protected int port = 10031;
    protected volatile boolean disabled = false;

    protected final Log log;
    private TmSink tmSink;

    ParameterValue svConnectionStatus;
    List<ParameterValue> sysVariables = new ArrayList<>();

    String packetInputStreamClassName;
    Object packetInputStreamArgs;
    PacketInputStream packetInputStream;

    @Deprecated
    public TcpTmDataLink(String instance, String name, String spec) throws ConfigurationException {
        this(instance, name, YConfiguration.getConfiguration("tcp").getConfig(spec));
    }

    public TcpTmDataLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        super(instance, name, config);
        log = new Log(this.getClass(), instance);
        if (config.containsKey("tmHost")) { // this is when the config is specified in tcp.yaml
            host = config.getString("tmHost");
            port = config.getInt("tmPort");
        } else {
            host = config.getString("host");
            port = config.getInt("port");
        }
        if (config.containsKey("packetInputStreamClassName")) {
            this.packetInputStreamClassName = config.getString("packetInputStreamClassName");
        } else {
            this.packetInputStreamClassName = CcsdsPacketInputStream.class.getName();
        }
        this.packetInputStreamArgs = config.get("packetInputStreamArgs");

        initPreprocessor(instance, config);
    }

    protected void openSocket() throws IOException {
        InetAddress address = InetAddress.getByName(host);
        tmSocket = new Socket();
        tmSocket.setKeepAlive(true);
        tmSocket.connect(new InetSocketAddress(address, port), 1000);
        try {
            if (packetInputStreamArgs != null) {
                packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName, tmSocket.getInputStream(),
                        packetInputStreamArgs);
            } else {
                packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName, tmSocket.getInputStream());
            }
        } catch (ConfigurationException e) {
            log.error("Cannot instantiate the packetInput stream", e);
            throw e;
        }
    }

    @Override
    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    @Override
    public void run() {
        setupSysVariables();
        while (isRunning()) {
            PacketWithTime pwrt = getNextPacket();
            if (pwrt == null) {
                break;
            }
            tmSink.processPacket(pwrt);
        }
    }

    public PacketWithTime getNextPacket() {
        PacketWithTime pwt = null;
        while (isRunning()) {
            while (disabled) {
                if (!isRunning()) {
                    return null;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                if (tmSocket == null) {
                    openSocket();
                    log.info("TM connection established to {}:{}", host, port);
                }
                byte[] packet = packetInputStream.readPacket();
                updateStats(packet.length);
                pwt = packetPreprocessor.process(packet);
                if (pwt != null) {
                    break;
                }
            } catch (EOFException e) {
                log.warn("Tm Connection closed");
                tmSocket = null;
            } catch (IOException e) {
                String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
                log.info("Cannot open or read TM socket {}:{} {}'. Retrying in 10s", host, port, exc);
                try {
                    tmSocket.close();
                } catch (Exception e2) {
                }
                tmSocket = null;
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (PacketTooLongException e) {
                log.warn(e.toString());
                try {
                    tmSocket.close();
                } catch (Exception e2) {
                }
                tmSocket = null;
            }
        }
        return pwt;
    }

    @Override
    public Status getLinkStatus() {
        if (disabled) {
            return Status.DISABLED;
        }
        if (tmSocket == null) {
            return Status.UNAVAIL;
        } else {
            return Status.OK;
        }
    }

    @Override
    public void triggerShutdown() {
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
        if (sysParamCollector != null) {
            sysParamCollector.unregisterProducer(this);
        }
    }

    @Override
    public void disable() {
        disabled = true;
        if (tmSocket != null) {
            try {
                tmSocket.close();
            } catch (IOException e) {
                log.warn("Exception got when closing the tm socket:", e);
            }
            tmSocket = null;
        }
    }

    @Override
    public void enable() {
        disabled = false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public String getDetailedStatus() {
        if (disabled) {
            return String.format("DISABLED (should connect to %s:%d)", host, port);
        }
        if (tmSocket == null) {
            return String.format("Not connected to %s:%d", host, port);
        } else {
            return String.format("OK, connected to %s:%d, received %d packets", host, port, packetcount);
        }
    }

}
