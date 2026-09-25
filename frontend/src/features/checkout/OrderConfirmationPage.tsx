import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, type Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { Banner } from '../../shared/Banner';
import { formatPln } from '../../shared/formatPln';
import { cartQueryKey } from '../cart/useCart';
import { useOrder } from './api';
import styles from './OrderConfirmationPage.module.css';

/**
 * Confirmation `/orders/:number` (US4-4, US4-6). Shows only what the API reports: returning from the payment
 * page never marks anything paid (FR-019) - the status comes from the verified webhook.
 */
export function OrderConfirmationPage() {
  const { number = '' } = useParams();
  const { data: order, error, isPending, isError, refetch, timedOut } = useOrder(number);
  const queryClient = useQueryClient();
  const paid = order?.status === 'PAID';

  useEffect(() => {
    if (paid) {
      // the webhook cleared the cart - the header counter must show 0
      void queryClient.invalidateQueries({ queryKey: cartQueryKey });
    }
  }, [paid, queryClient]);

  if (isPending) {
    return <p role="status">{t('common.loading')}</p>;
  }
  if (isError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <section className={styles.order}>
          <h1>{t('order.notFound')}</h1>
          <p>{t('order.notFoundText')}</p>
          <Link to="/">{t('order.backToShop')}</Link>
        </section>
      );
    }
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

  return (
    <section className={styles.order}>
      <h1>{t('order.title')}</h1>
      <p className={styles.number}>{t('order.number', { number: order.number })}</p>
      <Status order={order} timedOut={timedOut} />
      <ul className={styles.lines} aria-label={t('order.lines')}>
        {order.lines.map((line, index) => (
          <li key={index}>
            <span>{line.name}</span>
            <span className={styles.quantity}>
              {t('checkout.lineQuantity', {
                quantity: line.quantity,
                price: formatPln(line.unitPriceMinor),
              })}
            </span>
            <strong>{formatPln(line.lineTotalMinor)}</strong>
          </li>
        ))}
      </ul>
      <p className={styles.total}>
        <span>{t('order.total')}</span>
        <strong>{formatPln(order.totalMinor)}</strong>
      </p>
      <Link to="/">{t('order.backToShop')}</Link>
    </section>
  );
}

function Status({ order, timedOut }: { order: Schemas['Order']; timedOut: boolean }) {
  switch (order.status) {
    case 'AWAITING_PAYMENT':
      return timedOut ? (
        <Banner kind="warning">{t('order.verifyingSlow')}</Banner>
      ) : (
        <Banner kind="info">
          <span className={styles.spinner} aria-hidden="true" />
          {t('order.verifying')}
        </Banner>
      );
    case 'PAID':
      return <Banner kind="info">{t('order.paid')}</Banner>;
    case 'PAYMENT_FAILED':
      return (
        <Banner kind="error" action={<Link to="/cart">{t('order.failedAction')}</Link>}>
          {t('order.failed')}
        </Banner>
      );
    case 'NEEDS_REVIEW':
      return <Banner kind="warning">{t('order.needsReview')}</Banner>;
  }
}
