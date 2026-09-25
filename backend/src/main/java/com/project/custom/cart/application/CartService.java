package com.project.custom.cart.application;

import com.project.custom.cart.domain.Cart;
import com.project.custom.cart.domain.CartId;
import com.project.custom.cart.domain.CartLineNotFoundException;
import com.project.custom.cart.domain.CartPricing;
import com.project.custom.cart.domain.CartProduct;
import com.project.custom.cart.domain.CartRepository;
import com.project.custom.cart.domain.PricedCart;
import com.project.custom.cart.domain.ProductAvailability;
import com.project.custom.cart.domain.QuantityCapped;
import com.project.custom.catalog.CatalogQueryFacade;
import com.project.custom.catalog.PricingProductDto;
import com.project.custom.shared.domain.GuestId;
import com.project.custom.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cart use cases of the guest (US2, US3): reading the priced cart, adding products and editing the cart.
 * Prices always come from the catalog (FR-010); every change returns the freshly priced cart.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CatalogQueryFacade catalogQueryFacade;
    private final Clock clock;

    CartService(CartRepository cartRepository, CatalogQueryFacade catalogQueryFacade, Clock clock) {
        this.cartRepository = cartRepository;
        this.catalogQueryFacade = catalogQueryFacade;
        this.clock = clock;
    }

    /** The guest's cart priced with current catalog data; an empty cart when the guest has none yet. */
    @Transactional(readOnly = true)
    public PricedCart price(GuestId guestId) {
        return cartRepository.findByGuest(guestId).map(this::price).orElseGet(PricedCart::empty);
    }

    /**
     * Adds {@code quantity} items of the product, creating the cart on the first addition.
     *
     * @throws CartProductNotFoundException                                  when the product does not exist
     * @throws com.project.custom.cart.domain.ProductUnavailableException    when it cannot be bought
     * @throws com.project.custom.cart.domain.QuantityExceedsLimitException when the line would exceed the limit
     */
    @Transactional
    public PricedCart add(GuestId guestId, long productId, int quantity) {
        CartProduct product = products(Set.of(productId)).get(productId);
        if (product == null) {
            throw new CartProductNotFoundException(productId);
        }
        Cart cart = cartRepository.findByGuest(guestId)
                .orElseGet(() -> Cart.create(CartId.random(), guestId, clock.instant()));
        cart.add(product, quantity, clock.instant());
        cartRepository.save(cart);
        return price(cart);
    }

    /**
     * Sets the quantity of a line, capped at what is available now; {@code 0} removes the line (US3-2..4).
     *
     * @throws CartLineNotFoundException                                  when the cart has no line for the product
     * @throws com.project.custom.cart.domain.ProductUnavailableException when a positive quantity is set for an
     *                                                                    unavailable product
     */
    @Transactional
    public QuantityChange changeQuantity(GuestId guestId, long productId, int quantity) {
        Cart cart = cartRepository.findByGuest(guestId).orElseThrow(() -> new CartLineNotFoundException(productId));
        CartProduct product = products(Set.of(productId)).get(productId);
        int maxQuantity = product != null && product.isAvailable() ? product.maxQuantity() : 0;
        QuantityCapped capped = cart.changeQuantity(productId, quantity, maxQuantity, clock.instant()).orElse(null);
        cartRepository.save(cart);
        return new QuantityChange(price(cart), productId, capped);
    }

    /** Removes a line; removing a line that is not in the cart is not an error (idempotent). */
    @Transactional
    public PricedCart remove(GuestId guestId, long productId) {
        return cartRepository.findByGuest(guestId).map(cart -> {
            cart.remove(productId, clock.instant());
            cartRepository.save(cart);
            return price(cart);
        }).orElseGet(PricedCart::empty);
    }

    /** Removes all lines (FR-009). */
    @Transactional
    public PricedCart clear(GuestId guestId) {
        cartRepository.findByGuest(guestId).ifPresent(cart -> {
            cart.clear(clock.instant());
            cartRepository.save(cart);
        });
        return PricedCart.empty();
    }

    /** The customer acknowledged the price changes; the current prices stop being reported as changed (R-09). */
    @Transactional
    public PricedCart acceptPrices(GuestId guestId) {
        return cartRepository.findByGuest(guestId).map(cart -> {
            Map<Long, CartProduct> products = products(cart.productIds());
            cart.acceptPrices(products.values().stream()
                    .collect(Collectors.toMap(CartProduct::productId, CartProduct::price)), clock.instant());
            cartRepository.save(cart);
            return CartPricing.price(cart, products);
        }).orElseGet(PricedCart::empty);
    }

    private PricedCart price(Cart cart) {
        return CartPricing.price(cart, products(cart.productIds()));
    }

    private Map<Long, CartProduct> products(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return catalogQueryFacade.getForPricing(productIds).values().stream()
                .collect(Collectors.toMap(PricingProductDto::productId, CartService::toCartProduct));
    }

    private static CartProduct toCartProduct(PricingProductDto product) {
        return new CartProduct(product.productId(), product.name(), product.imageUrl(),
                Money.pln(product.priceMinor()), ProductAvailability.valueOf(product.status()),
                product.maxQuantity());
    }
}
