import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api, unwrap } from '../../api/client';
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
