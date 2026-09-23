import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      include: ['tests/unit/**/*.test.{ts,tsx}'],
      setupFiles: ['tests/unit/setup.ts'],
      css: { modules: { classNameStrategy: 'non-scoped' } },
    },
  }),
);
