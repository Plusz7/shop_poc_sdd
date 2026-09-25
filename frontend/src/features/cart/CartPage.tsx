import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useId } from 'react';
import { Link, useSearchParams } from 'react-router';
import type { Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { Banner } from '../../shared/Banner';
import { formatPln } from '../../shared/formatPln';
import { CartLineItem } from './CartLineItem';
import styles from './CartPage.module.css';
import { cartQueryKey, useAcceptPrices, useCart, useClearCart } from './useCart';

/** Problems that keep the customer from placing an order (FR-014). */
const BLOCKING_CODES = new Set(['PRODUCT_UNAVAILABLE', 'QUANTITY_EXCEEDS_STOCK']);

/**
 * Cart `/cart` (US3): lines priced by the API, editing, price change notices and the total. The frontend
 * never calculates prices - the total and `canPlaceOrder` come from the backend.
 */
export function CartPage() {
  const { data: cart, isPending, isError, refetch } = useCart();
  const acceptPrices = useAcceptPrices();
  const clearCart = useClearCart();
  const paymentCanceled = useSearchParams()[0].get('payment') === 'canceled';
  const queryClient = useQueryClient();

  useEffect(() => {
    if (paymentCanceled) {
      // back from the payment page: the cart may have been cleared meanwhile by a paid order in another tab
      void queryClient.invalidateQueries({ queryKey: cartQueryKey });
    }
  }, [paymentCanceled, queryClient]);

  if (isPending) {
    return <p role="status">{t('common.loading')}</p>;
  }
  if (isError) {
    return (
      <Banner
        kind="error"
        action={
          <button type="button" onClick={() => refetch()}>
            {t('common.retry')}
          </button>
        }
      >
        {t('common.error')}
      </Banner>
    );
  }

  if (cart.lines.length === 0) {
    return (
      <section className={styles.cart}>
        <h1>{t('cart.title')}</h1>
        {paymentCanceled && <Banner kind="warning">{t('cart.paymentCanceled')}</Banner>}
        <div className={styles.empty}>
          <p>{t('cart.empty')}</p>
          <Link to="/">{t('cart.backToShop')}</Link>
        </div>
      </section>
    );
  }

  return (
    <section className={styles.cart}>
      <h1>{t('cart.title')}</h1>
      {paymentCanceled && <Banner kind="warning">{t('cart.paymentCanceled')}</Banner>}
      {cart.lines.some((line) => line.priceChanged) && (
        <Banner
          kind="warning"
          action={
            <button
              type="button"
              disabled={acceptPrices.isPending}
              onClick={() => acceptPrices.mutate()}
            >
              {t('cart.acceptPrices')}
            </button>
          }
        >
          {t('cart.priceChanged')}
        </Banner>
      )}
      <ul className={styles.lines} aria-label={t('cart.lines')}>
        {cart.lines.map((line) => (
          <CartLineItem key={line.productId} line={line} />
        ))}
      </ul>
      <div className={styles.summary}>
        <button
          type="button"
          className={styles.clear}
          disabled={clearCart.isPending}
          onClick={() => clearCart.mutate()}
        >
          {t('cart.clear')}
        </button>
        <p className={styles.total}>
          <span>{t('cart.total')}</span>
          <strong>{formatPln(cart.totalMinor)}</strong>
        </p>
      </div>
      <Checkout cart={cart} />
    </section>
  );
}

function Checkout({ cart }: { cart: Schemas['Cart'] }) {
  const hintId = useId();
  if (cart.canPlaceOrder) {
    return (
      <div className={styles.checkout}>
        <Link to="/checkout" className={styles.checkoutButton}>
          {t('cart.checkout')}
        </Link>
      </div>
    );
  }
  const blocking = cart.messages.filter((message) => BLOCKING_CODES.has(message.code));
  return (
    <div className={styles.checkout}>
      <button type="button" className={styles.checkoutButton} disabled aria-describedby={hintId}>
        {t('cart.checkout')}
      </button>
      <div id={hintId} className={styles.hint}>
        {blocking.length > 0 ? (
          blocking.map((message) => (
            <p key={`${message.code}-${message.productId}`}>{message.text}</p>
          ))
        ) : (
          <p>{t('cart.checkoutBlocked')}</p>
        )}
      </div>
    </div>
  );
}
