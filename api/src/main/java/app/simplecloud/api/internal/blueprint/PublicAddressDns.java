package app.simplecloud.api.internal.blueprint;

import okhttp3.Dns;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@link Dns} that refuses to resolve hosts to non-public addresses.
 *
 * <p>Filtering at resolution time means the checked addresses are exactly the ones OkHttp connects to,
 * for the initial request and every redirect hop. OkHttp skips {@link Dns} for IP literals, so callers
 * must also check those with {@link #requirePublicLiteral(HttpUrl)}.
 */
final class PublicAddressDns implements Dns {
    // Same heuristic OkHttp uses to decide whether a host is an IP literal and bypasses Dns.
    private static final Pattern IP_LITERAL = Pattern.compile("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)");

    private final Dns delegate;

    PublicAddressDns(Dns delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = delegate.lookup(hostname);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new UnknownHostException(
                        hostname + " resolves to non-public address " + address.getHostAddress()
                );
            }
        }
        return addresses;
    }

    static void requirePublicLiteral(HttpUrl url) throws UnknownHostException {
        String host = url.host();
        if (!IP_LITERAL.matcher(host).matches()) {
            return;
        }
        InetAddress address = InetAddress.getByName(host);
        if (!isPublic(address)) {
            throw new UnknownHostException("Non-public address " + address.getHostAddress());
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        return bytes.length == 4 ? isPublicIpv4(bytes) : isPublicIpv6(bytes);
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first != 0                                   // 0.0.0.0/8 "this network"
                && !(first == 100 && (second & 0xC0) == 64) // 100.64.0.0/10 carrier-grade NAT
                && !(first == 198 && (second & 0xFE) == 18) // 198.18.0.0/15 benchmarking
                && first < 240;                             // 240.0.0.0/4 reserved, incl. broadcast
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        if ((bytes[0] & 0xFE) == 0xFC) {
            return false; // fc00::/7 unique local
        }
        InetAddress embedded = embeddedIpv4(bytes);
        return embedded == null || isPublic(embedded);
    }

    /**
     * Returns the IPv4 address tunnelled inside IPv4-compatible (::/96), NAT64 (64:ff9b::/96)
     * or 6to4 (2002::/16) addresses. IPv4-mapped addresses are already returned as IPv4 by the JDK.
     */
    private static @Nullable InetAddress embeddedIpv4(byte[] bytes) {
        if (startsWith(bytes, new byte[12])) {
            return ipv4(bytes, 12);
        }
        if (startsWith(bytes, new byte[]{0x00, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 0, 0, 0, 0, 0, 0, 0})) {
            return ipv4(bytes, 12);
        }
        if (bytes[0] == 0x20 && bytes[1] == 0x02) {
            return ipv4(bytes, 2);
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static InetAddress ipv4(byte[] bytes, int offset) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, offset, offset + 4));
        } catch (UnknownHostException e) {
            throw new AssertionError("4-byte address is always valid", e);
        }
    }
}
