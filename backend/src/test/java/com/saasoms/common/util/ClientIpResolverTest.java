package com.saasoms.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final String REMOTE = "172.18.0.5";

    private static String resolve(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE);
        if (header != null) {
            request.addHeader("X-Real-IP", header);
        }
        return ClientIpResolver.resolve(request);
    }

    @Test
    void resolve_validIpv4_usesHeader() {
        assertThat(resolve("5.5.5.5")).isEqualTo("5.5.5.5");
    }

    @Test
    void resolve_missingHeader_usesRemoteAddr() {
        assertThat(resolve(null)).isEqualTo(REMOTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "example.com", "evil.invalid", "abc", "dead.beef", "999.1.1.1", "1.2.3", "1.2.3.4.5",
            "010.1.1.1", "1.1.1.1, 2.2.2.2", "[::1]", "fe80::1%eth0", "g::1", ".::1", "abc:def", "1.2:", ":::", "unknown"
    })
    void resolve_notIpLiteral_fallsBackToRemoteAddr(String header) {
        assertThat(resolve(header)).isEqualTo(REMOTE);
    }

    @Test
    void resolve_ipv6SamePrefix64_sameKey() {
        String first = resolve("2001:db8:1:2:aaaa:bbbb:cccc:dddd");
        String second = resolve("2001:DB8:1:2::1");

        assertThat(first).isEqualTo(second).isEqualTo("2001:db8:1:2::/64");
    }

    @Test
    void resolve_ipv6DifferentPrefix64_differentKey() {
        assertThat(resolve("2001:db8:1:2::1")).isNotEqualTo(resolve("2001:db8:1:3::1"));
    }

    @Test
    void resolve_ipv4MappedIpv6_sameKeyAsIpv4() {
        assertThat(resolve("::ffff:5.5.5.5")).isEqualTo("5.5.5.5");
    }

    @Test
    void resolve_ipv6RemoteAddr_groupedToo() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:1:2:0:0:0:9");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("2001:db8:1:2::/64");
    }
}
