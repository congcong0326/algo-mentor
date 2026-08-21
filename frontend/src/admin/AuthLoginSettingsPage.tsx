import { RefreshCw, Save } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  ApiRequestError,
  getAdminAuthLoginSettings,
  requireApiData,
  updateAdminAuthLoginSettings,
} from '../services/api';
import type { AuthLoginSettings } from '../types/api';
import { useI18n } from '../i18n/I18nProvider';

interface AuthLoginSettingsPageProps {
  onNavigateHome: () => void;
}

type LoginSettingKey = Exclude<keyof AuthLoginSettings, 'id' | 'updatedBy' | 'updatedByDisplayName' | 'updatedAt'>;

const settingKeys: LoginSettingKey[] = [
  'accountRegistrationEnabled',
  'passwordLoginEnabled',
  'passwordRegistrationEnabled',
  'googleLoginEnabled',
  'githubLoginEnabled',
];

export default function AuthLoginSettingsPage({ onNavigateHome }: AuthLoginSettingsPageProps) {
  const { resources } = useI18n();
  const t = resources.authLoginSettings;
  const [settings, setSettings] = useState<AuthLoginSettings>();
  const [draft, setDraft] = useState<AuthLoginSettings>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [forbidden, setForbidden] = useState(false);

  const hasChanges = Boolean(settings && draft && settingKeys.some((key) => settings[key] !== draft[key]));

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, []);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    setForbidden(false);
    try {
      const next = requireApiData(await getAdminAuthLoginSettings(signal), t.loadFailed);
      if (!signal?.aborted) {
        setSettings(next);
        setDraft(next);
      }
    } catch (caught) {
      if (signal?.aborted) {
        return;
      }
      if (caught instanceof ApiRequestError && (caught.status === 401 || caught.status === 403)) {
        setForbidden(true);
        setError(t.forbidden);
      } else {
        setError(caught instanceof Error ? caught.message : t.loadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  function updateDraft(key: LoginSettingKey, value: boolean) {
    setDraft((current) => current ? { ...current, [key]: value } : current);
  }

  async function save() {
    if (!draft || !hasChanges || saving) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      const updated = requireApiData(await updateAdminAuthLoginSettings({
        accountRegistrationEnabled: draft.accountRegistrationEnabled,
        passwordLoginEnabled: draft.passwordLoginEnabled,
        passwordRegistrationEnabled: draft.passwordRegistrationEnabled,
        googleLoginEnabled: draft.googleLoginEnabled,
        githubLoginEnabled: draft.githubLoginEnabled,
      }), t.saveFailed);
      setSettings(updated);
      setDraft(updated);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.saveFailed);
    } finally {
      setSaving(false);
    }
  }

  if (forbidden) {
    return (
      <section className="auth-login-settings-page" aria-label={t.ariaLabel}>
        <div className="admin-users-forbidden" role="alert">
          <h1>{t.title}</h1>
          <p>{t.forbidden}</p>
          <button className="primary-button" onClick={onNavigateHome} type="button">{t.backHome}</button>
        </div>
      </section>
    );
  }

  return (
    <section className="auth-login-settings-page" aria-label={t.ariaLabel}>
      <header className="auth-login-settings-header">
        <div>
          <h1>{t.title}</h1>
          <p>{t.subtitle}</p>
          {error ? <p className="error-text" role="alert">{error}</p> : null}
        </div>
        <div className="auth-login-settings-actions">
          <button
            aria-label={t.refresh}
            className="icon-button"
            disabled={loading || saving}
            onClick={() => void load()}
            title={t.refresh}
            type="button"
          >
            <RefreshCw aria-hidden="true" />
          </button>
          <button className="primary-button" disabled={!hasChanges || loading || saving} onClick={() => void save()} type="button">
            <Save aria-hidden="true" />
            <span>{saving ? t.saving : t.save}</span>
          </button>
        </div>
      </header>

      <div className="auth-login-settings-list" aria-busy={loading}>
        {settingKeys.map((key) => (
          <LoginSettingRow
            checked={draft?.[key] ?? false}
            disabled={!draft || loading || saving}
            key={key}
            label={t.settings[key].label}
            onChange={(value) => updateDraft(key, value)}
            summary={t.settings[key].summary}
          />
        ))}
      </div>

      {settings ? <p className="auth-login-settings-meta">{t.updatedAt(formatDateTime(settings.updatedAt))}</p> : null}
    </section>
  );
}

function LoginSettingRow({
  checked,
  disabled,
  label,
  onChange,
  summary,
}: {
  checked: boolean;
  disabled: boolean;
  label: string;
  onChange: (value: boolean) => void;
  summary: string;
}) {
  return (
    <label className="auth-login-setting-row">
      <span>
        <strong>{label}</strong>
        <small>{summary}</small>
      </span>
      <span className="auth-login-settings-switch">
        <input
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
          role="switch"
          type="checkbox"
        />
        <span aria-hidden="true" />
        <span className="visually-hidden">{label}</span>
      </span>
    </label>
  );
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
