import { KeyRound, LogOut } from 'lucide-react';
import { FormEvent, useState } from 'react';
import { completePasswordReset } from '../services/api';
import type { CurrentUser } from '../types/api';
import { useI18n } from '../i18n/I18nProvider';
import LanguageSelector from '../i18n/LanguageSelector';

interface PasswordChangeRequiredPageProps {
  logoutPending?: boolean;
  onCompleted: (user: CurrentUser) => void;
  onLogout: () => void;
}

export default function PasswordChangeRequiredPage({
  logoutPending = false,
  onCompleted,
  onLogout,
}: PasswordChangeRequiredPageProps) {
  const { resources } = useI18n();
  const t = resources.passwordChange;
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) {
      return;
    }
    setError('');
    if (newPassword.length < 8) {
      setError(t.passwordTooShort);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t.passwordMismatch);
      return;
    }
    setPending(true);
    try {
      const user = await completePasswordReset({ newPassword, confirmPassword });
      setNewPassword('');
      setConfirmPassword('');
      onCompleted(user);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.failed);
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="password-change-page" aria-label={t.ariaLabel}>
      <section className="password-change-panel">
        <div className="login-brand-lockup">
          <KeyRound aria-hidden="true" className="password-change-icon" />
          <h1>{t.title}</h1>
          <p>{t.description}</p>
        </div>
        <form className="password-change-form" onSubmit={(event) => void handleSubmit(event)}>
          {error ? <p className="error-text" role="alert">{error}</p> : null}
          <label>
            <span>{t.newPassword}</span>
            <input
              autoComplete="new-password"
              disabled={pending}
              onChange={(event) => setNewPassword(event.target.value)}
              type="password"
              value={newPassword}
            />
          </label>
          <label>
            <span>{t.confirmPassword}</span>
            <input
              autoComplete="new-password"
              disabled={pending}
              onChange={(event) => setConfirmPassword(event.target.value)}
              type="password"
              value={confirmPassword}
            />
          </label>
          <button className="primary-button" disabled={pending} type="submit">
            {pending ? t.submitting : t.submit}
          </button>
        </form>
        <div className="password-change-footer">
          <LanguageSelector />
          <button className="secondary-button" disabled={logoutPending} onClick={onLogout} type="button">
            <LogOut aria-hidden="true" />
            <span>{t.logout}</span>
          </button>
        </div>
      </section>
    </main>
  );
}
