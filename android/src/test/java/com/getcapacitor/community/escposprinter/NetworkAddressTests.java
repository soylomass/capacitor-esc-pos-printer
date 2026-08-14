package com.getcapacitor.community.escposprinter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.getcapacitor.community.escposprinter.printers.NetworkAddress;
import com.getcapacitor.community.escposprinter.printers.NetworkPrinter;

import org.junit.Test;

import java.util.List;

public class NetworkAddressTests {

    @Test
    public void parsesHostWithDefaultPort() {
        NetworkAddress address = NetworkAddress.parse("192.168.1.100");
        assertNotNull(address);
        assertEquals("192.168.1.100", address.host);
        assertEquals(9100, address.port);
    }

    @Test
    public void parsesHostWithExplicitPort() {
        NetworkAddress address = NetworkAddress.parse("192.168.1.100:9101");
        assertNotNull(address);
        assertEquals("192.168.1.100", address.host);
        assertEquals(9101, address.port);
    }

    @Test
    public void parsesHostnameAndTrimsWhitespace() {
        NetworkAddress address = NetworkAddress.parse("  printer.local:631  ");
        assertNotNull(address);
        assertEquals("printer.local", address.host);
        assertEquals(631, address.port);
    }

    @Test
    public void rejectsMalformedInput() {
        assertNull(NetworkAddress.parse(null));
        assertNull(NetworkAddress.parse(""));
        assertNull(NetworkAddress.parse("   "));
        assertNull(NetworkAddress.parse(":9100"));
        assertNull(NetworkAddress.parse("host:"));
        assertNull(NetworkAddress.parse("host:abc"));
        assertNull(NetworkAddress.parse("host:0"));
        assertNull(NetworkAddress.parse("host:65536"));
        assertNull(NetworkAddress.parse("host:-1"));
        // IPv6 literals are unsupported by the host:port format
        assertNull(NetworkAddress.parse("fe80::1:9100"));
    }

    @Test
    public void decodesDleEotStatusFlags() {
        assertTrue(NetworkPrinter.decodeDleEotStatus(0x16).isEmpty());

        List<String> offline = NetworkPrinter.decodeDleEotStatus(0x16 | 0x08);
        assertEquals(List.of("OFFLINE"), offline);

        List<String> paperOut = NetworkPrinter.decodeDleEotStatus(0x16 | 0x20);
        assertEquals(List.of("PAPER_OUT"), paperOut);

        List<String> all = NetworkPrinter.decodeDleEotStatus(0x08 | 0x20 | 0x40);
        assertEquals(List.of("OFFLINE", "PAPER_OUT", "ERROR"), all);
    }
}
