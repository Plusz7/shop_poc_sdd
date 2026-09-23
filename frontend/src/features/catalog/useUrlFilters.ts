import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import type { ProductQuery } from './api';

export type SortOption = NonNullable<ProductQuery['sort']>;

export const SORT_OPTIONS: readonly SortOption[] = ['name_asc', 'price_asc', 'price_desc'];
const DEFAULT_SORT: SortOption = 'name_asc';
export const PAGE_SIZE = 24;

/** Filter state of the product list as kept in the URL (contracts/frontend-routes.md). */
export type CatalogFilters = {
  category?: string;
  q?: string;
  /** grosze */
  minPrice?: number;
  /** grosze */
  maxPrice?: number;
  sort: SortOption;
  /** 1-based, as shown in the URL */
  page: number;
};

export type FilterPatch = Partial<Omit<CatalogFilters, 'page'>>;

const FILTER_KEYS = ['category', 'q', 'minPrice', 'maxPrice', 'sort'] as const;

/**
 * Single source of truth for the list filters: the URL query string, so a link to the results can be
 * shared (FR-004). Changing a filter resets the page to 1; changing the page keeps the filters.
 */
export function useUrlFilters() {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = useMemo(() => readFilters(searchParams), [searchParams]);

  const apiQuery = useMemo<ProductQuery>(
    () =>
      withoutUndefined({
        category: filters.category,
        q: filters.q,
        minPrice: filters.minPrice,
        maxPrice: filters.maxPrice,
        sort: filters.sort,
        page: filters.page - 1,
        size: PAGE_SIZE,
      }),
    [filters],
  );

  const update = useCallback(
    (patch: FilterPatch) => setSearchParams(writeFilters({ ...filters, ...patch, page: 1 })),
    [filters, setSearchParams],
  );

  const setPage = useCallback(
    (page: number) => setSearchParams(writeFilters({ ...filters, page })),
    [filters, setSearchParams],
  );

  const clear = useCallback(() => setSearchParams(new URLSearchParams()), [setSearchParams]);

  /** Query string (with `?`) for a link applying the patch - e.g. category navigation. */
  const searchFor = useCallback(
    (patch: FilterPatch) => {
      const search = writeFilters({ ...filters, ...patch, page: 1 }).toString();
      return search ? `?${search}` : '';
    },
    [filters],
  );

  const hasFilters = FILTER_KEYS.some((key) => key !== 'sort' && filters[key] !== undefined);

  return { filters, apiQuery, update, setPage, clear, searchFor, hasFilters };
}

function readFilters(params: URLSearchParams): CatalogFilters {
  const sort = params.get('sort');
  return withoutUndefined({
    category: nonBlank(params.get('category')),
    q: nonBlank(params.get('q')),
    minPrice: nonNegativeInteger(params.get('minPrice')),
    maxPrice: nonNegativeInteger(params.get('maxPrice')),
    sort: SORT_OPTIONS.includes(sort as SortOption) ? (sort as SortOption) : DEFAULT_SORT,
    page: positiveInteger(params.get('page')) ?? 1,
  });
}

function writeFilters(filters: CatalogFilters): URLSearchParams {
  const params = new URLSearchParams();
  const category = nonBlank(filters.category);
  const q = nonBlank(filters.q);
  if (category) params.set('category', category);
  if (q) params.set('q', q);
  if (filters.minPrice !== undefined) params.set('minPrice', String(filters.minPrice));
  if (filters.maxPrice !== undefined) params.set('maxPrice', String(filters.maxPrice));
  if (filters.sort !== DEFAULT_SORT) params.set('sort', filters.sort);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

/** Parses an amount typed in PLN ("49,99", "49.9", "50") into grosze; `undefined` when invalid. */
export function plnToMinor(input: string): number | undefined {
  const match = /^(\d{1,9})(?:[.,](\d{1,2}))?$/.exec(input.trim());
  if (!match) {
    return undefined;
  }
  const fraction = (match[2] ?? '').padEnd(2, '0');
  return Number(match[1]) * 100 + Number(fraction);
}

/** Formats grosze for a PLN input field: "50", "49,99", "49,90". */
export function minorToPln(minor: number): string {
  const whole = Math.floor(minor / 100);
  const fraction = minor % 100;
  return fraction === 0 ? String(whole) : `${whole},${String(fraction).padStart(2, '0')}`;
}

function nonBlank(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function nonNegativeInteger(value: string | null): number | undefined {
  return value !== null && /^\d{1,15}$/.test(value) ? Number(value) : undefined;
}

function positiveInteger(value: string | null): number | undefined {
  const number = nonNegativeInteger(value);
  return number !== undefined && number > 0 ? number : undefined;
}

function withoutUndefined<T extends object>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined)) as T;
}
