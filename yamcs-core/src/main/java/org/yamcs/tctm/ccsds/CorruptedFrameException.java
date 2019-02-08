package org.yamcs.tctm.ccsds;

/**
 * Exception indicating a frame is corrupted
 * 
 * @author nm
 *
 */
public class CorruptedFrameException extends Exception {
    public CorruptedFrameException(String msg) {
        super(msg);
    }
    public CorruptedFrameException(String message, Throwable cause) {
        super(message, cause);
    }
}
