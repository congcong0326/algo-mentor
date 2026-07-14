import { ExternalLink, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  getAdminAiUsageSummary,
  getAdminUserAiPolicy,
  requireApiData,
  updateAdminUserAiPolicy,
} from '../../services/api';
import type { AdminAiUsageSummary, AdminUserAiPolicy } from '../../types/api';
import { formatEstimatedUsd, formatNumber, isoDateDaysAgo, isoDateToday, isWholeLimit } from '../ai/aiFormat';

interface AdminUserAiSectionProps {
  onViewFullUsage: () => void;
  userId: number;
}

interface AiUsageSnapshots {
  last7?: AdminAiUsageSummary;
  last30?: AdminAiUsageSummary;
  today?: AdminAiUsageSummary;
}

export default function AdminUserAiSection({ onViewFullUsage, userId }: AdminUserAiSectionProps) {
  const { resources } = useI18n();
  const t = resources.adminUserAi;
  const [policy, setPolicy] = useState<AdminUserAiPolicy>();
  const [snapshots, setSnapshots] = useState<AiUsageSnapshots>({});
  const [policyLoading, setPolicyLoading] = useState(true);
  const [usageLoading, setUsageLoading] = useState(true);
  const [policyError, setPolicyError] = useState('');
  const [usageError, setUsageError] = useState('');
  const [saveError, setSaveError] = useState('');
  const [saving, setSaving] = useState(false);
  const [limitInput, setLimitInput] = useState('');
  const [pendingPause, setPendingPause] = useState<boolean>();
  const requestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [userId]);

  useEffect(() => {
    setLimitInput(policy?.dailyRequestLimitOverride ? String(policy.dailyRequestLimitOverride) : '');
  }, [policy?.dailyRequestLimitOverride, userId]);

  async function load(signal?: AbortSignal) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const current = () => requestIdRef.current === requestId && !signal?.aborted;
    setPolicyLoading(true);
    setUsageLoading(true);
    setPolicyError('');
    setUsageError('');
    const today = isoDateToday();
    const [policyResult, usageResult] = await Promise.allSettled([
      getAdminUserAiPolicy(userId, signal),
      Promise.all([
        getAdminAiUsageSummary({ userId, from: today, to: today }, signal),
        getAdminAiUsageSummary({ userId, from: isoDateDaysAgo(6), to: today }, signal),
        getAdminAiUsageSummary({ userId, from: isoDateDaysAgo(29), to: today }, signal),
      ]),
    ]);
    if (!current()) {
      return;
    }
    if (policyResult.status === 'fulfilled') {
      try {
        setPolicy(requireApiData(policyResult.value, t.policyLoadFailed));
      } catch (caught) {
        setPolicyError(errorMessage(caught, t.policyLoadFailed));
      }
    } else {
      setPolicyError(errorMessage(policyResult.reason, t.policyLoadFailed));
    }
    if (usageResult.status === 'fulfilled') {
      try {
        const [todaySummary, last7, last30] = usageResult.value.map((response) => requireApiData(response, t.usageLoadFailed));
        setSnapshots({ today: todaySummary, last7, last30 });
      } catch (caught) {
        setUsageError(errorMessage(caught, t.usageLoadFailed));
      }
    } else {
      setUsageError(errorMessage(usageResult.reason, t.usageLoadFailed));
    }
    if (current()) {
      setPolicyLoading(false);
      setUsageLoading(false);
    }
  }

  async function savePolicy(aiEnabledOverride: boolean | null, dailyRequestLimitOverride: number | null): Promise<boolean> {
    if (saving) {
      return false;
    }
    setSaving(true);
    setSaveError('');
    try {
      const updated = requireApiData(await updateAdminUserAiPolicy(userId, {
        aiEnabledOverride,
        dailyRequestLimitOverride,
      }), t.saveFailed);
      setPolicy(updated);
      await loadUsageOnly();
      return true;
    } catch (caught) {
      setSaveError(errorMessage(caught, t.saveFailed));
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function loadUsageOnly() {
    const today = isoDateToday();
    try {
      const responses = await Promise.all([
        getAdminAiUsageSummary({ userId, from: today, to: today }),
        getAdminAiUsageSummary({ userId, from: isoDateDaysAgo(6), to: today }),
        getAdminAiUsageSummary({ userId, from: isoDateDaysAgo(29), to: today }),
      ]);
      const [todaySummary, last7, last30] = responses.map((response) => requireApiData(response, t.usageLoadFailed));
      setSnapshots({ today: todaySummary, last7, last30 });
      setUsageError('');
    } catch (caught) {
      setUsageError(errorMessage(caught, t.usageLoadFailed));
    }
  }

  async function saveLimit() {
    if (!policy) {
      return;
    }
    if (limitInput.trim() && !isWholeLimit(limitInput)) {
      setSaveError(t.limitInvalid);
      return;
    }
    await savePolicy(policy.aiEnabledOverride ?? null, limitInput.trim() ? Number(limitInput) : null);
  }

  async function restoreInheritance() {
    const succeeded = await savePolicy(null, null);
    if (succeeded) {
      setLimitInput('');
    }
  }

  async function confirmPause() {
    if (!policy || pendingPause === undefined) {
      return;
    }
    const succeeded = await savePolicy(pendingPause ? false : null, policy.dailyRequestLimitOverride ?? null);
    if (succeeded) {
      setPendingPause(undefined);
    }
  }

  const paused = policy?.aiEnabledOverride === false;
  const disabledReason = policy?.effectiveDisabledReason;
  const effectiveStatus = !policy?.effectiveAiEnabled
    ? disabledReason === 'GLOBAL' ? t.globalDisabled : t.userPaused
    : t.normal;

  return (
    <section className="admin-user-ai-section" aria-label={t.title}>
      <div className="admin-user-ai-heading">
        <h3>{t.title}</h3>
        <button
          aria-label={resources.adminAi.refresh}
          className="icon-button"
          disabled={policyLoading || usageLoading || saving}
          onClick={() => void load()}
          title={resources.adminAi.refresh}
          type="button"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </div>
      {policyError ? <p className="error-text" role="alert">{policyError}</p> : null}
      {usageError ? <p className="error-text" role="alert">{usageError}</p> : null}
      {saveError ? <p className="error-text" role="alert">{saveError}</p> : null}

      {policyLoading && !policy ? <p role="status">{t.loading}</p> : null}
      {policy ? (
        <>
          <div className="admin-user-ai-status-grid">
            <div><span>{t.effectiveStatus}</span><strong className={policy.effectiveAiEnabled ? 'status-positive' : 'status-negative'}>{effectiveStatus}</strong></div>
            <div><span>{t.defaultLimit}</span><strong>{formatNumber(policy.globalDefaultDailyRequestLimit)}</strong></div>
            <div><span>{t.effectiveLimit}</span><strong>{formatNumber(policy.effectiveDailyRequestLimit)}</strong></div>
          </div>

          <div className="admin-user-ai-controls">
            <div className="admin-user-ai-toggle-row">
              <span>{paused ? t.paused : t.inherited}</span>
              <button
                aria-checked={!paused}
                aria-label={t.pauseUser}
                className="switch-control"
                disabled={saving || !policy.globalAiEnabled}
                onClick={() => setPendingPause(!paused)}
                role="switch"
                title={!policy.globalAiEnabled ? t.globalDisabled : undefined}
                type="button"
              >
                <span aria-hidden="true" />
              </button>
            </div>
            <label className="admin-user-ai-limit-input">
              <span>{t.overrideLimit}</span>
              <input
                inputMode="numeric"
                max="10000"
                min="1"
                onChange={(event) => setLimitInput(event.target.value)}
                placeholder={t.inherited}
                type="number"
                value={limitInput}
              />
            </label>
            <div className="admin-user-ai-control-actions">
              <button className="secondary-button compact" disabled={saving} onClick={() => void saveLimit()} type="button">{t.saveOverride}</button>
              <button className="secondary-button compact" disabled={saving} onClick={() => void restoreInheritance()} type="button">{t.restoreInheritance}</button>
            </div>
          </div>
        </>
      ) : null}

      <div className="admin-user-ai-usage-grid" aria-busy={usageLoading}>
        <UsageSummary label={t.todayEntryRequests} value={policy ? `${formatNumber(snapshots.today?.admittedEntryRequestCount)} / ${formatNumber(policy.effectiveDailyRequestLimit)}` : '—'} />
        <UsageSummary label={t.todayTokens} value={formatNumber(snapshots.today?.metrics.totalTokens)} />
        <UsageSummary label={t.todayEstimatedCost} value={formatEstimatedUsd(snapshots.today?.metrics.estimatedCostUsd)} />
        <UsageSummary label={t.last7DaysEstimatedCost} value={formatEstimatedUsd(snapshots.last7?.metrics.estimatedCostUsd)} />
        <UsageSummary label={t.last30DaysEstimatedCost} value={formatEstimatedUsd(snapshots.last30?.metrics.estimatedCostUsd)} />
      </div>

      <button className="secondary-button compact" onClick={onViewFullUsage} type="button">
        <ExternalLink aria-hidden="true" />
        <span>{t.viewFullUsage}</span>
      </button>

      {pendingPause !== undefined ? (
        <div className="admin-confirm-dialog-backdrop">
          <div aria-labelledby="user-ai-pause-confirm-title" aria-modal="true" className="admin-confirm-dialog" role="dialog">
            <h2 id="user-ai-pause-confirm-title">{pendingPause ? t.confirmPauseTitle : t.confirmResumeTitle}</h2>
            <p>{pendingPause ? t.confirmPauseDescription : t.confirmResumeDescription}</p>
            <div className="button-row">
              <button className="secondary-button" disabled={saving} onClick={() => setPendingPause(undefined)} type="button">{resources.adminAi.cancel}</button>
              <button className="primary-button" disabled={saving} onClick={() => void confirmPause()} type="button">{saving ? resources.adminAi.saving : resources.adminAi.confirm}</button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function UsageSummary({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>;
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
