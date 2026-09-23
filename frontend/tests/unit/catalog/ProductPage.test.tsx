import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { formatPln } from '../../../src/shared/formatPln';
import { apiUrl, problem } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

// Testing Library normalizes the non-breaking spaces of Intl output in the DOM, not in the matcher
const price = (minor: number) => formatPln(minor).replace(/\s/gu, ' ');

const lamp: Schemas['ProductDetails'] = {
  id: 2,
  name: 'Lampka biurkowa LED Duo',
  description: 'Lampka z regulacją jasności.',
  priceMinor: 12900,
  status: 'LOW_STOCK',
  images: [
    { url: '/images/office.svg', alt: 'Lampka biurkowa LED Duo' },
    { url: '/images/detail.svg', alt: 'Lampka biurkowa LED Duo - zbliżenie' },
  ],
  category: { slug: 'office', name: 'Biuro' },
  maxAddable: 2,
};

describe('ProductPage', () => {
  it('shows name, price, availability, description and the image gallery', async () => {
    server.use(http.get(apiUrl('/api/products/2'), () => HttpResponse.json(lamp)));
    const user = userEvent.setup();

    renderWithProviders('/product/2');

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Lampka biurkowa LED Duo' }),
    ).toBeInTheDocument();
    expect(screen.getByText(price(12900))).toBeInTheDocument();
    expect(screen.getByText('Ostatnie sztuki')).toBeInTheDocument();
    expect(screen.getByText('Lampka z regulacją jasności.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Biuro' })).toHaveAttribute(
      'href',
      '/?category=office',
    );
    expect(screen.getByRole('img', { name: 'Lampka biurkowa LED Duo' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Pokaż zdjęcie 2' }));
    expect(
      screen.getByRole('img', { name: 'Lampka biurkowa LED Duo - zbliżenie' }),
    ).toBeInTheDocument();
  });

  it('shows "Product not found" for a 404', async () => {
    server.use(
      http.get(apiUrl('/api/products/999'), () =>
        problem(404, { type: 'about:blank', code: 'NOT_FOUND' }),
      ),
    );

    renderWithProviders('/product/999');

    expect(
      await screen.findByRole('heading', { name: 'Nie znaleziono produktu' }),
    ).toBeInTheDocument();
  });

  it('treats a non-numeric id as not found without calling the API', () => {
    renderWithProviders('/product/abc');

    expect(screen.getByRole('heading', { name: 'Nie znaleziono produktu' })).toBeInTheDocument();
  });
});

describe('header search box', () => {
  it('opens the product list for the phrase from page 1', async () => {
    const user = userEvent.setup();
    const { router } = renderWithProviders('/?category=kitchen&page=3');

    await user.type(screen.getByRole('searchbox', { name: 'Szukaj produktów' }), 'LODZ{Enter}');

    await waitFor(() => expect(router.state.location.search).toBe('?q=LODZ'));
    expect(router.state.location.pathname).toBe('/');
  });
});
