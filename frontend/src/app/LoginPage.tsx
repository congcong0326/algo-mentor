import { LogIn, Moon, Sun, UserPlus } from 'lucide-react';
import { FormEvent, useState } from 'react';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';
import type { AppTheme } from './theme';
import type { PasswordLoginRequest, PasswordRegisterRequest } from '../types/api';

export interface LoginPageProps {
  authFailed?: boolean;
  authError?: string;
  pending?: boolean;
  onLogin?: (request: PasswordLoginRequest) => Promise<void>;
  onRegister?: (request: PasswordRegisterRequest) => Promise<void>;
  onToggleTheme?: () => void;
  theme?: AppTheme;
}

type PasswordMode = 'login' | 'register';

export default function LoginPage({
  authFailed = false,
  authError = '',
  pending = false,
  onLogin,
  onRegister,
  onToggleTheme,
  theme = 'light',
}: LoginPageProps) {
  const { resources } = useI18n();
  const [mode, setMode] = useState<PasswordMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [validationError, setValidationError] = useState('');
  const isRegisterMode = mode === 'register';
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;
  const [brandLead, ...brandRestParts] = resources.app.brandName.split(' ');
  const brandRest = brandRestParts.length > 0 ? ` ${brandRestParts.join(' ')}` : '';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidationError('');
    if (!email.trim()) {
      setValidationError(resources.auth.validationEmailRequired);
      return;
    }
    if (!password) {
      setValidationError(resources.auth.validationPasswordRequired);
      return;
    }
    if (isRegisterMode) {
      await onRegister?.({
        email: email.trim(),
        password,
        displayName: displayName.trim() || undefined,
      });
      return;
    }
    await onLogin?.({ email: email.trim(), password });
  }

  const errorText = validationError || authError || (authFailed ? resources.auth.failed : '');

  return (
    <main className="login-page" aria-labelledby="login-title">
      {onToggleTheme && (
        <button
          aria-label={themeLabel}
          className="icon-button login-theme-toggle"
          onClick={onToggleTheme}
          title={themeLabel}
          type="button"
        >
          <ThemeIcon aria-hidden="true" />
        </button>
      )}
      <section className="login-panel" aria-label={resources.auth.loginModeTitle}>
        <div className="login-brand-lockup" aria-label={resources.app.brandName}>
          <span className="login-brand-mark" aria-hidden="true">
            <span>A</span>
            <span>M</span>
          </span>
          <h1 id="login-title">
            <span>{brandLead}</span>{brandRest}
          </h1>
          <p>{resources.auth.subtitle}</p>
        </div>

        <form className="password-auth-form" onSubmit={(event) => void handleSubmit(event)}>
          <div className="password-auth-heading">
            <h2>{isRegisterMode ? resources.auth.registerModeTitle : resources.auth.loginModeTitle}</h2>
            <p>{resources.auth.emailAuthDivider}</p>
          </div>
          {errorText && <p className="error-text" role="alert">{errorText}</p>}
          <label>
            <span className="visually-hidden">{resources.auth.emailLabel}</span>
            <input
              autoComplete="email"
              disabled={pending}
              onChange={(event) => setEmail(event.target.value)}
              placeholder={resources.auth.emailPlaceholder}
              type="email"
              value={email}
            />
          </label>
          <label>
            <span className="visually-hidden">{resources.auth.passwordLabel}</span>
            <input
              autoComplete={isRegisterMode ? 'new-password' : 'current-password'}
              disabled={pending}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={resources.auth.passwordPlaceholder}
              type="password"
              value={password}
            />
          </label>
          {isRegisterMode && (
            <label>
              <span className="visually-hidden">{resources.auth.displayNameLabel}</span>
              <input
                autoComplete="nickname"
                disabled={pending}
                onChange={(event) => setDisplayName(event.target.value)}
                placeholder={resources.auth.displayNamePlaceholder}
                type="text"
                value={displayName}
              />
            </label>
          )}
          <button className="password-auth-submit" disabled={pending} type="submit">
            {pending
              ? isRegisterMode ? resources.auth.registering : resources.auth.loggingIn
              : isRegisterMode ? resources.auth.passwordRegister : resources.auth.passwordLogin}
          </button>
        </form>

        <div className="login-auth-divider">
          <span>{resources.auth.socialAuthDivider}</span>
        </div>
        <div className="login-social-grid">
          <a className="login-social-button" href="/oauth2/authorization/google">
            <span className="login-google-mark" aria-hidden="true">G</span>
            <span>{resources.auth.googleLogin}</span>
          </a>
          <button
            className="login-social-button"
            disabled={pending}
            onClick={() => {
              setMode(isRegisterMode ? 'login' : 'register');
              setValidationError('');
            }}
            type="button"
          >
            {isRegisterMode ? <LogIn aria-hidden="true" /> : <UserPlus aria-hidden="true" />}
            <span>{isRegisterMode ? resources.auth.showLogin : resources.auth.showRegister}</span>
          </button>
        </div>

        <div className="login-support">
          <p>
            {resources.auth.needHelpPrefix}
            <a href={`mailto:${resources.auth.supportEmail}`}>{resources.auth.supportEmail}</a>
          </p>
          <p>
            {resources.auth.termsPrefix}
            <a href="/terms">{resources.auth.termsLabel}</a>
            {resources.auth.termsConnector}
            <a href="/privacy">{resources.auth.privacyLabel}</a>
          </p>
        </div>

        <div className="login-language">
          <LanguageSelector />
        </div>
      </section>
    </main>
  );
}
