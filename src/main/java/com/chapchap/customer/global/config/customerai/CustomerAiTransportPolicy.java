package com.chapchap.customer.global.config.customerai;

import java.net.URI;
import java.util.Collection;

public final class CustomerAiTransportPolicy {
    private CustomerAiTransportPolicy() {}

    public static java.util.List<String> httpOrigins(String configured) {
        if (configured == null || configured.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    public static boolean allows(URI uri, String configured) {
        return allows(uri, httpOrigins(configured));
    }

    public static boolean allows(URI uri, Collection<String> httpOrigins) {
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        return httpOrigins.stream().anyMatch(value -> {
            try {
                URI allowed = URI.create(value);
                return "http".equalsIgnoreCase(allowed.getScheme())
                        && allowed.getHost() != null && allowed.getUserInfo() == null
                        && allowed.getQuery() == null && allowed.getFragment() == null
                        && (allowed.getPath().isEmpty() || "/".equals(allowed.getPath()))
                        && allowed.getHost().equalsIgnoreCase(uri.getHost())
                        && port(allowed) == port(uri);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        });
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? 80 : uri.getPort();
    }
}
