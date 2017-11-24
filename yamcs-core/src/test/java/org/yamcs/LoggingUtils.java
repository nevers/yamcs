package org.yamcs;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingUtils {
    /**
     * use to enable logging during junit tests debugging. 
     * Do not leave it enabled as travis will kill the tests if it outputs too much
     */
    public static void enableLogging() {
        Logger l = Logger.getLogger("org.yamcs");
        l.setLevel(Level.ALL);
        
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.ALL);
        l.addHandler(h);
    }
}
