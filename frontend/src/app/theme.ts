export const THEME_STORAGE_KEY = 'algo-mentor-theme';

export const APP_THEMES = ['light', 'dark'] as const;

export type AppTheme = typeof APP_THEMES[number];

export function isAppTheme(value: unknown): value is AppTheme {
  return value === 'light' || value === 'dark';
}

function getBrowserStorage(): Storage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.localStorage;
  } catch {
    return undefined;
  }
}

export function readStoredTheme(storage?: Storage): AppTheme {
  try {
    const storedTheme = (storage ?? getBrowserStorage())?.getItem(THEME_STORAGE_KEY);
    return isAppTheme(storedTheme) ? storedTheme : 'light';
  } catch {
    return 'light';
  }
}

export function storeTheme(theme: AppTheme, storage?: Storage) {
  try {
    (storage ?? getBrowserStorage())?.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Theme persistence should never block the app shell.
  }
}

export function applyTheme(theme: AppTheme, root: HTMLElement = document.documentElement) {
  root.dataset.theme = theme;
}

export function nextTheme(theme: AppTheme): AppTheme {
  return theme === 'light' ? 'dark' : 'light';
}
