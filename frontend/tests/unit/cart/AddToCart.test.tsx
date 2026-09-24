import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { Schemas } from '../../../src/api/client';
import { apiUrl, emptyCart, problem } from '../msw/handlers';
import { server } from '../msw/server';
import { renderWithProviders } from '../renderWithProviders';

const lamp: Schemas['ProductDetails'] = {
  id: 2,
  name: 'Lampka biurkowa LED Duo',
  description: 'Lampka z regulacją jasności.',
  priceMinor: 12900,
  status: 'AVAILABLE',
  images: [{ url: '/images/office.svg', alt: 'Lampka biurkowa LED Duo' }],
  category: { slug: 'office', name: 'Biuro' },
  maxAddable: 5,
};

function cartWith(productId: number, quantity: number): Schemas['Cart'] {
  return {
    lines: [
      {
        productId,
        name: 'Lampka biurkowa LED Duo',
        imageUrl: '/images/office.svg',
        unitPriceMinor: 12900,
        quantity,
        lineTotalMinor: 12900 * quantity,
        status: 'AVAILABLE',
        maxQuantity: 5,
        priceChanged: false,
        quantityExceedsStock: false,
      },
    ],
    itemCount: quantity,
    totalMinor: 12900 * quantity,
    canPlaceOrder: false,
    messages: [],
  };
}

/** A backend double that keeps the cart between requests, like the real one does per cookie. */
function statefulCart() {
  let cart = emptyCart;
  const requests: unknown[] = [];
  server.use(
    http.get(apiUrl('/api/cart'), () => HttpResponse.json(cart)),
    http.post(apiUrl('/api/cart/lines'), async ({ request }) => {
      const body = (await request.json()) as { productId: number; quantity: number };
      requests.push(body);
      cart = cartWith(body.productId, (cart.lines[0]?.quantity ?? 0) + body.quantity);
      return HttpResponse.json(cart);
    }),
  );
  return requests;
}

describe('adding to the cart', () => {
  it('adds the chosen quantity from the product page, confirms and refreshes the counter', async () => {
    server.use(http.get(apiUrl('/api/products/2'), () => HttpResponse.json(lamp)));
    const requests = statefulCart();
    const user = userEvent.setup();

    renderWithProviders('/product/2');

    expect(
      await screen.findByRole('link', { name: 'Koszyk, liczba produktów: 0' }),
    ).toBeInTheDocument();
    const quantity = await screen.findByRole('combobox', { name: 'Liczba sztuk' });
    expect(within(quantity).getAllByRole('option')).toHaveLength(5);
    await user.selectOptions(quantity, '2');
    await user.click(screen.getByRole('button', { name: 'Dodaj do koszyka' }));

    expect(await screen.findByText('Dodano do koszyka')).toBeInTheDocument();
    expect(requests).toEqual([{ productId: 2, quantity: 2 }]);
    expect(
      await screen.findByRole('link', { name: 'Koszyk, liczba produktów: 2' }),
    ).toHaveAttribute('href', '/cart');
  });

  it('adds one item from the product list', async () => {
    server.use(
      http.get(apiUrl('/api/products'), () =>
        HttpResponse.json({
          products: [
            {
              id: 2,
              name: lamp.name,
              priceMinor: lamp.priceMinor,
              status: 'AVAILABLE',
              image: lamp.images[0],
              maxAddable: 5,
            },
          ],
          page: 0,
          size: 24,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    );
    const requests = statefulCart();
    const user = userEvent.setup();

    renderWithProviders('/');

    await user.click(
      await screen.findByRole('button', { name: 'Dodaj do koszyka: Lampka biurkowa LED Duo' }),
    );

    expect(await screen.findByText('Dodano do koszyka')).toBeInTheDocument();
    expect(requests).toEqual([{ productId: 2, quantity: 1 }]);
    expect(
      await screen.findByRole('link', { name: 'Koszyk, liczba produktów: 1' }),
    ).toBeInTheDocument();
  });

  it('shows how many items can still be added when the limit is exceeded', async () => {
    server.use(
      http.get(apiUrl('/api/products/2'), () => HttpResponse.json(lamp)),
      http.post(apiUrl('/api/cart/lines'), () =>
        problem(409, {
          type: 'about:blank',
          code: 'QUANTITY_EXCEEDS_LIMIT',
          maxQuantity: 1,
          detail: 'Możesz dodać najwyżej 1 szt.',
        }),
      ),
    );
    const user = userEvent.setup();

    renderWithProviders('/product/2');
    await user.click(await screen.findByRole('button', { name: 'Dodaj do koszyka' }));

    expect(await screen.findByText('Możesz dodać najwyżej 1 szt.')).toBeInTheDocument();
    expect(screen.queryByText('Dodano do koszyka')).not.toBeInTheDocument();
  });

  it('explains that the line is full when nothing more can be added', async () => {
    server.use(
      http.get(apiUrl('/api/products/2'), () => HttpResponse.json(lamp)),
      http.post(apiUrl('/api/cart/lines'), () =>
        problem(409, { type: 'about:blank', code: 'QUANTITY_EXCEEDS_LIMIT', maxQuantity: 0 }),
      ),
    );
    const user = userEvent.setup();

    renderWithProviders('/product/2');
    await user.click(await screen.findByRole('button', { name: 'Dodaj do koszyka' }));

    expect(
      await screen.findByText('Masz już w koszyku maksymalną dostępną liczbę sztuk tego produktu.'),
    ).toBeInTheDocument();
  });

  it('disables adding an unavailable product and explains why', async () => {
    let posted = false;
    server.use(
      http.get(apiUrl('/api/products/2'), () =>
        HttpResponse.json({ ...lamp, status: 'UNAVAILABLE', maxAddable: 0 }),
      ),
      http.post(apiUrl('/api/cart/lines'), () => {
        posted = true;
        return HttpResponse.json(emptyCart);
      }),
    );
    const user = userEvent.setup();

    renderWithProviders('/product/2');

    const button = await screen.findByRole('button', { name: 'Dodaj do koszyka' });
    expect(button).toHaveAttribute('aria-disabled', 'true');
    expect(button).toHaveAccessibleDescription('Ten produkt jest obecnie niedostępny.');
    expect(screen.queryByRole('combobox', { name: 'Liczba sztuk' })).not.toBeInTheDocument();

    await user.click(button);
    await waitFor(() => expect(posted).toBe(false));
  });
});
