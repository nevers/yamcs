package org.yamcs.tctm.ccsds;

/**
 * Transfer frame is an interface covering the three CCSDS tranfer frames types:
 * <ul>
 * <li>TM (CCSDS 132.0-B-2)</li>
 * <li>AOS (CCSDS 732.0-B-3)</li>
 * <li>UNIFIED ( 732.1-B-1)</li>
 * </ul>
 * <p>
 * <p>
 * For the purpose of SDU (e.g. packet) extraction each frame has defined three offsets:<br>
 * dataStart &lt;= firstSduStart &lt; dataEnd
 * <p>
 * <p>
 * firstSduStart refers to the first SDU that starts in this frame.
 * <p>
 * The data in between dataStart and firstSduStart is part of a previous packet and will be used only if there is no
 * discontinuity in the frame sequence count.
 * 
 * @author nm
 *
 */
public interface TransferFrame {
    /**
     * 
     * @return virtual channel frame count
     */
    long getVcFrameSeq();

    /**
     * 
     * @return master channel id
     */
    int getMasterChannelId();

    /**
     * 
     * @return virtual channel id
     */
    int getVirtualChannelId();

    /**
     * Checks if the frame matches a filter - it is used for routing the frame to a handler
     * 
     * @param filter
     * @return
     */
    boolean matchesFilter(FrameFilter filter);

    /**
     * Returns the number of frames lost from the previous sequence to this one.
     * If no frame has been lost (i.e. if prevFrameSeq and getFrameSeq() are in order) then return 0.
     * 
     * -1 means that a number of lost frames could not be determined - if there is some indication that the stream has
     * been reset
     * 
     * @param prevFrameSeq
     * @return
     */
    int lostFramesCount(long prevFrameSeq);

    byte[] getData();

    /**
     * Where in the byte array returned by {@link #getData} starts the data.
     * 
     * @return
     */
    int getDataStart();

    /**
     * Where in the byte array returned by {@link #getData} starts the first SDU.
     * 
     * Returns -1 if there is no SDU starting in this frame.
     * 
     * @return the offset of the first SDU that starts in this frame or -1 if no SDU starts in this frame
     */
    int getFirstSduStart();

    /**
     * Where in the byte array returned by {@link #getData} ends the data.
     * 
     * @return data end
     */
    int getDataEnd();
    
    /**
     * 
     * @return true if this frame contains only idle (fill) data.
     */
    boolean containsOnlyIdleData();

}
