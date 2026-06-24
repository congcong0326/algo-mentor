import { LogIn } from 'lucide-react';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';

export interface LoginPageProps {
  authFailed?: boolean;
}

export default function LoginPage({ authFailed = false }: LoginPageProps) {
  const { resources } = useI18n();

  return (
    <main className="login-page" aria-labelledby="login-title">
      <div className="login-language">
        <LanguageSelector />
      </div>
      <section className="login-panel">
        <p className="home-kicker">{resources.app.brandKicker}</p>
        <h1 id="login-title">{resources.app.brandName}</h1>
        <p className="login-subtitle">{resources.auth.subtitle}</p>
        {authFailed && <p className="error-text">{resources.auth.failed}</p>}
        <a className="primary-button login-oauth-link" href="/oauth2/authorization/google">
          <LogIn aria-hidden="true" />
          <span>{resources.auth.googleLogin}</span>
        </a>
      </section>
    </main>
  );
}
