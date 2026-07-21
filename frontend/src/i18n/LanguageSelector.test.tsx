import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { I18nProvider } from './I18nProvider';
import LanguageSelector from './LanguageSelector';

let originalLocalStorage: Storage;

beforeEach(() => {
  originalLocalStorage = window.localStorage;
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: () => null,
      setItem: () => {},
    },
  });
});

afterEach(() => {
  cleanup();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: originalLocalStorage,
  });
});

describe('LanguageSelector', () => {
  it('renders a custom listbox and closes it after selecting a language', () => {
    render(
      <I18nProvider>
        <LanguageSelector />
      </I18nProvider>,
    );

    const trigger = screen.getByRole('combobox', { name: '语言' });
    fireEvent.click(trigger);

    expect(screen.getByRole('listbox', { name: '语言' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '中文' })).toHaveAttribute('aria-selected', 'true');

    fireEvent.click(screen.getByRole('option', { name: 'English' }));

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Language' })).toHaveTextContent('English');
  });

  it('closes the menu with Escape and restores focus to the trigger', () => {
    render(
      <I18nProvider>
        <LanguageSelector />
      </I18nProvider>,
    );

    const trigger = screen.getByRole('combobox', { name: '语言' });
    fireEvent.click(trigger);
    const option = screen.getByRole('option', { name: '中文' });
    option.focus();
    fireEvent.keyDown(option, { key: 'Escape' });

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('closes the menu when focus moves outside the selector', () => {
    render(
      <I18nProvider>
        <LanguageSelector />
        <button type="button">Next control</button>
      </I18nProvider>,
    );

    fireEvent.click(screen.getByRole('combobox', { name: '语言' }));
    fireEvent.focusIn(screen.getByRole('button', { name: 'Next control' }));

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });
});
