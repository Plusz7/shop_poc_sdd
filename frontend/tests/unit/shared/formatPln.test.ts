import { describe, expect, it } from 'vitest';
import { formatPln } from '../../../src/shared/formatPln';

// Intl uses non-breaking spaces as separators; normalize them for readable assertions.
const normalize = (text: string) => text.replace(/\s/gu, ' ');

describe('formatPln', () => {
  it('formats grosze as Polish złoty', () => {
    expect(normalize(formatPln(12345))).toBe('123,45 zł');
  });

  it('keeps two decimal places', () => {
    expect(normalize(formatPln(5000))).toBe('50,00 zł');
    expect(normalize(formatPln(1))).toBe('0,01 zł');
  });

  it('groups thousands', () => {
    expect(normalize(formatPln(123456789))).toBe('1 234 567,89 zł');
  });

  it('formats zero', () => {
    expect(normalize(formatPln(0))).toBe('0,00 zł');
  });
});
