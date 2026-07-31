import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { setApiLocale } from '../services/api';
import { localeResources, SUPPORTED_LOCALES, type LocaleResources, type SupportedLocale } from './locales';

const STORAGE_KEY = 'algo-mentor-locale';
const DEFAULT_LOCALE: SupportedLocale = 'zh-CN';

interface I18nContextValue {
  locale: SupportedLocale;
  resources: LocaleResources;
  setLocale: (locale: SupportedLocale) => void;
}

const I18nContext = createContext<I18nContextValue>({
  locale: DEFAULT_LOCALE,
  resources: localeResources[DEFAULT_LOCALE],
  setLocale: () => {},
});

function isSupportedLocale(value: string | null | undefined): value is SupportedLocale {
  return SUPPORTED_LOCALES.some((locale) => locale === value);
}

function localeFromBrowser(): SupportedLocale | undefined {
  const browserLocales = window.navigator.languages?.length
    ? window.navigator.languages
    : [window.navigator.language];
  for (const browserLocale of browserLocales) {
    if (isSupportedLocale(browserLocale)) {
      return browserLocale;
    }
    const language = browserLocale?.split('-')[0]?.toLowerCase();
    const matchingLocale = SUPPORTED_LOCALES.find((locale) => locale.toLowerCase().startsWith(`${language}-`));
    if (matchingLocale) {
      return matchingLocale;
    }
  }
  return undefined;
}

function initialLocale(): SupportedLocale {
  if (typeof window === 'undefined') {
    return DEFAULT_LOCALE;
  }

  try {
    const storedLocale = typeof window.localStorage.getItem === 'function'
      ? window.localStorage.getItem(STORAGE_KEY)
      : null;
    if (isSupportedLocale(storedLocale)) {
      return storedLocale;
    }
  } catch {
    // Browsers can block storage in privacy-restricted contexts.
  }
  return localeFromBrowser() ?? DEFAULT_LOCALE;
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<SupportedLocale>(() => {
    const nextLocale = initialLocale();
    setApiLocale(nextLocale);
    return nextLocale;
  });

  useEffect(() => {
    document.documentElement.lang = locale;
    setApiLocale(locale);
    try {
      if (typeof window.localStorage.setItem === 'function') {
        window.localStorage.setItem(STORAGE_KEY, locale);
      }
    } catch {
      // The active locale still works for this session when persistence is unavailable.
    }
  }, [locale]);

  const value = useMemo<I18nContextValue>(() => ({
    locale,
    resources: localeResources[locale],
    setLocale: setLocaleState,
  }), [locale]);

  return (
    <I18nContext.Provider value={value}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n(): I18nContextValue {
  return useContext(I18nContext);
}
