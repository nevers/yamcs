package org.yamcs.tctm.ccsds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.tctm.TcTmException;

public class PacketDecoderTest {
    List<byte[]> pl = new ArrayList<>();
    PacketDecoder pd = new PacketDecoder(1000, (byte[] p) -> pl.add(p));

    @Before
    public void emptyList() {
        pl.clear();
    }

    @Test
    public void testOneByteEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE0 }, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE0 }, p);
    }

    @Test
    public void testTwoBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE1, 2 }, 0, 2);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE1 , 2 }, p);
    }

    @Test
    public void testFourBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE2, 0,0,4 }, 0, 4);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE2 , 0,0,4 }, p);
        
        pl.clear();
        pd.process(new byte[] { (byte) 0xE2, 0}, 0, 2);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] { (byte) 0, 4}, 0, 2);
        assertFalse(pd.hasIncompletePacket());
        p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE2 , 0,0,4 }, p);
    }
    
    @Test
    public void testEightBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE3, 0, 0, 0, 
                0,0,0,9 }, 0, 8);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] {10}, 0,1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] { (byte) 0xE3 , 0, 0, 0, 0,0,0,9,10 }, p);
    }

    @Test
    public void testMinCcsds() throws TcTmException {
        pd.process(new byte[] { 0,0,0 ,0, 0}, 0, 5);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] {0}, 0, 1);
        assertTrue(pd.hasIncompletePacket());
        assertEquals(0, pl.size());
        pd.process(new byte[] {0}, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0 }, p);
        
    }
        
    @Test(expected = TcTmException.class)
    public void testInvalidTwoBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE1, 1 }, 0, 2);
    }

    @Test(expected = TcTmException.class)
    public void testInvalidFourBytesEncapsulation() throws TcTmException {
        pd.process(new byte[] { (byte) 0xE2, 0, 0, 1 }, 0, 4);
        assertFalse(pd.hasIncompletePacket());
    }
}
