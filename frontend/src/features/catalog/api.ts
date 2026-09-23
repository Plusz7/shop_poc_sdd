import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, unwrap } from '../../api/client';
import type { paths } from '../../api/schema';

export type ProductQuery = NonNullable<paths['/api/products']['get']['parameters']['query']>;

export function useCategories() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: () => unwrap(api.GET('/api/categories')),
    staleTime: 5 * 60_000,
  });
}

/** Product list for the URL filters; the previous page stays visible while the next one loads. */
export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: ['products', query],
    queryFn: () => unwrap(api.GET('/api/products', { params: { query } })),
    placeholderData: keepPreviousData,
  });
}

export function useProduct(id: number | undefined) {
  return useQuery({
    queryKey: ['product', id],
    queryFn: () => unwrap(api.GET('/api/products/{id}', { params: { path: { id: id! } } })),
    enabled: id !== undefined,
  });
}
