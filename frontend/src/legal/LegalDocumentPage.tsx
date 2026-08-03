import { ArrowLeft, Moon, Sun } from 'lucide-react';
import { useEffect } from 'react';
import HeaderActionTooltip from '../app/HeaderActionTooltip';
import { APP_ROUTES } from '../app/navigation';
import type { AppTheme } from '../app/theme';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';
import { legalDocuments, type LegalDocumentKind } from './legalDocuments';

interface LegalDocumentPageProps {
  kind: LegalDocumentKind;
  onToggleTheme: () => void;
  theme: AppTheme;
}

export default function LegalDocumentPage({ kind, onToggleTheme, theme }: LegalDocumentPageProps) {
  const { locale, resources } = useI18n();
  const content = legalDocuments[locale][kind];
  const relatedPath = kind === 'terms' ? APP_ROUTES.privacy : APP_ROUTES.terms;
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;

  useEffect(() => {
    const previousTitle = document.title;
    document.title = `${content.title} | ${resources.app.brandName}`;
    return () => {
      document.title = previousTitle;
    };
  }, [content.title, resources.app.brandName]);

  return (
    <main className="app-shell legal-page">
      <header className="app-header legal-header" role="banner">
        <a className="app-brand legal-brand" href={APP_ROUTES.home}>
          <strong>{resources.app.brandName}</strong>
        </a>
        <div className="app-header-actions">
          <HeaderActionTooltip id="legal-theme-toggle-tooltip" label={themeLabel}>
            <button
              aria-describedby="legal-theme-toggle-tooltip"
              aria-label={themeLabel}
              className="icon-button theme-toggle-button"
              onClick={onToggleTheme}
              type="button"
            >
              <ThemeIcon aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
          <LanguageSelector />
        </div>
      </header>

      <article className="legal-document" aria-labelledby="legal-document-title">
        <a className="legal-back-link" href={APP_ROUTES.home}>
          <ArrowLeft aria-hidden="true" />
          <span>{content.backLabel}</span>
        </a>
        <header className="legal-document-heading">
          <h1 id="legal-document-title">{content.title}</h1>
          <p>{content.description}</p>
          <p className="legal-effective-date">
            {content.effectiveDateLabel}: {content.effectiveDate}
          </p>
        </header>

        <div className="legal-sections">
          {content.sections.map((section) => (
            <section key={section.title}>
              <h2>{section.title}</h2>
              {section.paragraphs?.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
              {section.items ? (
                <ul>
                  {section.items.map((item) => <li key={item}>{item}</li>)}
                </ul>
              ) : null}
            </section>
          ))}
        </div>

        <footer className="legal-document-footer">
          <a href={relatedPath}>{content.relatedLabel}</a>
          <a href={APP_ROUTES.home}>{content.backLabel}</a>
        </footer>
      </article>
    </main>
  );
}
