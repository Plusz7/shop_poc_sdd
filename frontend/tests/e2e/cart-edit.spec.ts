import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

/**
 * US3 - editing the cart (quickstart 3.1-3.7) on the seed catalog (db/seed/R__seed_catalog.sql:
 * product 1 "Kubek ceramiczny Łódź" 59,00 zł with stock 25, product 3 "Plecak miejski Tatra" 159,00 zł
 * with stock 3). Price and stock changes go straight to the database through `catalogDb`, which restores
 * them after the test. Quickstart 3.8 (invalid quantities -> 400) is covered by CartEditingIT.
 */

const mugName = 'Kubek ceramiczny Łódź';
const backpackName = 'Plecak miejski Tatra';

const cartCounter = (page: Page, count: number) =>
  page.getByRole('banner').getByRole('link', { name: `Koszyk, liczba produktów: ${count}` });
const cartLines = (page: Page) =>
  page.getByRole('list', { name: 'Produkty w koszyku' }).getByRole('listitem');
const cartLine = (page: Page, name: string) => cartLines(page).filter({ hasText: name });
const quantityField = (page: Page, name: string) =>
  page.getByRole('textbox', { name: `Liczba sztuk: ${name}` });
const checkout = (page: Page) => page.getByRole('main').getByText('Przejdź do kasy');

async function add(page: Page, productId: number, quantity: number) {
  await page.goto(`/product/${productId}`);
  await page.getByRole('combobox', { name: 'Liczba sztuk' }).selectOption(String(quantity));
  await page.getByRole('button', { name: 'Dodaj do koszyka' }).click();
  await expect(page.getByText('Dodano do koszyka')).toBeVisible();
}

async function setQuantity(page: Page, name: string, quantity: string) {
  await quantityField(page, name).fill(quantity);
  await quantityField(page, name).press('Enter');
}

/** A cart with the mug (1 item) and the backpack (1 item): 59,00 + 159,00 = 218,00 zł. */
async function openCartWithTwoProducts(page: Page) {
  await add(page, 1, 1);
  await add(page, 3, 1);
  await page.goto('/cart');
  await expect(cartLines(page)).toHaveCount(2);
}

test('3.1 the cart shows unit prices, quantities, line totals and the total', async ({ page }) => {
  await openCartWithTwoProducts(page);

  await expect(cartLine(page, mugName)).toContainText('Cena: 59,00 zł');
  await expect(quantityField(page, mugName)).toHaveValue('1');
  await expect(cartLine(page, backpackName)).toContainText('159,00 zł');
  await expect(page.getByText('Razem')).toBeVisible();
  await expect(page.getByRole('main')).toContainText('218,00 zł');
  await expect(page.getByRole('link', { name: 'Przejdź do kasy' })).toBeVisible();
});

test('3.2 changing the quantity recalculates the line total and the total', async ({ page }) => {
  await openCartWithTwoProducts(page);

  await setQuantity(page, mugName, '3');

  await expect(cartLine(page, mugName)).toContainText('177,00 zł');
  await expect(page.getByRole('main')).toContainText('336,00 zł');
  await expect(cartCounter(page, 4)).toBeVisible();
});

test('3.3 a quantity above the stock is capped with a message', async ({ page, catalogDb }) => {
  await catalogDb.setStock(3, 5);
  await openCartWithTwoProducts(page);

  await setQuantity(page, backpackName, '50');

  await expect(page.getByText('Dostępnych jest tylko 5 szt.')).toBeVisible();
  await expect(quantityField(page, backpackName)).toHaveValue('5');
  await expect(cartCounter(page, 6)).toBeVisible();
});

test('3.4 quantity 0 and "Remove" delete lines', async ({ page }) => {
  await openCartWithTwoProducts(page);

  await setQuantity(page, mugName, '0');
  await expect(cartLines(page)).toHaveCount(1);
  await expect(page.getByRole('main')).toContainText('159,00 zł');

  await page.getByRole('button', { name: `Usuń: ${backpackName}` }).click();
  await expect(page.getByText('Twój koszyk jest pusty')).toBeVisible();
  await expect(cartCounter(page, 0)).toBeVisible();
});

test('3.5 clearing the cart shows the empty state without checkout', async ({ page }) => {
  await openCartWithTwoProducts(page);

  await page.getByRole('button', { name: 'Wyczyść koszyk' }).click();

  await expect(page.getByText('Twój koszyk jest pusty')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Przejdź do sklepu' })).toBeVisible();
  await expect(checkout(page)).toHaveCount(0);
  await expect(cartCounter(page, 0)).toBeVisible();
});

test('3.6 a price change is shown until the customer accepts it', async ({ page, catalogDb }) => {
  await openCartWithTwoProducts(page);

  await catalogDb.setPrice(1, 6490);
  await page.reload();

  await expect(cartLine(page, mugName)).toContainText('Cena: 64,90 zł');
  await expect(cartLine(page, mugName).locator('del')).toContainText('59,00 zł');
  await expect(
    page.getByText('Ceny niektórych produktów zmieniły się od dodania ich do koszyka.'),
  ).toBeVisible();

  await page.getByRole('button', { name: 'Rozumiem' }).click();
  await expect(
    page.getByText('Ceny niektórych produktów zmieniły się od dodania ich do koszyka.'),
  ).toBeHidden();
  await page.reload();
  await expect(cartLine(page, mugName).locator('del')).toHaveCount(0);
});

test('3.7 a sold-out product is unavailable and blocks the checkout', async ({
  page,
  catalogDb,
}) => {
  await openCartWithTwoProducts(page);

  await catalogDb.setStock(3, 0);
  await page.reload();

  await expect(cartLine(page, backpackName)).toContainText('Niedostępny');
  await expect(quantityField(page, backpackName)).toHaveCount(0);
  await expect(page.getByRole('main')).toContainText('59,00 zł');
  await expect(page.getByRole('button', { name: 'Przejdź do kasy' })).toBeDisabled();

  await page.getByRole('button', { name: `Usuń: ${backpackName}` }).click();
  await expect(page.getByRole('link', { name: 'Przejdź do kasy' })).toBeVisible();
});
