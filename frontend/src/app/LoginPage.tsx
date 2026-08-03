import { GitFork, LogIn, Moon, Sun, UserPlus } from 'lucide-react';
import { FormEvent, MouseEvent, useEffect, useState } from 'react';
import LanguageSelector from '../i18n/LanguageSelector';
import { useI18n } from '../i18n/I18nProvider';
import HeaderActionTooltip from './HeaderActionTooltip';
import { APP_ROUTES } from './navigation';
import type { AppTheme } from './theme';
import type { OAuthProvider, PasswordLoginRequest, PasswordRegisterRequest } from '../types/api';

export interface LoginPageProps {
  authFailed?: boolean;
  betaAccessDenied?: boolean;
  authError?: string;
  pending?: boolean;
  onLogin?: (request: PasswordLoginRequest) => Promise<void>;
  onRegister?: (request: PasswordRegisterRequest) => Promise<void>;
  passwordLoginEnabled?: boolean;
  passwordRegistrationEnabled?: boolean;
  oauthProviders?: OAuthProvider[];
  onToggleTheme?: () => void;
  theme?: AppTheme;
}

type PasswordMode = 'login' | 'register';

export default function LoginPage({
  authFailed = false,
  betaAccessDenied = false,
  authError = '',
  pending = false,
  onLogin,
  onRegister,
  passwordLoginEnabled = true,
  passwordRegistrationEnabled = true,
  oauthProviders = ['google', 'github'],
  onToggleTheme,
  theme = 'light',
}: LoginPageProps) {
  const { resources } = useI18n();
  const [mode, setMode] = useState<PasswordMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [validationError, setValidationError] = useState('');
  const [oauthLoginPending, setOAuthLoginPending] = useState<OAuthProvider>();
  const passwordLoginAvailable = passwordLoginEnabled;
  const passwordRegistrationAvailable = passwordRegistrationEnabled;
  const passwordAuthAvailable = passwordLoginAvailable || passwordRegistrationAvailable;
  const isRegisterMode = mode === 'register' || (!passwordLoginAvailable && passwordRegistrationAvailable);
  const ThemeIcon = theme === 'light' ? Moon : Sun;
  const themeLabel = theme === 'light' ? resources.app.switchToDarkMode : resources.app.switchToLightMode;
  const [brandLead, ...brandRestParts] = resources.app.brandName.split(' ');
  const brandRest = brandRestParts.length > 0 ? ` ${brandRestParts.join(' ')}` : '';
  const oauthLoginDisabled = pending || oauthLoginPending !== undefined;
  const authModeSwitchAvailable = passwordLoginAvailable && passwordRegistrationAvailable;
  const socialActionsAvailable = oauthProviders.length > 0 || authModeSwitchAvailable;
  const oauthOnly = !passwordAuthAvailable && oauthProviders.length > 0;

  useEffect(() => {
    if (!passwordLoginAvailable && passwordRegistrationAvailable) {
      setMode('register');
    } else if (passwordLoginAvailable && !passwordRegistrationAvailable) {
      setMode('login');
    }
  }, [passwordLoginAvailable, passwordRegistrationAvailable]);

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
      if (!passwordRegistrationAvailable) {
        return;
      }
      if (!displayName.trim()) {
        setValidationError(resources.auth.validationDisplayNameRequired);
        return;
      }
      await onRegister?.({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
      });
      return;
    }
    if (!passwordLoginAvailable) {
      return;
    }
    await onLogin?.({ email: email.trim(), password });
  }

  function handleOAuthLoginClick(provider: OAuthProvider, event: MouseEvent<HTMLAnchorElement>) {
    if (oauthLoginDisabled) {
      event.preventDefault();
      return;
    }
    setOAuthLoginPending(provider);
  }

  const errorText = validationError
    || authError
    || (betaAccessDenied ? resources.auth.betaAccessDenied : '')
    || (authFailed ? resources.auth.failed : '');

  return (
    <main className="login-page" aria-labelledby="login-title">
      {onToggleTheme && (
        <HeaderActionTooltip className="login-theme-tooltip-wrap" id="login-theme-toggle-tooltip" label={themeLabel}>
          <button
            aria-describedby="login-theme-toggle-tooltip"
            aria-label={themeLabel}
            className="icon-button login-theme-toggle"
            onClick={onToggleTheme}
            type="button"
          >
            <ThemeIcon aria-hidden="true" />
          </button>
        </HeaderActionTooltip>
      )}
      <section
        className={`login-panel${oauthOnly ? ' login-panel--oauth-only' : ''}`}
        aria-label={oauthOnly ? resources.auth.oauthModeTitle : resources.auth.loginModeTitle}
      >
        <div className="login-brand-lockup" aria-label={resources.app.brandName}>
          <h1 id="login-title">
            <span>{brandLead}</span>{brandRest}
          </h1>
          <p>{resources.auth.subtitle}</p>
        </div>
        {errorText && <p className="error-text" role="alert">{errorText}</p>}

        {passwordAuthAvailable ? (
          <>
            <form className="password-auth-form" onSubmit={(event) => void handleSubmit(event)}>
              <div className="password-auth-heading">
                <h2>{isRegisterMode ? resources.auth.registerModeTitle : resources.auth.loginModeTitle}</h2>
                <p>{resources.auth.emailAuthDivider}</p>
              </div>
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

            {socialActionsAvailable ? (
              <div className="login-auth-divider">
                <span>{resources.auth.socialAuthDivider}</span>
              </div>
            ) : null}
          </>
        ) : null}
        {oauthOnly ? (
          <div className="oauth-auth-section">
            <div className="oauth-auth-heading">
              <h2>{resources.auth.oauthModeTitle}</h2>
              <p>{resources.auth.oauthModeDescription}</p>
            </div>
            <div className="login-social-grid login-social-grid--oauth-only">
              <OAuthProviderLinks
                disabled={oauthLoginDisabled}
                onLogin={handleOAuthLoginClick}
                providers={oauthProviders}
              />
            </div>
          </div>
        ) : socialActionsAvailable ? (
          <div className="login-social-grid">
            <OAuthProviderLinks
              disabled={oauthLoginDisabled}
              onLogin={handleOAuthLoginClick}
              providers={oauthProviders}
            />
            {authModeSwitchAvailable ? (
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
            ) : null}
          </div>
        ) : null}

        <div className="login-support">
          <p>{resources.auth.supportContact}</p>
          <p>
            {resources.auth.termsPrefix}
            <a href={APP_ROUTES.terms}>{resources.auth.termsLabel}</a>
            {resources.auth.termsConnector}
            <a href={APP_ROUTES.privacy}>{resources.auth.privacyLabel}</a>
          </p>
        </div>

        <div className="login-language">
          <LanguageSelector />
        </div>
      </section>
    </main>
  );
}

interface OAuthProviderLinksProps {
  disabled: boolean;
  onLogin: (provider: OAuthProvider, event: MouseEvent<HTMLAnchorElement>) => void;
  providers: OAuthProvider[];
}

function OAuthProviderLinks({ disabled, onLogin, providers }: OAuthProviderLinksProps) {
  const { resources } = useI18n();

  return (
    <>
      {providers.includes('google') ? (
        <a
          aria-disabled={disabled}
          className="login-social-button"
          href="/oauth2/authorization/google"
          onClick={(event) => onLogin('google', event)}
        >
          <span className="login-google-mark" aria-hidden="true">G</span>
          <span>{resources.auth.googleLogin}</span>
        </a>
      ) : null}
      {providers.includes('github') ? (
        <a
          aria-disabled={disabled}
          className="login-social-button"
          href="/oauth2/authorization/github"
          onClick={(event) => onLogin('github', event)}
        >
          <GitFork aria-hidden="true" />
          <span>{resources.auth.githubLogin}</span>
        </a>
      ) : null}
    </>
  );
}
