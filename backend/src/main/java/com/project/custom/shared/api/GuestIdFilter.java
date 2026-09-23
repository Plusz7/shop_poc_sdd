package com.project.custom.shared.api;

import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.infrastructure.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Identifies the anonymous shopper by the {@code shop_guest} cookie (research R-08). Runs only for
 * {@code /api/cart*} and {@code /api/orders*}; the catalog and the Stripe webhook never get the cookie.
 */
class GuestIdFilter extends OncePerRequestFilter {

    static final String GUEST_ID_ATTRIBUTE = GuestId.class.getName();
    static final String COOKIE_NAME = "shop_guest";

    private final AppProperties.Cookie cookieSettings;

    GuestIdFilter(AppProperties appProperties) {
        this.cookieSettings = appProperties.cookie();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.startsWith("/api/cart") || path.startsWith("/api/orders"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<GuestId> existing = readCookie(request);
        GuestId guestId = existing.orElseGet(GuestId::random);
        request.setAttribute(GUEST_ID_ATTRIBUTE, guestId);

        if (existing.isEmpty() || isCartChange(request)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(guestId).toString());
        }
        chain.doFilter(request, response);
    }

    private Optional<GuestId> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                try {
                    return Optional.of(GuestId.fromString(cookie.getValue()));
                } catch (IllegalArgumentException invalid) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isCartChange(HttpServletRequest request) {
        String method = request.getMethod();
        return request.getRequestURI().contains("/api/cart")
                && !HttpMethod.GET.matches(method)
                && !HttpMethod.HEAD.matches(method);
    }

    private ResponseCookie cookie(GuestId guestId) {
        return ResponseCookie.from(COOKIE_NAME, guestId.value().toString())
                .httpOnly(true)
                .secure(cookieSettings.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(cookieSettings.maxAge())
                .build();
    }
}
