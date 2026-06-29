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

function initialLocale(): SupportedLocale {
  if (typeof window === 'undefined') {
    return DEFAULT_LOCALE;
  }

  const storedLocale = typeof window.localStorage.getItem === 'function'
    ? window.localStorage.getItem(STORAGE_KEY)
    : null;
  return isSupportedLocale(storedLocale) ? storedLocale : DEFAULT_LOCALE;
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
    if (typeof window.localStorage.setItem === 'function') {
      window.localStorage.setItem(STORAGE_KEY, locale);
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
