package com.project.custom.shared.api;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.infrastructure.config.AppProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuestIdFilterTest {

    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private final GuestIdFilter secureFilter = new GuestIdFilter(properties(true));
    private final GuestIdFilter localFilter = new GuestIdFilter(properties(false));

    @Test
    void firstCartRequestSetsGuestCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .matches("shop_guest=" + UUID_V4 + ";.*")
                .contains("Path=/")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure");
        GuestId guestId = (GuestId) request.getAttribute(GuestIdFilter.GUEST_ID_ATTRIBUTE);
        assertThat(setCookie).startsWith("shop_guest=" + guestId.value());
    }

    @Test
    void localProfileCookieIsNotSecure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        localFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Secure");
    }

    @Test
    void existingCookieKeepsTheSameGuest() throws Exception {
        UUID existing = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/ORD-0000000000");
        request.setCookies(new Cookie("shop_guest", existing.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(GuestIdFilter.GUEST_ID_ATTRIBUTE)).isEqualTo(new GuestId(existing));
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void cartChangeRenewsCookieMaxAge() throws Exception {
        UUID existing = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cart/lines");
        request.setCookies(new Cookie("shop_guest", existing.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("shop_guest=" + existing)
                .contains("Max-Age=2592000");
    }

    @Test
    void invalidCookieIsReplacedWithANewGuest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.setCookies(new Cookie("shop_guest", "not-a-uuid"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).matches("shop_guest=" + UUID_V4 + ";.*");
        assertThat(request.getAttribute(GuestIdFilter.GUEST_ID_ATTRIBUTE)).isNotNull();
    }

    @Test
    void catalogRequestsDoNotGetTheCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(request.getAttribute(GuestIdFilter.GUEST_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void webhookRequestsDoNotGetTheCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/stripe/webhook");
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static AppProperties properties(boolean secure) {
        return new AppProperties(URI.create("http://localhost:5173"),
                new AppProperties.Cookie(secure, Duration.ofDays(30)));
    }
}
