package com.project.custom.shared.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationFilterTest {

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationFilter filter = new CorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void requestWithoutHeaderGetsANewUuid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/products"), response, (req, res) -> { });

        assertThat(response.getHeader("X-Request-Id")).matches(UUID);
    }

    @Test
    void validHeaderIsKeptAndVisibleInTheMdcDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader("X-Request-Id", "abc-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInMdc.set(MDC.get("requestId")));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("abc-12345");
        assertThat(seenInMdc).hasValue("abc-12345");
        assertThat(MDC.get("requestId")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc-123", "<script>alert(1)</script>", "abcdefgh\r\nX-Injected: 1",
            "a123456789012345678901234567890123456789012345678901234567890abcd"})
    void invalidHeaderIsReplacedWithANewUuid(String supplied) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("X-Request-Id", supplied);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Request-Id")).matches(UUID);
    }

    @Test
    void mdcIsClearedAlsoAfterAnException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void runsBeforeTheGuestIdFilter() {
        assertThat(WebConfig.CORRELATION_FILTER_ORDER).isLessThan(WebConfig.GUEST_ID_FILTER_ORDER);
    }
}
