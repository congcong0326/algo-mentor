import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  THEME_STORAGE_KEY,
  applyTheme,
  nextTheme,
  readStoredTheme,
  storeTheme,
} from './theme';

function createStorage(initialValue?: string): Storage {
  const values = new Map<string, string>();

  if (initialValue !== undefined) {
    values.set(THEME_STORAGE_KEY, initialValue);
  }

  return {
    get length() {
      return values.size;
    },
    clear: vi.fn(() => values.clear()),
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    key: vi.fn((index: number) => Array.from(values.keys())[index] ?? null),
    removeItem: vi.fn((key: string) => {
      values.delete(key);
    }),
    setItem: vi.fn((key: string, value: string) => {
      values.set(key, value);
    }),
  };
}

afterEach(() => {
  vi.restoreAllMocks();
  document.documentElement.removeAttribute('data-theme');
});

describe('theme utilities', () => {
  it('defaults to light when no valid stored theme exists', () => {
    expect(readStoredTheme(createStorage())).toBe('light');

    const storage = createStorage('sepia');

    expect(readStoredTheme(storage)).toBe('light');
  });

  it('reads a stored dark theme', () => {
    const storage = createStorage('dark');

    expect(readStoredTheme(storage)).toBe('dark');
  });

  it('applies the theme to the document element', () => {
    applyTheme('dark');

    expect(document.documentElement.dataset.theme).toBe('dark');

    applyTheme('light');

    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('stores the theme when localStorage is available', () => {
    const storage = createStorage();

    storeTheme('dark', storage);

    expect(storage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('ignores localStorage failures', () => {
    const storage = createStorage();
    vi.mocked(storage.getItem).mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(readStoredTheme(storage)).toBe('light');

    vi.mocked(storage.setItem).mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => storeTheme('dark', storage)).not.toThrow();
  });

  it('ignores localStorage access failures', () => {
    vi.spyOn(window, 'localStorage', 'get').mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(readStoredTheme()).toBe('light');
    expect(() => storeTheme('dark')).not.toThrow();
  });

  it('returns the opposite theme', () => {
    expect(nextTheme('light')).toBe('dark');
    expect(nextTheme('dark')).toBe('light');
  });
});
