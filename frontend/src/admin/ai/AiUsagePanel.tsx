import { ExternalLink, RefreshCw, TriangleAlert } from 'lucide-react';
import { type FormEvent, useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  getAdminAiUsageByModel,
  getAdminAiUsageBySource,
  getAdminAiUsageByUser,
  getAdminAiUsageSummary,
  requireApiData,
} from '../../services/api';
import type {
  AdminAiUsageByModel,
  AdminAiUsageBySource,
  AdminAiUsageByUser,
  AdminAiUsageMetrics,
  AdminAiUsageQuery,
  AdminAiUsageSummary,
} from '../../types/api';
import { formatEstimatedUsd, formatNumber, isoDateDaysAgo, isoDateToday } from './aiFormat';

export type AiUsageDimension = 'user' | 'model' | 'source';

export interface AiUsageRouteState extends AdminAiUsageQuery {
  dimension: AiUsageDimension;
  from: string;
  to: string;
}

interface AiUsagePanelProps {
  filters: AiUsageRouteState;
  onFiltersChange: (next: AiUsageRouteState) => void;
  onNavigate: (path: string) => void;
  refreshKey: number;
}

type DimensionData = AdminAiUsageByUser[] | AdminAiUsageByModel[] | AdminAiUsageBySource[];

const EMPTY_METRICS: AdminAiUsageMetrics = {
  modelCallCount: 0,
  inputTokens: 0,
  cachedTokens: 0,
  outputTokens: 0,
  reasoningTokens: 0,
  totalTokens: 0,
  pricedCallCount: 0,
  pricedTokenCount: 0,
  estimatedCostUsd: '0.00000000',
  unpricedCallCount: 0,
  unpricedTokenCount: 0,
};

export default function AiUsagePanel({ filters, onFiltersChange, onNavigate, refreshKey }: AiUsagePanelProps) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const [draft, setDraft] = useState(() => draftFromFilters(filters));
  const [summary, setSummary] = useState<AdminAiUsageSummary>();
  const [rows, setRows] = useState<DimensionData>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const requestIdRef = useRef(0);

  useEffect(() => {
    setDraft(draftFromFilters(filters));
  }, [filters.from, filters.model, filters.provider, filters.purpose, filters.source, filters.to, filters.userId]);

  useEffect(() => {
    const controller = new AbortController();
    void load(filters, controller.signal);
    return () => controller.abort();
  }, [filters.dimension, filters.from, filters.model, filters.provider, filters.purpose, filters.source, filters.to, filters.userId, refreshKey]);

  async function load(nextFilters: AiUsageRouteState, signal?: AbortSignal) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const current = () => requestIdRef.current === requestId && !signal?.aborted;
    setLoading(true);
    setError('');
    try {
      const query = usageQuery(nextFilters);
      const dimensionRequest = nextFilters.dimension === 'user'
        ? getAdminAiUsageByUser({ ...query, page: 1, pageSize: 100 }, signal)
        : nextFilters.dimension === 'model'
          ? getAdminAiUsageByModel(query, signal)
          : getAdminAiUsageBySource(query, signal);
      const [summaryResponse, dimensionResponse] = await Promise.all([
        getAdminAiUsageSummary(query, signal),
        dimensionRequest,
      ]);
      if (!current()) {
        return;
      }
      setSummary(requireApiData(summaryResponse, t.usageLoadFailed));
      if (nextFilters.dimension === 'user') {
        setRows(requireApiData(dimensionResponse as Awaited<ReturnType<typeof getAdminAiUsageByUser>>, t.usageLoadFailed).items);
      } else {
        setRows(requireApiData(dimensionResponse as Awaited<ReturnType<typeof getAdminAiUsageByModel>>, t.usageLoadFailed));
      }
    } catch (caught) {
      if (current()) {
        setError(errorMessage(caught, t.usageLoadFailed));
        setRows([]);
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  function applyDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const hasUserId = !!draft.userId.trim();
    const parsedUserId = hasUserId ? Number(draft.userId) : 0;
    if (hasUserId && (!Number.isSafeInteger(parsedUserId) || parsedUserId < 1)) {
      setError(t.userId);
      return;
    }
    const userId = hasUserId ? parsedUserId : undefined;
    onFiltersChange({
      ...filters,
      from: draft.from,
      to: draft.to,
      userId,
      provider: blankToUndefined(draft.provider),
      model: blankToUndefined(draft.model),
      purpose: blankToUndefined(draft.purpose),
      source: blankToUndefined(draft.source),
    });
  }

  function selectQuickRange(days: number) {
    const to = isoDateToday();
    onFiltersChange({
      ...filters,
      from: days === 0 ? to : isoDateDaysAgo(days - 1),
      to,
    });
  }

  function setDimension(dimension: AiUsageDimension) {
    onFiltersChange({ ...filters, dimension });
  }

  function clearFilters() {
    const today = isoDateToday();
    onFiltersChange({ from: today, to: today, dimension: filters.dimension });
  }

  function filterToUser(userId: number) {
    onFiltersChange({ ...filters, userId, dimension: 'user' });
  }

  const metrics = summary?.metrics ?? EMPTY_METRICS;
  const quickToday = filters.from === isoDateToday() && filters.to === isoDateToday();
  const quickSeven = filters.from === isoDateDaysAgo(6) && filters.to === isoDateToday();
  const quickThirty = filters.from === isoDateDaysAgo(29) && filters.to === isoDateToday();

  return (
    <section className="ai-usage-panel" aria-label={t.usageTitle}>
      <div className="ai-panel-heading">
        <div>
          <p className="section-kicker">{t.usageTab}</p>
          <h2>{t.usageTitle}</h2>
        </div>
        <button
          aria-label={t.refresh}
          className="icon-button"
          disabled={loading}
          onClick={() => void load(filters)}
          title={t.refresh}
          type="button"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </div>

      <div className="ai-usage-range-controls" role="group" aria-label={t.customRange}>
        <button aria-pressed={quickToday} className="segmented-button" onClick={() => selectQuickRange(0)} type="button">{t.today}</button>
        <button aria-pressed={quickSeven} className="segmented-button" onClick={() => selectQuickRange(7)} type="button">{t.last7Days}</button>
        <button aria-pressed={quickThirty} className="segmented-button" onClick={() => selectQuickRange(30)} type="button">{t.last30Days}</button>
      </div>

      <form className="ai-usage-filter-form" onSubmit={applyDraft}>
        <label>
          <span>{t.from}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))} type="date" value={draft.from} />
        </label>
        <label>
          <span>{t.to}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))} type="date" value={draft.to} />
        </label>
        <label>
          <span>{t.userId}</span>
          <input inputMode="numeric" onChange={(event) => setDraft((current) => ({ ...current, userId: event.target.value }))} value={draft.userId} />
        </label>
        <label>
          <span>{t.provider}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, provider: event.target.value }))} value={draft.provider} />
        </label>
        <label>
          <span>{t.model}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, model: event.target.value }))} value={draft.model} />
        </label>
        <label>
          <span>{t.purpose}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, purpose: event.target.value }))} value={draft.purpose} />
        </label>
        <label>
          <span>{t.source}</span>
          <input onChange={(event) => setDraft((current) => ({ ...current, source: event.target.value }))} value={draft.source} />
        </label>
        <div className="ai-usage-filter-actions">
          <button className="primary-button compact" type="submit">{t.apply}</button>
          <button className="secondary-button compact" onClick={clearFilters} type="button">{t.clear}</button>
        </div>
      </form>

      {error ? <p className="error-text" role="alert">{error}</p> : null}

      <div className="ai-usage-metrics" aria-busy={loading}>
        <Metric label={t.admittedEntryRequests} value={formatNumber(summary?.admittedEntryRequestCount)} />
        <Metric label={t.modelCalls} value={formatNumber(metrics.modelCallCount)} />
        <Metric label={t.inputTokens} value={formatNumber(metrics.inputTokens)} />
        <Metric label={t.cachedTokens} value={formatNumber(metrics.cachedTokens)} />
        <Metric label={t.outputTokens} value={formatNumber(metrics.outputTokens)} />
        <Metric label={t.totalTokens} value={formatNumber(metrics.totalTokens)} />
        <Metric label={t.estimatedCost} note={t.estimatedAtCurrentPrices} value={formatEstimatedUsd(metrics.estimatedCostUsd)} />
        <Metric
          alert={metrics.unpricedCallCount > 0}
          label={t.unpricedCalls}
          note={`${formatNumber(metrics.unpricedTokenCount)} ${t.unpricedTokens}`}
          value={formatNumber(metrics.unpricedCallCount)}
        />
      </div>

      {metrics.unpricedCallCount > 0 ? (
        <div className="ai-usage-unpriced-note" role="status">
          <TriangleAlert aria-hidden="true" />
          <span>{t.unpricedCalls}: {formatNumber(metrics.unpricedCallCount)} · {t.unpricedTokens}: {formatNumber(metrics.unpricedTokenCount)}</span>
        </div>
      ) : null}

      <div className="ai-dimension-controls" role="group" aria-label={t.usageTitle}>
        <button aria-pressed={filters.dimension === 'user'} className="segmented-button" onClick={() => setDimension('user')} type="button">{t.byUser}</button>
        <button aria-pressed={filters.dimension === 'model'} className="segmented-button" onClick={() => setDimension('model')} type="button">{t.byModel}</button>
        <button aria-pressed={filters.dimension === 'source'} className="segmented-button" onClick={() => setDimension('source')} type="button">{t.bySource}</button>
      </div>

      {filters.dimension === 'user'
        ? <UserTable loading={loading} onFilterToUser={filterToUser} onNavigate={onNavigate} rows={rows as AdminAiUsageByUser[]} />
        : filters.dimension === 'model'
          ? <ModelTable loading={loading} rows={rows as AdminAiUsageByModel[]} />
          : <SourceTable loading={loading} rows={rows as AdminAiUsageBySource[]} />}
    </section>
  );
}

function Metric({ alert, label, note, value }: { alert?: boolean; label: string; note?: string; value: string }) {
  return (
    <div className={alert ? 'ai-metric-card warning' : 'ai-metric-card'}>
      <span>{label}</span>
      <strong>{value}</strong>
      {note ? <small>{note}</small> : null}
    </div>
  );
}

function UserTable({
  loading,
  onFilterToUser,
  onNavigate,
  rows,
}: {
  loading: boolean;
  onFilterToUser: (userId: number) => void;
  onNavigate: (path: string) => void;
  rows: AdminAiUsageByUser[];
}) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  return (
    <div className="ai-data-table-wrap">
      <table className="ai-data-table ai-usage-table ai-user-usage-table">
        <thead><tr><th>{t.userId}</th><th>{t.accountStatus}</th><th>{t.requestQuota}</th><th>{t.modelCalls}</th><th>{t.inputTokens}</th><th>{t.cachedTokens}</th><th>{t.outputTokens}</th><th>{t.totalTokens}</th><th>{t.estimatedCost}</th><th>{t.unpricedCalls}</th><th>{t.actions}</th></tr></thead>
        <tbody>
          {loading ? <LoadingRows columnCount={11} /> : null}
          {!loading && rows.map((row) => (
            <tr key={row.userId}>
              <td><strong>{row.displayName || row.email || `#${row.userId}`}</strong><small>#{row.userId} {row.email || ''}</small></td>
              <td>{row.accountStatus || '—'}<small>{row.effectiveAiEnabled ? t.active : t.inactive}</small></td>
              <td>{formatNumber(row.todayEntryRequestCount)} / {formatNumber(row.effectiveDailyRequestLimit)}</td>
              <td>{formatNumber(row.metrics.modelCallCount)}</td>
              <td>{formatNumber(row.metrics.inputTokens)}</td>
              <td>{formatNumber(row.metrics.cachedTokens)}</td>
              <td>{formatNumber(row.metrics.outputTokens)}</td>
              <td>{formatNumber(row.metrics.totalTokens)}</td>
              <td>{formatEstimatedUsd(row.metrics.estimatedCostUsd)}<small>{t.estimatedAtCurrentPrices}</small></td>
              <td className={row.metrics.unpricedCallCount > 0 ? 'ai-warning-cell' : ''}>{formatNumber(row.metrics.unpricedCallCount)}<small>{formatNumber(row.metrics.unpricedTokenCount)} {t.unpricedTokens}</small></td>
              <td><div className="ai-table-actions"><button className="secondary-button compact" onClick={() => onNavigate(`/admin/users?userId=${row.userId}`)} type="button"><ExternalLink aria-hidden="true" /><span>{t.viewUser}</span></button><button className="secondary-button compact" onClick={() => onFilterToUser(row.userId)} type="button">{t.filterUser}</button></div></td>
            </tr>
          ))}
          {!loading && rows.length === 0 ? <tr><td colSpan={11}>{t.noResults}</td></tr> : null}
        </tbody>
      </table>
    </div>
  );
}

function ModelTable({ loading, rows }: { loading: boolean; rows: AdminAiUsageByModel[] }) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  return (
    <div className="ai-data-table-wrap"><table className="ai-data-table ai-usage-table"><thead><tr><th>{t.provider}</th><th>{t.model}</th><th>{t.priceStatus}</th><th>{t.modelCalls}</th><th>{t.inputTokens}</th><th>{t.cachedTokens}</th><th>{t.outputTokens}</th><th>{t.totalTokens}</th><th>{t.estimatedCost}</th></tr></thead><tbody>
      {loading ? <LoadingRows columnCount={9} /> : null}
      {!loading && rows.map((row) => <tr key={`${row.provider ?? 'unknown'}:${row.model ?? 'unknown'}`}><td>{row.provider || 'UNKNOWN'}</td><td>{row.model || 'UNKNOWN'}</td><td><span className={row.priced ? 'ai-status-badge active' : 'ai-status-badge warning'}>{row.priced ? t.priced : t.unpriced}</span></td><td>{formatNumber(row.metrics.modelCallCount)}</td><td>{formatNumber(row.metrics.inputTokens)}</td><td>{formatNumber(row.metrics.cachedTokens)}</td><td>{formatNumber(row.metrics.outputTokens)}</td><td>{formatNumber(row.metrics.totalTokens)}</td><td>{row.priced ? formatEstimatedUsd(row.metrics.estimatedCostUsd) : t.unpriced}</td></tr>)}
      {!loading && rows.length === 0 ? <tr><td colSpan={9}>{t.noResults}</td></tr> : null}
    </tbody></table></div>
  );
}

function SourceTable({ loading, rows }: { loading: boolean; rows: AdminAiUsageBySource[] }) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  return (
    <div className="ai-data-table-wrap"><table className="ai-data-table ai-usage-table"><thead><tr><th>{t.source}</th><th>{t.modelCalls}</th><th>{t.inputTokens}</th><th>{t.cachedTokens}</th><th>{t.outputTokens}</th><th>{t.totalTokens}</th><th>{t.estimatedCost}</th><th>{t.unpricedCalls}</th></tr></thead><tbody>
      {loading ? <LoadingRows columnCount={8} /> : null}
      {!loading && rows.map((row) => <tr key={row.source}><td>{row.source}</td><td>{formatNumber(row.metrics.modelCallCount)}</td><td>{formatNumber(row.metrics.inputTokens)}</td><td>{formatNumber(row.metrics.cachedTokens)}</td><td>{formatNumber(row.metrics.outputTokens)}</td><td>{formatNumber(row.metrics.totalTokens)}</td><td>{formatEstimatedUsd(row.metrics.estimatedCostUsd)}<small>{t.estimatedAtCurrentPrices}</small></td><td className={row.metrics.unpricedCallCount > 0 ? 'ai-warning-cell' : ''}>{formatNumber(row.metrics.unpricedCallCount)}<small>{formatNumber(row.metrics.unpricedTokenCount)} {t.unpricedTokens}</small></td></tr>)}
      {!loading && rows.length === 0 ? <tr><td colSpan={8}>{t.noResults}</td></tr> : null}
    </tbody></table></div>
  );
}

function LoadingRows({ columnCount }: { columnCount: number }) {
  const { resources } = useI18n();
  return <>{Array.from({ length: 4 }, (_, index) => <tr className="ai-table-skeleton" key={`usage-loading-${index}`}><td colSpan={columnCount}>{resources.adminAi.usageLoadFailed}</td></tr>)}</>;
}

function draftFromFilters(filters: AiUsageRouteState) {
  return {
    from: filters.from,
    to: filters.to,
    userId: filters.userId ? String(filters.userId) : '',
    provider: filters.provider ?? '',
    model: filters.model ?? '',
    purpose: filters.purpose ?? '',
    source: filters.source ?? '',
  };
}

function usageQuery(filters: AiUsageRouteState): AdminAiUsageQuery {
  return {
    from: filters.from,
    to: filters.to,
    userId: filters.userId,
    provider: filters.provider,
    model: filters.model,
    purpose: filters.purpose,
    source: filters.source,
  };
}

function blankToUndefined(value: string): string | undefined {
  return value.trim() || undefined;
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
