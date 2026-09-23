package com.project.custom.shared.api;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Customer-facing texts returned by the API ({@code Problem.detail}, {@code Message.text},
 * {@code FieldError.message}). The Polish copy lives only in {@code i18n/messages.properties}.
 */
@Component
public class UiMessages {

    private static final Locale UI_LOCALE = Locale.forLanguageTag("pl");

    private final MessageSource messageSource;

    UiMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, UI_LOCALE);
    }
}
