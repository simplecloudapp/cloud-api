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
    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern DOTTED_QUAD = Pattern.compile(OCTET + "(\\." + OCTET + "){3}");

    private static final byte[] IPV4_COMPATIBLE_PREFIX = new byte[12];
    private static final byte[] IPV4_MAPPED_PREFIX = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] IPV4_TRANSLATED_PREFIX = {0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0, 0};
    private static final byte[] NAT64_PREFIX = {0x00, 0x64, (byte) 0xFF, (byte) 0x9B, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] NAT64_LOCAL_PREFIX = {0x00, 0x64, (byte) 0xFF, (byte) 0x9B, 0x00, 0x01};
    private static final byte[] SIX_TO_FOUR_PREFIX = {0x20, 0x02};

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
        // HttpUrl only accepts valid IPv6 literals, but numeric hosts like "127.1" or "1.2.3.4.5" reach
        // OkHttp's InetAddress.getByName unchanged and may hit system DNS. Only plain dotted quads are allowed.
        if (!host.contains(":") && !DOTTED_QUAD.matcher(host).matches()) {
            throw new UnknownHostException("Ambiguous numeric host " + host);
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
        int third = bytes[2] & 0xFF;
        return first != 0                                                 // 0.0.0.0/8 "this network"
                && !(first == 100 && (second & 0xC0) == 64)               // 100.64.0.0/10 carrier-grade NAT
                && !(first == 192 && second == 0 && (third == 0 || third == 2)) // 192.0.0.0/24 IETF, 192.0.2.0/24 TEST-NET-1
                && !(first == 198 && (second & 0xFE) == 18)               // 198.18.0.0/15 benchmarking
                && !(first == 198 && second == 51 && third == 100)        // 198.51.100.0/24 TEST-NET-2
                && !(first == 203 && second == 0 && third == 113)         // 203.0.113.0/24 TEST-NET-3
                && first < 240;                                           // 240.0.0.0/4 reserved, incl. broadcast
    }

    private static boolean isPublicIpv6(byte[] bytes) {
        if ((bytes[0] & 0xFE) == 0xFC || startsWith(bytes, NAT64_LOCAL_PREFIX)) {
            return false; // fc00::/7 unique local, 64:ff9b:1::/48 local-use NAT64
        }
        InetAddress embedded = embeddedIpv4(bytes);
        return embedded == null || isPublic(embedded);
    }

    /**
     * Returns the IPv4 address carried inside IPv4-compatible (::/96), IPv4-mapped (::ffff:0:0/96),
     * IPv4-translated (::ffff:0:0:0/96), NAT64 (64:ff9b::/96) or 6to4 (2002::/16) addresses.
     * The JDK only converts IPv4-mapped literals to {@link java.net.Inet4Address}; resolver answers
     * stay IPv6, and dual-stack sockets still connect to the embedded IPv4 address.
     */
    private static @Nullable InetAddress embeddedIpv4(byte[] bytes) {
        if (startsWith(bytes, IPV4_COMPATIBLE_PREFIX)
                || startsWith(bytes, IPV4_MAPPED_PREFIX)
                || startsWith(bytes, IPV4_TRANSLATED_PREFIX)
                || startsWith(bytes, NAT64_PREFIX)) {
            return ipv4(bytes, 12);
        }
        if (startsWith(bytes, SIX_TO_FOUR_PREFIX)) {
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
