package com.project.custom.cart.domain;

import java.util.Objects;

/**
 * A notice about one priced cart line, e.g. its price changed since it was added.
 */
public record CartProblem(CartProblemCode code, long productId) {

    public CartProblem {
        Objects.requireNonNull(code, "code");
    }
}
