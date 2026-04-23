package me.zed_0xff.zombie_buddy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {
    @Test
    void bytesToHex_emptyArray_returnsEmptyString() {
        assertEquals("", Utils.bytesToHex(new byte[0]));
    }

    @Test
    void bytesToHex_null_returnsEmptyString() {
        assertEquals("", Utils.bytesToHex(null));
    }

    @Test
    void bytesToHex_singleByte() {
        assertEquals("00", Utils.bytesToHex(new byte[] { 0 }));
        assertEquals("FF", Utils.bytesToHex(new byte[] { (byte) 0xFF }));
        assertEquals("0A", Utils.bytesToHex(new byte[] { 10 }));
    }

    @Test
    void bytesToHex_multipleBytes_joinsWithColons() {
        assertEquals("00:01:02", Utils.bytesToHex(new byte[] { 0, 1, 2 }));
        assertEquals("A7:75:10:1B", Utils.bytesToHex(new byte[] {
            (byte) 0xA7, (byte) 0x75, (byte) 0x10, (byte) 0x1B
        }));
    }
}
