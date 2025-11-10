package com.k8s.generator.testutil;

import inet.ipaddr.IPAddress;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Test helpers for working with lists of IPAddress. */
public final class IpAssertions {
    private IpAssertions() {}

    /**
     * Converts IPAddress iterable to a list of canonical strings (e.g., "192.168.56.10").
     */
    public static List<String> canonical(Iterable<IPAddress> ips) {
        var out = new ArrayList<String>();
        for (IPAddress ip : ips) {
            out.add(ip.toCanonicalString());
        }
        return out;
    }

    /**
     * Asserts that the iterable of IPAddress matches exactly the expected canonical strings.
     */
    public static void assertIps(Iterable<IPAddress> ips, String... expected) {
        assertThat(canonical(ips)).containsExactly(expected);
    }
}

