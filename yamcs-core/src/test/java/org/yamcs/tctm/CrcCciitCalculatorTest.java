package org.yamcs.tctm;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;

import org.junit.Test;
import org.yamcs.tctm.ccsds.error.CrcCciitCalculator;

public class CrcCciitCalculatorTest {
    @Test
    public void test1() {
        CrcCciitCalculator c = new CrcCciitCalculator(new HashMap<String, Object>());
        byte[] data = new byte[] {0x6, 0x0, 0x0c, (byte)0xf0,  0x00, 0x04, 0x00, 0x55,  (byte)0x88, 0x73, (byte)0xc9, 0x00,  0x00, 0x05, 0x21};
        int x = c.compute(data, 0, data.length);
        assertEquals(0x75FB, x);
    }
}
