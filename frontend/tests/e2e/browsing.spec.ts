import type { Page } from '@playwright/test';
import { expect, test } from './fixtures';

/**
 * US1 - browsing and searching (quickstart 1.1-1.8) on the seed catalog (db/seed/R__seed_catalog.sql:
 * 12 categories, 520+ products; product 1 "Kubek ceramiczny Łódź", product 2 with stock 2).
 */

const productList = (page: Page) => page.getByRole('list', { name: 'Produkty' });
const tiles = (page: Page) => productList(page).getByRole('listitem');
const tileNames = (page: Page) =>
  productList(page).getByRole('heading', { level: 2 }).allInnerTexts();

/** "1 234,56 zł" → 123456 grosze */
async function tilePricesMinor(page: Page): Promise<number[]> {
  const texts = await productList(page).locator('article span:first-child').allInnerTexts();
  return texts.map((text) => Number(text.replace(/[^\d]/g, '')));
}

test('1.1 the home page lists 24 products and the categories', async ({ page }) => {
  await page.goto('/');

  await expect(tiles(page)).toHaveCount(24);
  const firstTile = tiles(page).first();
  await expect(firstTile.getByRole('img')).toBeVisible();
  await expect(firstTile.getByText(/zł/)).toBeVisible();
  await expect(firstTile.getByText(/^(Dostępny|Ostatnie sztuki|Niedostępny)$/)).toBeVisible();
  const categories = page.getByRole('navigation', { name: 'Kategorie' }).getByRole('link');
  await expect(categories).toHaveCount(13);
});

test('1.2 selecting a category filters the list and updates the URL', async ({ page }) => {
  await page.goto('/');

  await page
    .getByRole('navigation', { name: 'Kategorie' })
    .getByRole('link', { name: 'Kuchnia' })
    .click();

  await expect(page).toHaveURL(/\?category=kitchen$/);
  await expect(tiles(page).getByRole('heading', { name: 'Kubek ceramiczny Łódź' })).toBeVisible();
  for (const name of await tileNames(page)) {
    expect(name).toMatch(/^(Kubek ceramiczny Łódź|Patelnia )/);
  }
});

test('1.3 search ignores letter case and Polish diacritics', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('searchbox', { name: 'Szukaj produktów' }).fill('LODZ');
  await page.getByRole('searchbox', { name: 'Szukaj produktów' }).press('Enter');

  await expect(page).toHaveURL(/\?q=LODZ$/);
  await expect(tiles(page).getByRole('heading', { name: 'Kubek ceramiczny Łódź' })).toBeVisible();
});

test('1.4-1.5 price range and "price descending" are in the URL and the link can be shared', async ({
  page,
  context,
}) => {
  await page.goto('/');

  await page.getByLabel('Cena od (zł)').fill('50');
  await page.getByLabel('Cena do (zł)').fill('200');
  await page.getByRole('button', { name: 'Zastosuj' }).click();
  await expect(page).toHaveURL(/minPrice=5000&maxPrice=20000/);
  await page.getByLabel('Sortuj').selectOption('price_desc');
  await expect(page).toHaveURL(/\?minPrice=5000&maxPrice=20000&sort=price_desc$/);

  await expect(tiles(page)).toHaveCount(24);
  // polled: the previous results stay visible until the new ones arrive (keepPreviousData)
  await expect
    .poll(async () => {
      const prices = await tilePricesMinor(page);
      const inRange = prices.every((price) => price >= 5000 && price <= 20000);
      const descending = prices.every((price, index) => index === 0 || prices[index - 1] >= price);
      return inRange && descending;
    })
    .toBe(true);

  const names = await tileNames(page);
  const shared = await context.newPage();
  await shared.goto(page.url());
  await expect(tiles(shared)).toHaveCount(24);
  expect(await tileNames(shared)).toEqual(names);
});

test('1.6 a phrase with LIKE wildcards shows "No results" without an error', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('searchbox', { name: 'Szukaj produktów' }).fill('%_[zzz');
  await page.getByRole('button', { name: 'Szukaj' }).click();

  await expect(page.getByRole('heading', { name: 'Brak wyników' })).toBeVisible();
  await page.getByRole('button', { name: 'Wyczyść filtry' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(tiles(page)).toHaveCount(24);
});

test('1.7 the next page keeps the filters', async ({ page }) => {
  await page.goto('/?category=kitchen&sort=price_asc');
  await expect(tiles(page)).toHaveCount(24);
  const firstPage = await tileNames(page);

  await page.getByRole('button', { name: 'Następna' }).click();

  await expect(page).toHaveURL(/\?category=kitchen&sort=price_asc&page=2$/);
  await expect(page.getByText('Strona 2 z')).toBeVisible();
  // the previous page stays visible until the next one arrives (keepPreviousData)
  await expect(productList(page).getByRole('heading', { level: 2 }).first()).not.toHaveText(
    firstPage[0],
  );
  const secondPage = await tileNames(page);
  expect(secondPage.length).toBeGreaterThan(0);
  expect(secondPage.filter((name) => firstPage.includes(name))).toEqual([]);
});

test('1.8 the page of a product with stock 2 shows "Last items"', async ({ page }) => {
  await page.goto('/product/2');

  await expect(
    page.getByRole('heading', { level: 1, name: 'Lampka biurkowa LED Duo' }),
  ).toBeVisible();
  await expect(page.getByText('Ostatnie sztuki')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Opis' })).toBeVisible();
  await expect(
    page.getByRole('region', { name: 'Zdjęcia produktu' }).getByRole('img').first(),
  ).toBeVisible();
  await expect(page.getByText(/129,00\s*zł/)).toBeVisible();
});

test('an inactive product is not found', async ({ page }) => {
  await page.goto('/product/6');

  await expect(page.getByRole('heading', { name: 'Nie znaleziono produktu' })).toBeVisible();
});
