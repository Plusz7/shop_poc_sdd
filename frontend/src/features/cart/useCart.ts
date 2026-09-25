import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api, unwrap, type Schemas } from '../../api/client';
import { t } from '../../i18n/t';
import { useToast } from '../../shared/toast/useToast';

/** Query key of the priced cart; invalidated after every cart mutation (frontend-routes.md). */
export const cartQueryKey = ['cart'] as const;

export type AddLine = { productId: number; quantity: number };

export function useCart() {
  return useQuery({
    queryKey: cartQueryKey,
    queryFn: () => unwrap(api.GET('/api/cart')),
  });
}

/** Adds a product to the cart; confirms with a toast and explains a rejected addition (US2-3, US2-4). */
export function useAddToCart() {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (line: AddLine) => unwrap(api.POST('/api/cart/lines', { body: line })),
    onSuccess: (cart) => {
      queryClient.setQueryData(cartQueryKey, cart);
      toast.show(t('cart.added'), 'success');
    },
    onError: (error) => {
      toast.show(addErrorMessage(error), 'error');
      if (error instanceof ApiError && error.code === 'PRODUCT_UNAVAILABLE') {
        // the page still shows the product as available - refresh its status
        void queryClient.invalidateQueries({ queryKey: ['product'] });
        void queryClient.invalidateQueries({ queryKey: ['products'] });
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: cartQueryKey }),
  });
}

function addErrorMessage(error: Error): string {
  if (!(error instanceof ApiError)) {
    return t('common.actionFailed');
  }
  switch (error.code) {
    case 'QUANTITY_EXCEEDS_LIMIT': {
      const max = error.problem.maxQuantity ?? 0;
      return max > 0 ? t('cart.quantityExceedsLimit', { max }) : t('cart.lineFull');
    }
    case 'PRODUCT_UNAVAILABLE':
      return t('cart.productUnavailable');
    case 'NOT_FOUND':
      return t('product.notFound');
    default:
      return t('common.actionFailed');
  }
}

/**
 * A cart edit (US3): shows the returned cart at once, explains a failure and refetches the cart either way,
 * so the page and the header counter always show what the backend holds.
 */
function useCartEdit<TVariables>(
  mutationFn: (variables: TVariables) => Promise<Schemas['Cart']>,
  onCart?: (cart: Schemas['Cart']) => void,
) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn,
    onSuccess: (cart) => {
      queryClient.setQueryData(cartQueryKey, cart);
      onCart?.(cart);
    },
    onError: (error) => toast.show(editErrorMessage(error), 'error'),
    onSettled: () => queryClient.invalidateQueries({ queryKey: cartQueryKey }),
  });
}

/** Sets a line quantity (0 removes the line); a quantity capped at the stock is announced with a toast. */
export function useChangeQuantity() {
  const toast = useToast();
  return useCartEdit(
    ({ productId, quantity }: { productId: number; quantity: number }) =>
      unwrap(
        api.PUT('/api/cart/lines/{productId}', {
          params: { path: { productId } },
          body: { quantity },
        }),
      ),
    (cart) =>
      cart.messages
        .filter((message) => message.code === 'QUANTITY_CAPPED')
        .forEach((message) => toast.show(message.text, 'info')),
  );
}

export function useRemoveLine() {
  return useCartEdit((productId: number) =>
    unwrap(api.DELETE('/api/cart/lines/{productId}', { params: { path: { productId } } })),
  );
}

export function useClearCart() {
  return useCartEdit(() => unwrap(api.DELETE('/api/cart')));
}

/** The customer acknowledged the price changes (R-09). */
export function useAcceptPrices() {
  return useCartEdit(() => unwrap(api.POST('/api/cart/accept-prices')));
}

function editErrorMessage(error: Error): string {
  if (!(error instanceof ApiError)) {
    return t('common.actionFailed');
  }
  switch (error.code) {
    case 'CONCURRENCY_CONFLICT':
      return t('cart.concurrencyConflict');
    case 'PRODUCT_UNAVAILABLE':
      return t('cart.productUnavailable');
    case 'NOT_FOUND':
      return t('cart.lineNotFound');
    case 'VALIDATION_ERROR':
      return t('cart.quantityInvalid');
    default:
      return t('common.actionFailed');
  }
}
