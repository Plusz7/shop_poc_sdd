import { test as base, expect } from '@playwright/test';
import sql from 'mssql';

/**
 * Direct access to the E2E database, used to change a product's price or stock between steps
 * (quickstart 3.6, 3.7, 4.7). Connection settings come from the same variables as the backend (.env).
 */
export type CatalogDb = {
  setPrice: (productId: number, priceMinor: number) => Promise<void>;
  setStock: (productId: number, stock: number) => Promise<void>;
  findProductId: (whereSql: string) => Promise<number>;
  snapshot: (productId: number) => Promise<{ priceMinor: number; stock: number }>;
};

function connectionConfig(): sql.config {
  const password = process.env.DB_PASSWORD;
  if (!password) {
    throw new Error('DB_PASSWORD is required for E2E database helpers (see .env.example)');
  }
  return {
    server: process.env.DB_HOST ?? 'localhost',
    port: Number(process.env.DB_PORT ?? 1433),
    database: process.env.DB_NAME ?? 'shop',
    user: process.env.DB_USER ?? 'sa',
    password,
    options: { encrypt: true, trustServerCertificate: true },
  };
}

type Fixtures = { catalogDb: CatalogDb };

/**
 * Every Playwright test runs in a new browser context, so it starts without the shop_guest cookie -
 * i.e. with a fresh, empty cart. `catalogDb` restores every price/stock it changed after the test.
 */
export const test = base.extend<Fixtures>({
  catalogDb: async ({}, use) => {
    const pool = await new sql.ConnectionPool(connectionConfig()).connect();
    const originals = new Map<number, { priceMinor: number; stock: number }>();

    const snapshot = async (productId: number) => {
      const result = await pool
        .request()
        .input('id', sql.BigInt, productId)
        .query('SELECT price_minor AS priceMinor, stock FROM product WHERE id = @id');
      const row = result.recordset[0];
      return { priceMinor: Number(row.priceMinor), stock: Number(row.stock) };
    };
    const remember = async (productId: number) => {
      if (!originals.has(productId)) {
        originals.set(productId, await snapshot(productId));
      }
    };

    await use({
      snapshot,
      setPrice: async (productId, priceMinor) => {
        await remember(productId);
        await pool
          .request()
          .input('id', sql.BigInt, productId)
          .input('price', sql.BigInt, priceMinor)
          .query('UPDATE product SET price_minor = @price, version = version + 1 WHERE id = @id');
      },
      setStock: async (productId, stock) => {
        await remember(productId);
        await pool
          .request()
          .input('id', sql.BigInt, productId)
          .input('stock', sql.Int, stock)
          .query('UPDATE product SET stock = @stock, version = version + 1 WHERE id = @id');
      },
      findProductId: async (whereSql) => {
        const result = await pool.request().query(`SELECT TOP 1 id FROM product WHERE ${whereSql} ORDER BY id`);
        if (result.recordset.length === 0) {
          throw new Error(`No product matches: ${whereSql}`);
        }
        return Number(result.recordset[0].id);
      },
    });

    for (const [productId, original] of originals) {
      await pool
        .request()
        .input('id', sql.BigInt, productId)
        .input('price', sql.BigInt, original.priceMinor)
        .input('stock', sql.Int, original.stock)
        .query('UPDATE product SET price_minor = @price, stock = @stock, version = version + 1 WHERE id = @id');
    }
    await pool.close();
  },
});

export { expect };
