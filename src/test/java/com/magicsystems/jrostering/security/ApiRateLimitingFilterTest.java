package com.magicsystems.jrostering.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiRateLimitingFilterTest {

    private ApiRateLimitingFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new ApiRateLimitingFilter();
        chain  = mock(FilterChain.class);
    }

    @Test
    void nonApiPath_passesThrough_withoutConsuming() throws Exception {
        MockHttpServletRequest request  = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void apiRequests_withinLimit_passThrough() throws Exception {
        for (int i = 0; i < ApiRateLimitingFilter.CAPACITY; i++) {
            MockHttpServletRequest  req  = apiRequest("10.0.0.1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            filter.doFilter(req, resp, chain);

            assertThat(resp.getStatus())
                    .as("Request %d should pass", i + 1)
                    .isEqualTo(200);
        }
        verify(chain, times(ApiRateLimitingFilter.CAPACITY)).doFilter(any(), any());
    }

    @Test
    void apiRequests_exceedingLimit_return429() throws Exception {
        FilterChain passingChain = mock(FilterChain.class);

        // Drain the bucket for IP 10.0.0.2
        for (int i = 0; i < ApiRateLimitingFilter.CAPACITY; i++) {
            filter.doFilter(apiRequest("10.0.0.2"), new MockHttpServletResponse(), passingChain);
        }

        // (CAPACITY + 1)th request must be rejected
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilter(apiRequest("10.0.0.2"), overLimitResponse, mock(FilterChain.class));

        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
        assertThat(overLimitResponse.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void differentIps_haveSeparateBuckets() throws Exception {
        // Drain bucket for 10.0.0.3 entirely
        for (int i = 0; i < ApiRateLimitingFilter.CAPACITY; i++) {
            filter.doFilter(apiRequest("10.0.0.3"), new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // A request from a different IP should still get through
        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        filter.doFilter(apiRequest("10.0.0.4"), otherIpResponse, chain);

        assertThat(otherIpResponse.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static MockHttpServletRequest apiRequest(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/staff/1");
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
