import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

/**
 * US2 - adding to the cart (quickstart 2.1-2.5) on the seed catalog (db/seed/R__seed_catalog.sql:
 * product 1 "Kubek ceramiczny Łódź" with stock 25, product 3 with stock 3, product 5 with stock 0).
 * Every test starts with a new browser context, i.e. without the shop_guest cookie and with an empty cart.
 */

const cartCounter = (page: Page, count: number) =>
  page.getByRole('banner').getByRole('link', { name: `Koszyk, liczba produktów: ${count}` });

async function addFromProductPage(page: Page, productId: number, quantity: number) {
  await page.goto(`/product/${productId}`);
  await page.getByRole('combobox', { name: 'Liczba sztuk' }).selectOption(String(quantity));
  await page.getByRole('button', { name: 'Dodaj do koszyka' }).click();
}

test('2.1 adding 2 items on the product page confirms and increases the counter', async ({
  page,
}) => {
  await page.goto('/product/1');
  await expect(cartCounter(page, 0)).toBeVisible();

  await page.getByRole('combobox', { name: 'Liczba sztuk' }).selectOption('2');
  await page.getByRole('button', { name: 'Dodaj do koszyka' }).click();

  await expect(page.getByText('Dodano do koszyka')).toBeVisible();
  await expect(cartCounter(page, 2)).toBeVisible();
});

test('2.2 adding the same product from the list merges it into one line', async ({ page }) => {
  await addFromProductPage(page, 1, 2);
  await expect(cartCounter(page, 2)).toBeVisible();

  await page.goto('/?q=%C5%81%C3%B3d%C5%BA');
  await page.getByRole('button', { name: 'Dodaj do koszyka: Kubek ceramiczny Łódź' }).click();
  await expect(cartCounter(page, 3)).toBeVisible();

  await cartCounter(page, 3).click();
  await expect(page).toHaveURL(/\/cart$/);
  const lines = page.getByRole('list', { name: 'Produkty w koszyku' }).getByRole('listitem');
  await expect(lines).toHaveCount(1);
  await expect(lines.first()).toContainText('Kubek ceramiczny Łódź');
  await expect(lines.first()).toContainText('Liczba sztuk: 3');
});

test('2.3 adding beyond the stock is blocked with the remaining quantity', async ({ page }) => {
  await addFromProductPage(page, 3, 2);
  await expect(cartCounter(page, 2)).toBeVisible();

  await page.getByRole('combobox', { name: 'Liczba sztuk' }).selectOption('2');
  await page.getByRole('button', { name: 'Dodaj do koszyka' }).click();

  await expect(page.getByText('Możesz dodać najwyżej 1 szt.')).toBeVisible();
  await expect(cartCounter(page, 2)).toBeVisible();
});

test('2.4 an unavailable product cannot be added', async ({ page }) => {
  await page.goto('/product/5');

  await expect(page.getByText('Niedostępny', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Dodaj do koszyka' })).toHaveAttribute(
    'aria-disabled',
    'true',
  );
  await expect(page.getByText('Ten produkt jest obecnie niedostępny.')).toBeVisible();
});

test('2.5 the cart survives a refresh and a new tab', async ({ page, context }) => {
  await addFromProductPage(page, 1, 2);
  await expect(cartCounter(page, 2)).toBeVisible();

  await page.reload();
  await expect(cartCounter(page, 2)).toBeVisible();

  const secondTab = await context.newPage();
  await secondTab.goto('/cart');
  await expect(cartCounter(secondTab, 2)).toBeVisible();
  await expect(secondTab.getByText('Kubek ceramiczny Łódź')).toBeVisible();

  const guestCookie = (await context.cookies()).find((cookie) => cookie.name === 'shop_guest');
  expect(guestCookie?.httpOnly).toBe(true);
  expect(guestCookie?.expires ?? 0).toBeGreaterThan(Date.now() / 1000 + 29 * 24 * 3600);
});
