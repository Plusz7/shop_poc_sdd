import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useId, useState, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate } from 'react-router';
import { ApiError, type Schemas } from '../../api/client';
import { t, type MessageKey } from '../../i18n/t';
import { Banner } from '../../shared/Banner';
import { formatPln } from '../../shared/formatPln';
import { cartQueryKey, useCart } from '../cart/useCart';
import { usePlaceOrder } from './api';
import styles from './CheckoutPage.module.css';
import { checkoutFields, checkoutSchema, type CheckoutForm } from './checkoutSchema';

type Notice = { kind: 'warning' | 'error'; text: string };

/**
 * Checkout `/checkout` (US4-1..3, US4-7): delivery details and the summary the customer confirms. The summary
 * is the cart priced by the backend; when it changed meanwhile, the backend answers with the current cart,
 * which replaces the summary until the customer confirms again.
 */
export function CheckoutPage() {
  const { data: cart, isPending, isError, refetch } = useCart();
  const queryClient = useQueryClient();
  const placeOrder = usePlaceOrder();
  const [notice, setNotice] = useState<Notice | null>(null);
  const form = useForm<CheckoutForm>({
    resolver: zodResolver(checkoutSchema),
    defaultValues: { email: '', fullName: '', streetAndNumber: '', postalCode: '', city: '' },
  });
  const { errors } = form.formState;

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
  if (!cart.canPlaceOrder && !notice) {
    return <Navigate to="/cart" replace />;
  }

  const submit = form.handleSubmit((values) => {
    setNotice(null);
    placeOrder.mutate(
      { form: values, summary: cart },
      {
        onError: (error) => {
          if (!(error instanceof ApiError)) {
            setNotice({ kind: 'error', text: t('common.actionFailed') });
            return;
          }
          if (error.problem.cart) {
            queryClient.setQueryData(cartQueryKey, error.problem.cart);
          }
          switch (error.code) {
            case 'VALIDATION_ERROR':
              showFieldErrors(error.problem.errors ?? []);
              break;
            case 'SUMMARY_OUTDATED':
              setNotice({ kind: 'warning', text: t('checkout.summaryOutdated') });
              break;
            case 'CART_NOT_ORDERABLE':
              setNotice({ kind: 'error', text: t('checkout.cartNotOrderable') });
              break;
            case 'ORDER_ALREADY_PAID':
              setNotice({ kind: 'error', text: t('checkout.alreadyPaid') });
              break;
            case 'PAYMENT_UNAVAILABLE':
              setNotice({ kind: 'error', text: t('checkout.paymentUnavailable') });
              break;
            default:
              setNotice({ kind: 'error', text: t('common.actionFailed') });
          }
        },
      },
    );
  });

  function showFieldErrors(fieldErrors: Schemas['FieldError'][]) {
    let shown = false;
    for (const { field, message } of fieldErrors) {
      const name = checkoutFields.find((candidate) => candidate === field);
      if (name) {
        form.setError(name, { type: 'server', message }, { shouldFocus: !shown });
        shown = true;
      }
    }
    setNotice({ kind: 'error', text: t('checkout.invalid') });
  }

  const redirecting = placeOrder.isPending || placeOrder.isSuccess;

  return (
    <section className={styles.checkout}>
      <h1>{t('checkout.title')}</h1>
      {notice && (
        <Banner
          kind={notice.kind}
          action={!cart.canPlaceOrder && <Link to="/cart">{t('checkout.backToCart')}</Link>}
        >
          {notice.text}
        </Banner>
      )}
      <div className={styles.columns}>
        <form
          className={styles.form}
          onSubmit={submit}
          noValidate
          aria-labelledby="checkout-details"
        >
          <h2 id="checkout-details">{t('checkout.details')}</h2>
          <Field label="checkout.email" error={errors.email?.message}>
            {(props) => (
              <input type="email" autoComplete="email" {...props} {...form.register('email')} />
            )}
          </Field>
          <Field label="checkout.fullName" error={errors.fullName?.message}>
            {(props) => (
              <input type="text" autoComplete="name" {...props} {...form.register('fullName')} />
            )}
          </Field>
          <Field label="checkout.streetAndNumber" error={errors.streetAndNumber?.message}>
            {(props) => (
              <input
                type="text"
                autoComplete="street-address"
                {...props}
                {...form.register('streetAndNumber')}
              />
            )}
          </Field>
          <Field
            label="checkout.postalCode"
            hint="checkout.postalCodeHint"
            error={errors.postalCode?.message}
          >
            {(props) => (
              <input
                type="text"
                inputMode="numeric"
                autoComplete="postal-code"
                {...props}
                {...form.register('postalCode')}
              />
            )}
          </Field>
          <Field label="checkout.city" error={errors.city?.message}>
            {(props) => (
              <input
                type="text"
                autoComplete="address-level2"
                {...props}
                {...form.register('city')}
              />
            )}
          </Field>
          <p className={styles.country}>
            <span>{t('checkout.country')}</span> <strong>{t('checkout.countryPoland')}</strong>
          </p>
          <button
            type="submit"
            className={styles.submit}
            disabled={redirecting || !cart.canPlaceOrder}
          >
            {redirecting ? t('checkout.submitting') : t('checkout.submit')}
          </button>
        </form>
        <Summary cart={cart} />
      </div>
    </section>
  );
}

type FieldProps = {
  label: MessageKey;
  hint?: MessageKey;
  error?: string;
  children: (props: {
    id: string;
    'aria-invalid': boolean;
    'aria-describedby'?: string;
  }) => ReactNode;
};

/** A labelled input whose hint and error are linked with `aria-describedby` (accessibility, US4-2). */
function Field({ label, hint, error, children }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={styles.field}>
      <label htmlFor={id}>{t(label)}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && (
        <small id={hintId} className={styles.hint}>
          {t(hint)}
        </small>
      )}
      {error && (
        <small id={errorId} className={styles.error}>
          {error}
        </small>
      )}
    </div>
  );
}

function Summary({ cart }: { cart: Schemas['Cart'] }) {
  return (
    <aside className={styles.summary} aria-labelledby="checkout-summary">
      <h2 id="checkout-summary">{t('checkout.summary')}</h2>
      <ul className={styles.lines}>
        {cart.lines.map((line) => (
          <li key={line.productId}>
            <span className={styles.lineName}>{line.name}</span>
            <span className={styles.lineQuantity}>
              {t('checkout.lineQuantity', {
                quantity: line.quantity,
                price: formatPln(line.unitPriceMinor),
              })}
            </span>
            <strong>{formatPln(line.lineTotalMinor)}</strong>
          </li>
        ))}
      </ul>
      <p className={styles.row}>
        <span>{t('checkout.shipping')}</span>
        <span>{t('checkout.shippingFree')}</span>
      </p>
      <p className={`${styles.row} ${styles.total}`}>
        <span>{t('cart.total')}</span>
        <strong data-testid="checkout-total">{formatPln(cart.totalMinor)}</strong>
      </p>
    </aside>
  );
}
