package com.project.custom.shared.api;

import com.project.custom.shared.domain.GuestId;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets a controller declare a {@link GuestId} parameter; the value is set by {@link GuestIdFilter}.
 */
class GuestIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return GuestId.class.equals(parameter.getParameterType());
    }

    @Override
    public GuestId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object guestId = webRequest.getAttribute(GuestIdFilter.GUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (guestId == null) {
            throw new IllegalStateException("GuestId is available only for /api/cart* and /api/orders* requests");
        }
        return (GuestId) guestId;
    }
}
