package io.github.miklires.mauth.geoip;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoIpResolverTest {

    @Test
    void localRanges() throws Exception {
        assertTrue(GeoIpResolver.isLocalIp(InetAddress.getByName("127.0.0.1")));
        assertTrue(GeoIpResolver.isLocalIp(InetAddress.getByName("192.168.4.20")));
        assertTrue(GeoIpResolver.isLocalIp(InetAddress.getByName("172.31.8.2")));
        assertTrue(GeoIpResolver.isLocalIp(InetAddress.getByName("fd12::1")));
    }

    @Test
    void publicRanges() throws Exception {
        assertFalse(GeoIpResolver.isLocalIp(InetAddress.getByName("172.200.1.4")));
        assertFalse(GeoIpResolver.isLocalIp(InetAddress.getByName("1.1.1.1")));
        assertFalse(GeoIpResolver.isLocalIp(InetAddress.getByName("2606:4700:4700::1111")));
    }
}
