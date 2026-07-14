import { Pencil, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import type { AdminAiSettings } from '../../types/api';
import { formatDateTime, isWholeLimit } from './aiFormat';

interface AiRuntimePolicyBarProps {
  error?: string;
  loading: boolean;
  onRefresh: () => void;
  onUpdate: (next: { aiEnabled: boolean; defaultDailyRequestLimit: number }) => Promise<boolean>;
  settings?: AdminAiSettings;
  updating: boolean;
}

export default function AiRuntimePolicyBar({
  error,
  loading,
  onRefresh,
  onUpdate,
  settings,
  updating,
}: AiRuntimePolicyBarProps) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const [editingLimit, setEditingLimit] = useState(false);
  const [limitInput, setLimitInput] = useState('');
  const [limitError, setLimitError] = useState('');
  const [pendingEnabled, setPendingEnabled] = useState<boolean>();

  useEffect(() => {
    setLimitInput(settings ? String(settings.defaultDailyRequestLimit) : '');
  }, [settings?.defaultDailyRequestLimit]);

  if (loading && !settings) {
    return <section className="ai-runtime-policy-bar" aria-busy="true" aria-label={t.settingsTitle} />;
  }

  if (!settings) {
    return (
      <section className="ai-runtime-policy-bar ai-runtime-policy-empty" aria-label={t.settingsTitle}>
        <span className="error-text" role="alert">{error || t.settingsLoadFailed}</span>
        <button
          aria-label={t.refresh}
          className="icon-button"
          onClick={onRefresh}
          title={t.refresh}
          type="button"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </section>
    );
  }

  const currentSettings = settings;

  async function confirmEnabledChange() {
    if (pendingEnabled === undefined || updating) {
      return;
    }
    const succeeded = await onUpdate({
      aiEnabled: pendingEnabled,
      defaultDailyRequestLimit: currentSettings.defaultDailyRequestLimit,
    });
    if (succeeded) {
      setPendingEnabled(undefined);
    }
  }

  async function saveLimit() {
    if (updating) {
      return;
    }
    if (!isWholeLimit(limitInput)) {
      setLimitError(t.limitInvalid);
      return;
    }
    setLimitError('');
    const succeeded = await onUpdate({
      aiEnabled: currentSettings.aiEnabled,
      defaultDailyRequestLimit: Number(limitInput),
    });
    if (succeeded) {
      setEditingLimit(false);
    }
  }

  function cancelLimitEdit() {
    setLimitInput(String(currentSettings.defaultDailyRequestLimit));
    setLimitError('');
    setEditingLimit(false);
  }

  const changingToEnabled = pendingEnabled === true;
  const toggleLabel = currentSettings.aiEnabled ? t.enabled : t.disabled;

  return (
    <section className="ai-runtime-policy-bar" aria-label={t.settingsTitle}>
      <div className="ai-runtime-policy-heading">
        <div>
          <p className="section-kicker">{t.settingsTitle}</p>
          <h1>{t.title}</h1>
        </div>
        <button
          aria-label={t.refresh}
          className="icon-button"
          disabled={loading || updating}
          onClick={onRefresh}
          title={t.refresh}
          type="button"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </div>

      {error ? <p className="error-text" role="alert">{error}</p> : null}

      <div className="ai-runtime-policy-grid">
        <div className="ai-runtime-policy-item">
          <span>{t.globalStatus}</span>
          <div className="ai-runtime-status-control">
            <strong className={currentSettings.aiEnabled ? 'status-positive' : 'status-negative'}>{toggleLabel}</strong>
            <button
              aria-checked={currentSettings.aiEnabled}
              aria-label={t.globalStatus}
              className="switch-control"
              disabled={updating}
              onClick={() => setPendingEnabled(!currentSettings.aiEnabled)}
              role="switch"
              type="button"
            >
              <span aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="ai-runtime-policy-item ai-runtime-limit-item">
          <span>{t.defaultDailyRequestLimit}</span>
          {editingLimit ? (
            <div className="ai-runtime-limit-editor">
              <input
                aria-label={t.defaultDailyRequestLimit}
                inputMode="numeric"
                max="10000"
                min="1"
                onChange={(event) => setLimitInput(event.target.value)}
                type="number"
                value={limitInput}
              />
              <button className="primary-button compact" disabled={updating} onClick={() => void saveLimit()} type="button">
                {updating ? t.saving : t.save}
              </button>
              <button className="secondary-button compact" disabled={updating} onClick={cancelLimitEdit} type="button">
                {t.cancel}
              </button>
            </div>
          ) : (
            <div className="ai-runtime-limit-value">
              <strong>{currentSettings.defaultDailyRequestLimit}</strong>
              <button className="secondary-button compact" disabled={updating} onClick={() => setEditingLimit(true)} type="button">
                <Pencil aria-hidden="true" />
                <span>{t.editLimit}</span>
              </button>
            </div>
          )}
          <small>{limitError || t.limitHint}</small>
        </div>

        <div className="ai-runtime-policy-item">
          <span>{t.lastUpdated}</span>
          <strong>{formatDateTime(currentSettings.updatedAt)}</strong>
          {currentSettings.updatedByDisplayName ? <small>{currentSettings.updatedByDisplayName}</small> : null}
        </div>
      </div>

      {pendingEnabled !== undefined ? (
        <div className="admin-confirm-dialog-backdrop">
          <div aria-labelledby="ai-runtime-confirm-title" aria-modal="true" className="admin-confirm-dialog" role="dialog">
            <h2 id="ai-runtime-confirm-title">{changingToEnabled ? t.confirmEnableTitle : t.confirmDisableTitle}</h2>
            <p>{changingToEnabled ? t.confirmEnableDescription : t.confirmDisableDescription}</p>
            <div className="button-row">
              <button className="secondary-button" disabled={updating} onClick={() => setPendingEnabled(undefined)} type="button">
                {t.cancel}
              </button>
              <button className="primary-button" disabled={updating} onClick={() => void confirmEnabledChange()} type="button">
                {updating ? t.saving : t.confirm}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
