package com.k8s.generator.model;

import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkCIDRTest {

    @Test
    void shouldAcceptValidCIDR() {
        NetworkCIDR cidr = NetworkCIDR.of("10.244.0.0/16");

        assertThat(cidr.toCIDRString()).isEqualTo("10.244.0.0/16");
        assertThat(cidr.prefixLength()).isEqualTo(16);
        assertThat(cidr.isIPv4()).isTrue();
    }

    @Test
    void shouldNormalizeToPrefixBlock() {
        NetworkCIDR cidr = NetworkCIDR.of("10.244.3.4/16");
        assertThat(cidr.toCIDRString()).isEqualTo("10.244.0.0/16");
    }

    @Test
    void shouldSupportContainmentChecks() {
        NetworkCIDR cidr = NetworkCIDR.of("10.244.0.0/16");
        var ipIn = new IPAddressString("10.244.1.10").getAddress();
        var ipOut = new IPAddressString("10.245.1.10").getAddress();

        assertThat(cidr.contains(ipIn)).isTrue();
        assertThat(cidr.contains(ipOut)).isFalse();
    }

    @Test
    void shouldDetectOverlap() {
        NetworkCIDR net1 = NetworkCIDR.of("10.244.0.0/16");
        NetworkCIDR net2 = NetworkCIDR.of("10.244.128.0/17");
        NetworkCIDR net3 = NetworkCIDR.of("10.245.0.0/16");

        assertThat(net1.overlaps(net2)).isTrue();
        assertThat(net1.overlaps(net3)).isFalse();
    }

    @Test
    void shouldProvideDefaultNetworks() {
        NetworkCIDR pod = NetworkCIDR.defaultPodNetwork();
        NetworkCIDR svc = NetworkCIDR.defaultServiceNetwork();

        assertThat(pod.toCIDRString()).isEqualTo("10.244.0.0/16");
        assertThat(svc.toCIDRString()).isEqualTo("10.96.0.0/12");
    }
}

