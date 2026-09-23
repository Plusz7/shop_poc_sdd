package com.project.custom.catalog.domain;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results; {@code page} counts from 0.
 */
public record ResultPage<T>(List<T> items, int page, int size, long totalElements) {

    public ResultPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public <R> ResultPage<R> map(Function<T, R> mapper) {
        return new ResultPage<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
