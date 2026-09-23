import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { formatPln } from '../../../src/shared/formatPln';
import { apiUrl } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

// Testing Library normalizes the non-breaking spaces of Intl output in the DOM, not in the matcher
const price = (minor: number) => formatPln(minor).replace(/\s/gu, ' ');

const categories: Schemas['Category'][] = [
  { slug: 'electronics', name: 'Elektronika' },
  { slug: 'kitchen', name: 'Kuchnia' },
];

const mug: Schemas['ProductSummary'] = {
  id: 1,
  name: 'Kubek ceramiczny Łódź',
  priceMinor: 5900,
  status: 'AVAILABLE',
  image: { url: '/images/kitchen.svg', alt: 'Kubek ceramiczny Łódź' },
  maxAddable: 25,
};

const lamp: Schemas['ProductSummary'] = {
  id: 2,
  name: 'Lampka biurkowa LED Duo',
  priceMinor: 12900,
  status: 'LOW_STOCK',
  image: { url: '/images/office.svg', alt: 'Lampka biurkowa LED Duo' },
  maxAddable: 2,
};

const speaker: Schemas['ProductSummary'] = {
  id: 5,
  name: 'Głośnik przenośny Wave',
  priceMinor: 18900,
  status: 'UNAVAILABLE',
  image: { url: '/images/electronics.svg', alt: 'Głośnik przenośny Wave' },
  maxAddable: 0,
};

function result(products: Schemas['ProductSummary'][], page = 0, totalPages = 1) {
  return { products, page, size: 24, totalElements: products.length, totalPages };
}

/** Serves the catalog and records the query string of every product search. */
function serveCatalog(products: Schemas['ProductSummary'][], totalPages = 1) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get(apiUrl('/api/categories'), () => HttpResponse.json(categories)),
    http.get(apiUrl('/api/products'), ({ request }) => {
      const params = new URL(request.url).searchParams;
      requests.push(params);
      return HttpResponse.json(result(products, Number(params.get('page') ?? 0), totalPages));
    }),
  );
  return requests;
}

describe('CatalogPage', () => {
  it('shows product tiles with image, name, price and availability label', async () => {
    serveCatalog([mug, lamp, speaker]);

    renderWithProviders('/');

    const list = await screen.findByRole('list', { name: 'Produkty' });
    const tiles = within(list).getAllByRole('listitem');
    expect(tiles).toHaveLength(3);

    const mugTile = within(tiles[0]);
    expect(mugTile.getByRole('img', { name: 'Kubek ceramiczny Łódź' })).toHaveAttribute(
      'loading',
      'lazy',
    );
    expect(mugTile.getByRole('link', { name: /Kubek ceramiczny Łódź/ })).toHaveAttribute(
      'href',
      '/product/1',
    );
    expect(mugTile.getByText(price(5900))).toBeInTheDocument();
    expect(mugTile.getByText('Dostępny')).toBeInTheDocument();

    expect(within(tiles[1]).getByText('Ostatnie sztuki')).toBeInTheDocument();
    expect(within(tiles[2]).getByText('Niedostępny')).toBeInTheDocument();
  });

  it('passes the URL filters to the API with a 0-based page', async () => {
    const requests = serveCatalog([mug], 3);

    renderWithProviders('/?q=lodz&category=kitchen&minPrice=5000&sort=price_desc&page=2');

    await screen.findByText('Kubek ceramiczny Łódź');
    const last = requests.at(-1)!;
    expect(Object.fromEntries(last)).toEqual({
      q: 'lodz',
      category: 'kitchen',
      minPrice: '5000',
      sort: 'price_desc',
      page: '1',
      size: '24',
    });
  });

  it('navigates between categories and marks the selected one', async () => {
    const requests = serveCatalog([mug]);
    const user = userEvent.setup();
    const { router } = renderWithProviders('/');

    const nav = await screen.findByRole('navigation', { name: 'Kategorie' });
    await user.click(await within(nav).findByRole('link', { name: 'Kuchnia' }));

    await waitFor(() => expect(router.state.location.search).toBe('?category=kitchen'));
    await waitFor(() => expect(requests.at(-1)!.get('category')).toBe('kitchen'));
    expect(within(nav).getByRole('link', { name: 'Kuchnia' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav).getByRole('link', { name: 'Wszystkie' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('changes the sort order and resets the page', async () => {
    serveCatalog([mug, lamp], 2);
    const user = userEvent.setup();
    const { router } = renderWithProviders('/?page=2');

    await screen.findByText('Kubek ceramiczny Łódź');
    await user.selectOptions(screen.getByLabelText('Sortuj'), 'price_desc');

    await waitFor(() => expect(router.state.location.search).toBe('?sort=price_desc'));
  });

  it('applies a price range entered in PLN', async () => {
    serveCatalog([mug]);
    const user = userEvent.setup();
    const { router } = renderWithProviders('/');

    await screen.findByText('Kubek ceramiczny Łódź');
    await user.type(screen.getByLabelText('Cena od (zł)'), '50');
    await user.type(screen.getByLabelText('Cena do (zł)'), '200');
    await user.click(screen.getByRole('button', { name: 'Zastosuj' }));

    await waitFor(() => expect(router.state.location.search).toBe('?minPrice=5000&maxPrice=20000'));
  });

  it('keeps the filters when moving to the next page', async () => {
    serveCatalog([mug], 3);
    const user = userEvent.setup();
    const { router } = renderWithProviders('/?category=kitchen');

    await screen.findByText('Kubek ceramiczny Łódź');
    await user.click(screen.getByRole('button', { name: 'Następna' }));

    await waitFor(() => expect(router.state.location.search).toBe('?category=kitchen&page=2'));
  });

  it('shows "No results" with a button clearing the filters', async () => {
    serveCatalog([]);
    const user = userEvent.setup();
    const { router } = renderWithProviders('/?q=%25_%5Bzzz');

    expect(await screen.findByRole('heading', { name: 'Brak wyników' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Wyczyść filtry' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(router.state.location.search).toBe('');
  });

  it('shows an error with a retry button when the list cannot be loaded', async () => {
    server.use(
      http.get(apiUrl('/api/products'), () =>
        HttpResponse.json({ title: 'Error', status: 500, code: 'INTERNAL_ERROR' }, { status: 500 }),
      ),
    );

    renderWithProviders('/');

    expect(
      await screen.findByText('Nie udało się wczytać danych. Spróbuj ponownie.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Spróbuj ponownie' })).toBeInTheDocument();
  });
});
