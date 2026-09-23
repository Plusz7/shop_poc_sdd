import pl from './pl.json';

export type MessageKey = keyof typeof pl;

/**
 * Returns the Polish UI copy for a key; `{name}` placeholders are replaced with `params`.
 * Components never hard-code UI text - it lives only in pl.json.
 */
export function t(key: MessageKey, params?: Record<string, string | number>): string {
  const template: string = pl[key];
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
    name in params ? String(params[name]) : placeholder,
  );
}
