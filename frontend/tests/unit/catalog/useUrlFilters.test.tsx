import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { minorToPln, plnToMinor, useUrlFilters } from '../../../src/features/catalog/useUrlFilters';

function renderFilters(url: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
  );
  return renderHook(() => ({ state: useUrlFilters(), location: useLocation() }), { wrapper });
}

const searchOf = (result: { current: { location: { search: string } } }) =>
  new URLSearchParams(result.current.location.search);

describe('useUrlFilters', () => {
  it('reads every filter from the URL and maps the page to the 0-based API page', () => {
    const { result } = renderFilters(
      '/?category=kitchen&q=lodz&minPrice=5000&maxPrice=20000&sort=price_desc&page=3',
    );

    expect(result.current.state.filters).toEqual({
      category: 'kitchen',
      q: 'lodz',
      minPrice: 5000,
      maxPrice: 20000,
      sort: 'price_desc',
      page: 3,
    });
    expect(result.current.state.apiQuery).toEqual({
      category: 'kitchen',
      q: 'lodz',
      minPrice: 5000,
      maxPrice: 20000,
      sort: 'price_desc',
      page: 2,
      size: 24,
    });
  });

  it('uses the defaults for an empty URL', () => {
    const { result } = renderFilters('/');

    expect(result.current.state.filters).toEqual({ sort: 'name_asc', page: 1 });
    expect(result.current.state.apiQuery).toEqual({ sort: 'name_asc', page: 0, size: 24 });
    expect(result.current.state.hasFilters).toBe(false);
  });

  it('ignores invalid values in a hand-edited URL', () => {
    const { result } = renderFilters(
      '/?sort=xyz&page=abc&minPrice=-1&maxPrice=1.5&category=&q=%20%20',
    );

    expect(result.current.state.filters).toEqual({ sort: 'name_asc', page: 1 });
  });

  it('resets the page to 1 when a filter changes', () => {
    const { result } = renderFilters('/?q=lodz&page=3');

    act(() => result.current.state.update({ category: 'books' }));

    const search = searchOf(result);
    expect(search.get('category')).toBe('books');
    expect(search.get('q')).toBe('lodz');
    expect(search.has('page')).toBe(false);
    expect(result.current.state.filters.page).toBe(1);
  });

  it('keeps the filters when the page changes', () => {
    const { result } = renderFilters('/?category=kitchen&sort=price_asc');

    act(() => result.current.state.setPage(2));

    const search = searchOf(result);
    expect(search.get('category')).toBe('kitchen');
    expect(search.get('sort')).toBe('price_asc');
    expect(search.get('page')).toBe('2');

    act(() => result.current.state.setPage(1));
    expect(searchOf(result).has('page')).toBe(false);
  });

  it('omits the default sort from the URL', () => {
    const { result } = renderFilters('/?sort=price_desc');

    act(() => result.current.state.update({ sort: 'name_asc' }));

    expect(searchOf(result).has('sort')).toBe(false);
  });

  it('writes prices entered in PLN as grosze', () => {
    const { result } = renderFilters('/');

    act(() =>
      result.current.state.update({ minPrice: plnToMinor('50'), maxPrice: plnToMinor('199,99') }),
    );

    const search = searchOf(result);
    expect(search.get('minPrice')).toBe('5000');
    expect(search.get('maxPrice')).toBe('19999');
    expect(result.current.state.hasFilters).toBe(true);
  });

  it('removes a filter set to undefined or an empty string', () => {
    const { result } = renderFilters('/?category=kitchen&q=lodz&minPrice=5000');

    act(() => result.current.state.update({ category: undefined, q: '', minPrice: undefined }));

    expect(result.current.location.search).toBe('');
  });

  it('clears all filters', () => {
    const { result } = renderFilters('/?category=kitchen&q=lodz&page=2&sort=price_asc');

    act(() => result.current.state.clear());

    expect(result.current.location.search).toBe('');
  });
});

describe('PLN ↔ grosze conversion', () => {
  it.each([
    ['50', 5000],
    ['49,99', 4999],
    ['49.9', 4990],
    [' 200 ', 20000],
    ['0', 0],
  ])('parses "%s" as %i grosze', (input, expected) => {
    expect(plnToMinor(input)).toBe(expected);
  });

  it.each(['', '   ', 'abc', '-5', '1,234', '12a'])('rejects "%s"', (input) => {
    expect(plnToMinor(input)).toBeUndefined();
  });

  it.each([
    [5000, '50'],
    [4999, '49,99'],
    [4990, '49,90'],
    [5, '0,05'],
  ])('formats %i grosze as "%s"', (minor, expected) => {
    expect(minorToPln(minor)).toBe(expected);
  });
});
