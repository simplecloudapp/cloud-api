package app.simplecloud.api.internal.blueprint.manifest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAddressDnsTest {

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "140.82.112.3", "2606:4700:4700::1111"})
    void publicAddressesAreAllowed(String address) throws UnknownHostException {
        assertTrue(PublicAddressDns.isPublic(InetAddress.getByName(address)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "10.0.0.1",
                "172.16.0.1",
                "192.168.1.1",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "255.255.255.255",
                "::1",
                "fc00::1",
                "fe80::1",
                "::ffff:10.0.0.1",
                "64:ff9b::a00:1",
                "2002:a00:1::"
            })
    void privateAndReservedAddressesAreRejected(String address) throws UnknownHostException {
        assertFalse(PublicAddressDns.isPublic(InetAddress.getByName(address)));
    }
}
