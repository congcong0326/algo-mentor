import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { I18nProvider, useI18n } from './I18nProvider';

function LocaleProbe() {
  const { locale } = useI18n();
  return <span>{locale}</span>;
}

function setBrowserLocales(language: string, languages = [language]) {
  Object.defineProperty(window.navigator, 'language', { configurable: true, value: language });
  Object.defineProperty(window.navigator, 'languages', { configurable: true, value: languages });
}

describe('I18nProvider locale initialization', () => {
  beforeEach(() => {
    const values = new Map<string, string>();
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => values.get(key) ?? null,
        removeItem: (key: string) => values.delete(key),
        setItem: (key: string, value: string) => values.set(key, value),
      },
    });
    window.localStorage.removeItem('algo-mentor-locale');
  });

  afterEach(() => {
    setBrowserLocales('zh-CN');
    window.localStorage.removeItem('algo-mentor-locale');
  });

  it('uses the browser language when no preference is stored', () => {
    setBrowserLocales('en-GB');

    render(<I18nProvider><LocaleProbe /></I18nProvider>);

    expect(screen.getByText('en-US')).toBeInTheDocument();
  });

  it('prefers the stored locale over the browser language', () => {
    setBrowserLocales('en-US');
    window.localStorage.setItem('algo-mentor-locale', 'zh-CN');

    render(<I18nProvider><LocaleProbe /></I18nProvider>);

    expect(screen.getByText('zh-CN')).toBeInTheDocument();
  });
});
