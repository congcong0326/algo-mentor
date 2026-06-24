import { Globe2 } from 'lucide-react';
import { useI18n } from './I18nProvider';
import { SUPPORTED_LOCALES } from './locales';

export default function LanguageSelector() {
  const { locale, resources, setLocale } = useI18n();

  return (
    <label className="language-selector">
      <Globe2 aria-hidden="true" />
      <span className="visually-hidden">{resources.language.label}</span>
      <select
        aria-label={resources.language.label}
        onChange={(event) => setLocale(event.target.value as typeof SUPPORTED_LOCALES[number])}
        value={locale}
      >
        <option value="zh-CN">{resources.language.zhCN}</option>
        <option value="en-US">{resources.language.enUS}</option>
      </select>
    </label>
  );
}
