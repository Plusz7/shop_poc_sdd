import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

/**
 * US4 - placing an order and paying online (quickstart 4.1-4.11) on the seed catalog (product 1 "Kubek
 * ceramiczny Łódź" 59,00 zł, stock 25). The payment cases need Stripe test mode: a real `sk_test_…` key and
 * `stripe listen` forwarding webhooks to the backend (the CI E2E job provides both). With the dummy keys of
 * a local US1-US3 setup they are skipped; the form and summary cases run everywhere, because they end before
 * the backend calls Stripe. Forged/duplicate webhooks, amount mismatches and Stripe outages are covered by
 * WebhookHandlingServiceIT and PlaceOrderServiceIT.
 */

const stripeKey = process.env.STRIPE_SECRET_KEY ?? '';
const stripeEnabled = stripeKey.startsWith('sk_test_') && stripeKey !== 'sk_test_dummy';
const requiresStripe = () =>
  test.skip(!stripeEnabled, 'needs a Stripe test key and `stripe listen` (quickstart 4)');

const MUG = 1;

const cartCounter = (page: Page, count: number) =>
  page.getByRole('banner').getByRole('link', { name: `Koszyk, liczba produktów: ${count}` });
const submit = (page: Page) => page.getByRole('button', { name: 'Zamawiam i płacę' });

async function addMugsAndOpenCheckout(page: Page, quantity: number) {
  await page.goto(`/product/${MUG}`);
  await page.getByRole('combobox', { name: 'Liczba sztuk' }).selectOption(String(quantity));
  await page.getByRole('button', { name: 'Dodaj do koszyka' }).click();
  await expect(cartCounter(page, quantity)).toBeVisible();
  await page.goto('/cart');
  await page.getByRole('main').getByRole('link', { name: 'Przejdź do kasy' }).click();
  await expect(page).toHaveURL(/\/checkout$/);
}

async function fillDetails(page: Page) {
  await page.getByLabel('Adres e-mail').fill('jan.kowalski@example.com');
  await page.getByLabel('Imię i nazwisko').fill('Jan Kowalski');
  await page.getByLabel('Ulica i numer').fill('ul. Długa 5');
  await page.getByLabel('Kod pocztowy').fill('80-001');
  await page.getByLabel('Miejscowość').fill('Gdańsk');
}

/** Submits the form and returns the order number once the browser is on the Stripe payment page. */
async function goToStripe(page: Page): Promise<string> {
  const placed = page.waitForResponse(
    (response) => response.url().endsWith('/api/orders') && response.request().method() === 'POST',
  );
  await submit(page).click();
  const response = await placed;
  expect(response.status()).toBe(201);
  const { number } = (await response.json()) as { number: string };
  await page.waitForURL(/checkout\.stripe\.com/, { timeout: 30_000 });
  return number;
}

async function payWithCard(page: Page, cardNumber: string) {
  await page.locator('#cardNumber').fill(cardNumber);
  await page.locator('#cardExpiry').fill('12 / 34');
  await page.locator('#cardCvc').fill('123');
  await page.locator('#billingName').fill('Jan Kowalski');
  await page.locator('[data-testid="hosted-payment-submit-button"]').click();
}

test('4.2 the form blocks an invalid postal code and an empty email', async ({ page }) => {
  await addMugsAndOpenCheckout(page, 1);
  await fillDetails(page);
  await page.getByLabel('Adres e-mail').fill('');
  await page.getByLabel('Kod pocztowy').fill('12345');

  await submit(page).click();

  await expect(page.getByText('Podaj poprawny adres e-mail.')).toBeVisible();
  await expect(page.getByText('Kod pocztowy musi mieć format NN-NNN.')).toBeVisible();
  await expect(page.getByLabel('Kod pocztowy')).toHaveAttribute('aria-invalid', 'true');
  await expect(page).toHaveURL(/\/checkout$/);
});

test('4.7 a price change before "Pay" requires confirming the new summary', async ({
  page,
  catalogDb,
}) => {
  await addMugsAndOpenCheckout(page, 2);
  await expect(page.getByTestId('checkout-total')).toHaveText(/118,00\s*zł/);
  await fillDetails(page);

  await catalogDb.setPrice(MUG, 6_900);
  await submit(page).click();

  await expect(page.getByText(/Ceny lub dostępność produktów zmieniły się/)).toBeVisible();
  await expect(page.getByTestId('checkout-total')).toHaveText(/138,00\s*zł/);
  await expect(page).toHaveURL(/\/checkout$/);

  if (stripeEnabled) {
    await goToStripe(page);
  }
});

test('4.3-4.4 card 4242 pays the order within 30 s and empties the cart', async ({ page }) => {
  requiresStripe();
  await addMugsAndOpenCheckout(page, 2);
  await fillDetails(page);
  const number = await goToStripe(page);

  await payWithCard(page, '4242 4242 4242 4242');

  await page.waitForURL(new RegExp(`/orders/${number}`), { timeout: 30_000 });
  await expect(page.getByText('Dziękujemy! Zamówienie opłacone')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(`Numer zamówienia: ${number}`)).toBeVisible();
  await expect(cartCounter(page, 0)).toBeVisible();
});

test('4.5 a declined card and returning to the shop leave the cart untouched', async ({ page }) => {
  requiresStripe();
  await addMugsAndOpenCheckout(page, 2);
  await fillDetails(page);
  await goToStripe(page);

  await payWithCard(page, '4000 0000 0000 0002');
  await expect(page.getByText(/declined|odrzucona/i)).toBeVisible({ timeout: 30_000 });
  await page.goto('/cart?payment=canceled');

  await expect(page.getByText('Płatność nie została dokończona. Twój koszyk czeka.')).toBeVisible();
  await expect(cartCounter(page, 2)).toBeVisible();
});

test('4.6 visiting the success page without a webhook shows "payment being verified"', async ({
  page,
}) => {
  requiresStripe();
  await addMugsAndOpenCheckout(page, 1);
  await fillDetails(page);
  const number = await goToStripe(page);

  await page.goto(`/orders/${number}?session_id=cs_test_forged`);

  await expect(page.getByText('Trwa weryfikacja płatności')).toBeVisible();
  await expect(cartCounter(page, 1)).toBeVisible();
});
