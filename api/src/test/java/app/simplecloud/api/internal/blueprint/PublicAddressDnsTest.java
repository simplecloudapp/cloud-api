package app.simplecloud.api.internal.blueprint;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAddressDnsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "0.1.2.3",                          // this network
            "127.0.0.1", "127.1.2.3",                      // loopback
            "10.0.0.1", "172.16.0.1", "192.168.1.1",       // RFC 1918
            "169.254.169.254",                             // link-local / cloud metadata
            "100.64.0.1", "100.127.255.255",               // carrier-grade NAT
            "192.0.0.1", "192.0.2.1",                      // IETF protocol assignments / TEST-NET-1
            "198.18.0.1", "198.19.255.255",                // benchmarking
            "198.51.100.1", "203.0.113.1",                 // TEST-NET-2 / TEST-NET-3
            "224.0.0.1", "239.255.255.250",                // multicast
            "240.0.0.1", "255.255.255.255",                // reserved / broadcast
            "::", "::1",                                   // unspecified / loopback
            "fe80::1",                                     // link-local
            "fc00::1", "fd00:ec2::254",                    // unique local
            "ff02::1",                                     // multicast
            "::ffff:127.0.0.1", "::ffff:10.0.0.1",         // IPv4-mapped
            "::10.0.0.1",                                  // IPv4-compatible
            "64:ff9b::a9fe:a9fe",                          // NAT64 of 169.254.169.254
            "2002:c0a8:0101::1",                           // 6to4 of 192.168.1.1
            "64:ff9b:1::a00:1", "64:ff9b:1::a9fe:a9fe",    // local-use NAT64
    })
    void isPublic_rejectsNonPublicAddresses(String literal) throws UnknownHostException {
        assertFalse(PublicAddressDns.isPublic(InetAddress.getByName(literal)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.1.1.1", "8.8.8.8", "185.199.108.133",
            "100.63.255.255", "100.128.0.1", "198.17.255.255", "198.20.0.1",
            "192.0.1.1", "192.0.3.1", "198.51.101.1", "203.0.114.1",
            "2606:4700:4700::1111", "2a00:1450:4001:80b::200e",
            "64:ff9b::808:808",                            // NAT64 of 8.8.8.8
    })
    void isPublic_acceptsPublicAddresses(String literal) throws UnknownHostException {
        assertTrue(PublicAddressDns.isPublic(InetAddress.getByName(literal)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::ffff:10.0.0.1", // IPv4-mapped
            "::ffff:0:127.0.0.1",                                            // IPv4-translated
            "64:ff9b:1::808:808",                                            // local-use NAT64
    })
    void isPublic_rejectsNonPublicIpv4EmbeddedInResolverAnswers(String literal) throws UnknownHostException {
        // Resolver answers keep their IPv6 form; InetAddress.getByName would convert mapped literals to IPv4.
        assertFalse(PublicAddressDns.isPublic(ipv6Answer(literal)));
    }

    @Test
    void isPublic_acceptsPublicIpv4MappedResolverAnswer() throws UnknownHostException {
        assertTrue(PublicAddressDns.isPublic(ipv6Answer("::ffff:8.8.8.8")));
    }

    @Test
    void lookup_rejectsWhenAnyResolvedAddressIsNonPublic() throws UnknownHostException {
        List<InetAddress> addresses = List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("10.0.0.1"));
        PublicAddressDns dns = new PublicAddressDns(hostname -> addresses);

        assertThrows(UnknownHostException.class, () -> dns.lookup("mixed.test"));
    }

    @Test
    void lookup_returnsPublicAddresses() throws UnknownHostException {
        List<InetAddress> addresses = List.of(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("2606:4700:4700::1111"));
        PublicAddressDns dns = new PublicAddressDns(hostname -> addresses);

        assertEquals(addresses, dns.lookup("public.test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/", "https://[::1]/", "https://[fd00::1]/", "https://[::ffff:a9fe:a9fe]/",
            "https://127.1/", "https://2130706433/", "https://1.2.3.4.5/", "https://999.1.1.1/", // ambiguous numeric hosts
    })
    void requirePublicLiteral_rejectsNonPublicOrAmbiguousLiterals(String url) {
        assertThrows(UnknownHostException.class, () -> PublicAddressDns.requirePublicLiteral(HttpUrl.get(url)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://1.1.1.1/", "https://[2606:4700:4700::1111]/", "https://localhost/"})
    void requirePublicLiteral_ignoresPublicLiteralsAndHostnames(String url) {
        // Hostnames are left to the Dns lookup.
        assertDoesNotThrow(() -> PublicAddressDns.requirePublicLiteral(HttpUrl.get(url)));
    }

    private static InetAddress ipv6Answer(String literal) throws UnknownHostException {
        byte[] bytes = new byte[16];
        byte[] parsed = InetAddress.getByName(literal).getAddress();
        System.arraycopy(parsed, 0, bytes, 16 - parsed.length, parsed.length);
        if (parsed.length == 4) {
            bytes[10] = (byte) 0xFF;
            bytes[11] = (byte) 0xFF;
        }
        return Inet6Address.getByAddress(null, bytes, -1);
    }
}
