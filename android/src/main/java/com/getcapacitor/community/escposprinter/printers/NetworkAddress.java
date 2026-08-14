package com.getcapacitor.community.escposprinter.printers;

/**
 * Parsed "host[:port]" network printer address. Kept as a tiny static helper
 * so the parsing rules are unit-testable without Android dependencies.
 */
public final class NetworkAddress {
    public static final int DEFAULT_PORT = 9100;

    public final String host;
    public final int port;

    private NetworkAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Parses "host" or "host:port" (port 1-65535, default 9100).
     * Returns null for malformed input.
     */
    public static NetworkAddress parse(String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String host = trimmed;
        int port = DEFAULT_PORT;

        int colonIndex = trimmed.lastIndexOf(':');
        if (colonIndex >= 0) {
            // More than one colon would be an IPv6 literal, which this simple
            // "host:port" format does not support.
            if (trimmed.indexOf(':') != colonIndex) {
                return null;
            }
            host = trimmed.substring(0, colonIndex).trim();
            String portPart = trimmed.substring(colonIndex + 1).trim();
            if (host.isEmpty() || portPart.isEmpty()) {
                return null;
            }
            try {
                port = Integer.parseInt(portPart);
            } catch (NumberFormatException e) {
                return null;
            }
            if (port < 1 || port > 65535) {
                return null;
            }
        }

        return new NetworkAddress(host, port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
