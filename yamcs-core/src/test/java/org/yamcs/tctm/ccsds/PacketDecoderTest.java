package org.yamcs.tctm.ccsds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.yamcs.tctm.TcTmException;

public class PacketDecoderTest {
    
    @Test
    public void test1() throws TcTmException {
        List<byte[]> pl = new ArrayList<>();
        PacketDecoder pd = new PacketDecoder(1000, (byte[] p) -> pl.add(p));
        
        //add an empty encapsulation packet
        pd.process(new byte[] {0}, 0, 1);
        assertFalse(pd.hasIncompletePacket());
        assertEquals(1, pl.size());
        byte[] p = pl.get(0);
        assertArrayEquals(new byte[] {1}, p);
        
        
        
        
    }
}
