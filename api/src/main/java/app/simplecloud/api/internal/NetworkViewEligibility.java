package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.Identifiers;

/**
 * Decides whether a {@code CloudApi} instance may read from a view of its network. The view loads its
 * snapshots with the instance's credential, and the controller answers {@code GET /v0/network-state} only
 * for the network's password. Instances share a view only when they share a {@link SharedRuntime}, whose
 * settings hold the credential, so a view is never shared between credentials.
 */
final class NetworkViewEligibility {

    private NetworkViewEligibility() {
    }

    /**
     * Returns false when the instance reads over HTTP only: its network is not a UUID, which the controller
     * requires, or its credential may be a JWT.
     */
    static boolean allows(CloudApiOptions options) {
        return Identifiers.isUuid(options.getNetworkId()) && mayBeNetworkPassword(options.getNetworkSecret());
    }

    /**
     * Returns whether {@code credential} may be one of the network's passwords: it is not blank, and the
     * controller would not read it as a JWT, which it does for one that parses as a signed JWT, three parts
     * split by dots, or JSON ({@code AuthenticateNetworkCredential} in
     * {@code internal/auth/network_credentials.go}). A password the controller generates has only letters
     * and digits.
     *
     * <p>Safety does not rest on this rule. The controller answers 403 on {@code /v0/network-state} to every
     * credential but the network's password, so a view on any other credential never loads a snapshot and
     * never answers; and views are shared only by instances with the same credential, so such a view is never
     * shared with a password's instance. The rule only saves such a view's pointless subscriptions and load
     * retries.
     */
    private static boolean mayBeNetworkPassword(String credential) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        boolean mayBeJwt = credential.indexOf('.') >= 0 || credential.indexOf('{') >= 0;
        return !mayBeJwt;
    }
}
