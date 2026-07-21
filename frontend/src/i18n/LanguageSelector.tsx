import { Check, ChevronDown, Globe2 } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { useI18n } from './I18nProvider';
import { SUPPORTED_LOCALES } from './locales';

export default function LanguageSelector() {
  const { locale, resources, setLocale } = useI18n();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const listboxId = useId();

  const localeOptions = SUPPORTED_LOCALES.map((value) => ({
    label: value === 'zh-CN' ? resources.language.zhCN : resources.language.enUS,
    value,
  }));
  const selectedIndex = SUPPORTED_LOCALES.indexOf(locale);
  const selectedLabel = localeOptions[selectedIndex]?.label ?? resources.language.zhCN;

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    function handleOutsideMouseDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleOutsideFocus(event: FocusEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    document.addEventListener('mousedown', handleOutsideMouseDown);
    document.addEventListener('focusin', handleOutsideFocus);
    return () => {
      document.removeEventListener('mousedown', handleOutsideMouseDown);
      document.removeEventListener('focusin', handleOutsideFocus);
    };
  }, [open]);

  function closeMenu({ restoreFocus = false } = {}) {
    setOpen(false);
    if (restoreFocus) {
      triggerRef.current?.focus();
    }
  }

  function focusOption(index: number) {
    window.setTimeout(() => optionRefs.current[index]?.focus(), 0);
  }

  function handleTriggerKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      focusOption(event.key === 'ArrowDown' ? selectedIndex : localeOptions.length - 1);
      return;
    }

    if (event.key === 'Escape' && open) {
      event.preventDefault();
      closeMenu();
    }
  }

  function handleOptionKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeMenu({ restoreFocus: true });
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const offset = event.key === 'ArrowDown' ? 1 : -1;
      const nextIndex = (index + offset + localeOptions.length) % localeOptions.length;
      optionRefs.current[nextIndex]?.focus();
      return;
    }

    if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      optionRefs.current[event.key === 'Home' ? 0 : localeOptions.length - 1]?.focus();
    }
  }

  function selectLocale(value: typeof SUPPORTED_LOCALES[number]) {
    setLocale(value);
    closeMenu({ restoreFocus: true });
  }

  return (
    <div className="language-selector" ref={containerRef}>
      <button
        aria-controls={listboxId}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={resources.language.label}
        className="language-selector-trigger"
        onClick={() => setOpen((current) => !current)}
        onKeyDown={handleTriggerKeyDown}
        ref={triggerRef}
        role="combobox"
        type="button"
      >
        <Globe2 aria-hidden="true" />
        <span>{selectedLabel}</span>
        <ChevronDown aria-hidden="true" className="language-selector-chevron" />
      </button>
      {open ? (
        <div aria-label={resources.language.label} className="language-selector-menu" id={listboxId} role="listbox">
          {localeOptions.map((option, index) => (
            <button
              aria-selected={option.value === locale}
              className="language-selector-option"
              key={option.value}
              onClick={() => selectLocale(option.value)}
              onKeyDown={(event) => handleOptionKeyDown(event, index)}
              ref={(element) => { optionRefs.current[index] = element; }}
              role="option"
              tabIndex={-1}
              type="button"
            >
              <span>{option.label}</span>
              <Check aria-hidden="true" />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
