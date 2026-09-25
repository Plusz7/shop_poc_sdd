import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, unwrap, type Schemas } from '../../api/client';
import { redirectTo } from '../../shared/redirectTo';
import type { CheckoutForm } from './checkoutSchema';

/** How often the confirmation page asks for the payment status, and for how long (US4-6). */
export const ORDER_POLL_INTERVAL_MS = 2_000;
export const ORDER_POLL_LIMIT_MS = 60_000;

export const orderQueryKey = (number: string) => ['order', number] as const;

/**
 * Places the order for the summary the customer saw and sends them to the hosted payment page. The confirmed
 * prices are compared by the backend only; the amount is always priced there (R-14).
 */
export function usePlaceOrder() {
  return useMutation({
    mutationFn: ({ form, summary }: { form: CheckoutForm; summary: Schemas['Cart'] }) =>
      unwrap(
        api.POST('/api/orders', {
          body: {
            ...form,
            confirmedSummary: {
              lines: summary.lines.map(({ productId, quantity, unitPriceMinor }) => ({
                productId,
                quantity,
                unitPriceMinor,
              })),
              totalMinor: summary.totalMinor,
            },
          },
        }),
      ),
    onSuccess: ({ paymentUrl }) => redirectTo(paymentUrl),
  });
}

/**
 * The order of the confirmation page. While the payment is being verified the status is polled every 2 s,
 * for at most 60 s; `timedOut` then tells the page to stop waiting.
 */
export function useOrder(number: string) {
  const [timedOut, setTimedOut] = useState(false);
  const query = useQuery({
    queryKey: orderQueryKey(number),
    queryFn: () => unwrap(api.GET('/api/orders/{number}', { params: { path: { number } } })),
    refetchInterval: (current) =>
      current.state.data?.status === 'AWAITING_PAYMENT' && !timedOut
        ? ORDER_POLL_INTERVAL_MS
        : false,
  });
  const awaiting = query.data?.status === 'AWAITING_PAYMENT';

  useEffect(() => {
    if (!awaiting) {
      return undefined;
    }
    const timer = setTimeout(() => setTimedOut(true), ORDER_POLL_LIMIT_MS);
    return () => clearTimeout(timer);
  }, [awaiting]);

  return { ...query, timedOut };
}
