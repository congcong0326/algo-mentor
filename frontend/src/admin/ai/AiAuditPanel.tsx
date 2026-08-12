import { ChevronLeft, ChevronRight, Eye, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  getAdminAiAuditRun,
  getAdminAiAuditRuns,
  getAdminAiAuditStep,
  getAdminAiAuditToolResult,
  requireApiData,
} from '../../services/api';
import type {
  AdminAiAuditRun,
  AdminAiAuditRunDetail,
  AdminAiAuditRunQuery,
  AdminAiAuditRunStatistics,
  AdminAiAuditStep,
  AdminAiAuditStepDetail,
  AdminAiAuditToolResult,
} from '../../types/api';
import { formatDateTime } from './aiFormat';

type SnapshotTab = 'overview' | 'messages' | 'tools' | 'compaction' | 'raw';

const PAGE_SIZE = 20;

export default function AiAuditPanel() {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const [draft, setDraft] = useState<AuditFilters>(() => defaultFilters());
  const [filters, setFilters] = useState<AuditFilters>(() => defaultFilters());
  const [page, setPage] = useState(1);
  const [runs, setRuns] = useState<AdminAiAuditRun[]>([]);
  const [total, setTotal] = useState(0);
  const [statistics, setStatistics] = useState<AdminAiAuditRunStatistics>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedRun, setSelectedRun] = useState<AdminAiAuditRunDetail>();
  const [runLoading, setRunLoading] = useState(false);
  const [runError, setRunError] = useState('');
  const [selectedStep, setSelectedStep] = useState<AdminAiAuditStepDetail>();
  const [stepLoading, setStepLoading] = useState(false);
  const [stepError, setStepError] = useState('');
  const [activeTab, setActiveTab] = useState<SnapshotTab>('overview');
  const [toolResults, setToolResults] = useState<Record<string, AdminAiAuditToolResult>>({});
  const [toolResultLoading, setToolResultLoading] = useState<string>();
  const listRequestId = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    void loadRuns(controller.signal);
    return () => controller.abort();
  }, [filters, page]);

  async function loadRuns(signal?: AbortSignal) {
    const requestId = listRequestId.current + 1;
    listRequestId.current = requestId;
    const current = () => requestId === listRequestId.current && !signal?.aborted;
    setLoading(true);
    setError('');
    try {
      const response = requireApiData(await getAdminAiAuditRuns(toQuery(filters, page), signal), t.auditLoadFailed);
      if (current()) {
        setRuns(response.items);
        setTotal(response.total);
        setStatistics(response.statistics ?? undefined);
      }
    } catch (caught) {
      if (current()) {
        setError(errorMessage(caught, t.auditLoadFailed));
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  async function openRun(runId: number) {
    setRunLoading(true);
    setRunError('');
    setSelectedStep(undefined);
    setToolResults({});
    try {
      const response = requireApiData(await getAdminAiAuditRun(runId), t.auditRunLoadFailed);
      setSelectedRun(response);
    } catch (caught) {
      setRunError(errorMessage(caught, t.auditRunLoadFailed));
    } finally {
      setRunLoading(false);
    }
  }

  async function openStep(step: AdminAiAuditStep, raw = false) {
    if (!selectedRun || !step.snapshotAvailable) {
      setSelectedStep(undefined);
      return;
    }
    setStepLoading(true);
    setStepError('');
    if (!raw) {
      setActiveTab('overview');
    }
    try {
      const response = requireApiData(
        await getAdminAiAuditStep(selectedRun.run.runId, step.stepIndex, { raw: raw || undefined }),
        t.auditStepLoadFailed,
      );
      setSelectedStep(response);
    } catch (caught) {
      setStepError(errorMessage(caught, t.auditStepLoadFailed));
    } finally {
      setStepLoading(false);
    }
  }

  async function loadToolResult(toolCallId: string) {
    if (!selectedRun || toolResultLoading === toolCallId) {
      return;
    }
    setToolResultLoading(toolCallId);
    try {
      const response = requireApiData(
        await getAdminAiAuditToolResult(selectedRun.run.runId, toolCallId, { includeContent: true, limit: 4_000 }),
        t.auditToolResultLoadFailed,
      );
      setToolResults((current) => ({ ...current, [toolCallId]: response }));
    } catch (caught) {
      setStepError(errorMessage(caught, t.auditToolResultLoadFailed));
    } finally {
      setToolResultLoading(undefined);
    }
  }

  function applyFilters() {
    setPage(1);
    setFilters(draft);
    setSelectedRun(undefined);
    setSelectedStep(undefined);
  }

  function clearFilters() {
    const next = defaultFilters();
    setDraft(next);
    setFilters(next);
    setPage(1);
    setSelectedRun(undefined);
    setSelectedStep(undefined);
  }

  if (selectedRun || runLoading || runError) {
    return (
      <AuditRunDetail
        activeTab={activeTab}
        detail={selectedRun}
        error={runError}
        loading={runLoading}
        onBack={() => {
          setSelectedRun(undefined);
          setSelectedStep(undefined);
          setRunError('');
        }}
        onLoadToolResult={(toolCallId) => void loadToolResult(toolCallId)}
        onSelectStep={(step) => void openStep(step)}
        onTabChange={(tab) => {
          setActiveTab(tab);
          if (tab === 'raw' && selectedStep && selectedStep.requestSnapshot == null) {
            const step = selectedRun?.steps.find((item) => item.stepIndex === selectedStep.step.stepIndex);
            if (step) {
              void openStep(step, true);
            }
          }
        }}
        selectedStep={selectedStep}
        stepError={stepError}
        stepLoading={stepLoading}
        t={t}
        toolResultLoading={toolResultLoading}
        toolResults={toolResults}
      />
    );
  }

  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));
  return (
    <section className="ai-audit-panel" aria-label={t.auditTitle}>
      <header className="ai-panel-heading">
        <div>
          <p className="section-kicker">AI Platform</p>
          <h2>{t.auditTitle}</h2>
        </div>
        <button aria-label={t.auditRefresh} className="icon-button" disabled={loading} onClick={() => void loadRuns()} title={t.auditRefresh} type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </header>

      <form className="ai-audit-filter-form" onSubmit={(event) => { event.preventDefault(); applyFilters(); }}>
        <label><span>{t.auditFrom}</span><input aria-label={t.auditFrom} onChange={(event) => setDraft({ ...draft, from: event.target.value })} type="datetime-local" value={draft.from} /></label>
        <label><span>{t.auditTo}</span><input aria-label={t.auditTo} onChange={(event) => setDraft({ ...draft, to: event.target.value })} type="datetime-local" value={draft.to} /></label>
        <label><span>{t.auditUser}</span><input aria-label={t.auditUser} inputMode="numeric" min="1" onChange={(event) => setDraft({ ...draft, userId: event.target.value })} value={draft.userId} /></label>
        <label><span>{t.auditScenario}</span><input aria-label={t.auditScenario} onChange={(event) => setDraft({ ...draft, scenario: event.target.value })} value={draft.scenario} /></label>
        <label><span>{t.purpose}</span><input aria-label={t.purpose} onChange={(event) => setDraft({ ...draft, purpose: event.target.value })} value={draft.purpose} /></label>
        <label><span>{t.source}</span><input aria-label={t.source} onChange={(event) => setDraft({ ...draft, source: event.target.value })} value={draft.source} /></label>
        <label><span>{t.auditTaskId}</span><input aria-label={t.auditTaskId} inputMode="numeric" min="1" onChange={(event) => setDraft({ ...draft, taskId: event.target.value })} value={draft.taskId} /></label>
        <label><span>{t.auditTurnId}</span><input aria-label={t.auditTurnId} inputMode="numeric" min="1" onChange={(event) => setDraft({ ...draft, turnId: event.target.value })} value={draft.turnId} /></label>
        <label><span>{t.provider}</span><input aria-label={t.provider} onChange={(event) => setDraft({ ...draft, provider: event.target.value })} value={draft.provider} /></label>
        <label><span>{t.model}</span><input aria-label={t.model} onChange={(event) => setDraft({ ...draft, model: event.target.value })} value={draft.model} /></label>
        <label><span>{t.auditStatus}</span><input aria-label={t.auditStatus} onChange={(event) => setDraft({ ...draft, status: event.target.value })} value={draft.status} /></label>
        <label><span>{t.auditFinishReason}</span><input aria-label={t.auditFinishReason} onChange={(event) => setDraft({ ...draft, finishReason: event.target.value })} value={draft.finishReason} /></label>
        <label><span>{t.auditRunId}</span><input aria-label={t.auditRunId} inputMode="numeric" min="1" onChange={(event) => setDraft({ ...draft, runId: event.target.value })} value={draft.runId} /></label>
        <label><span>{t.auditMinCachedTokens}</span><input aria-label={t.auditMinCachedTokens} inputMode="numeric" min="0" onChange={(event) => setDraft({ ...draft, minCachedTokens: event.target.value })} value={draft.minCachedTokens} /></label>
        <label><span>{t.auditMaxCachedTokens}</span><input aria-label={t.auditMaxCachedTokens} inputMode="numeric" min="0" onChange={(event) => setDraft({ ...draft, maxCachedTokens: event.target.value })} value={draft.maxCachedTokens} /></label>
        <label><span>{t.auditMinCacheRatio}</span><input aria-label={t.auditMinCacheRatio} inputMode="decimal" max="1" min="0" onChange={(event) => setDraft({ ...draft, minCacheRatio: event.target.value })} step="0.01" type="number" value={draft.minCacheRatio} /></label>
        <label><span>{t.auditMaxCacheRatio}</span><input aria-label={t.auditMaxCacheRatio} inputMode="decimal" max="1" min="0" onChange={(event) => setDraft({ ...draft, maxCacheRatio: event.target.value })} step="0.01" type="number" value={draft.maxCacheRatio} /></label>
        <label><span>{t.auditSort}</span><select aria-label={t.auditSort} onChange={(event) => setDraft({ ...draft, sort: event.target.value as AuditFilters['sort'] })} value={draft.sort}><option value="requestedAt">{t.auditSortRequestedAt}</option><option value="overBudget">{t.auditSortOverBudget}</option><option value="cacheRatio">{t.auditSortCacheRatio}</option></select></label>
        <label><span>{t.auditSortDirection}</span><select aria-label={t.auditSortDirection} onChange={(event) => setDraft({ ...draft, direction: event.target.value as AuditFilters['direction'] })} value={draft.direction}><option value="desc">{t.auditSortDescending}</option><option value="asc">{t.auditSortAscending}</option></select></label>
        <fieldset className="ai-audit-toggles">
          <label><input checked={draft.hasTools} onChange={(event) => setDraft({ ...draft, hasTools: event.target.checked })} type="checkbox" />{t.auditOnlyWithTools}</label>
          <label><input checked={draft.hasCompaction} onChange={(event) => setDraft({ ...draft, hasCompaction: event.target.checked })} type="checkbox" />{t.auditOnlyCompacted}</label>
          <label><input checked={draft.overBudget} onChange={(event) => setDraft({ ...draft, overBudget: event.target.checked })} type="checkbox" />{t.auditOnlyOverBudget}</label>
          <label><input checked={draft.providerError} onChange={(event) => setDraft({ ...draft, providerError: event.target.checked })} type="checkbox" />{t.auditOnlyProviderError}</label>
        </fieldset>
        <div className="ai-audit-filter-actions">
          <button className="primary-button compact" type="submit">{t.auditFilter}</button>
          <button className="secondary-button compact" onClick={clearFilters} type="button">{t.auditClear}</button>
        </div>
      </form>

      {error ? <p className="error-text" role="alert">{error}</p> : null}
      {statistics ? <AuditRunStatistics statistics={statistics} t={t} /> : null}
      <div className="ai-data-table-wrap ai-audit-table-wrap" aria-busy={loading}>
        <table className="ai-data-table ai-audit-table">
          <thead><tr><th>{t.auditTime}</th><th>{t.auditRun}</th><th>{t.auditUser}</th><th>{t.auditProviderModel}</th><th>{t.auditSteps}</th><th>{t.auditTools}</th><th>{t.auditEstimateBudget}</th><th>{t.auditActualInput}</th><th>{t.auditCached}</th><th>{t.auditCompaction}</th><th>{t.auditState}</th><th>{t.actions}</th></tr></thead>
          <tbody>
            {runs.map((run) => <AuditRunRow key={run.runId} onOpen={() => void openRun(run.runId)} run={run} t={t} />)}
            {!loading && runs.length === 0 ? <tr><td className="ai-empty-cell" colSpan={12}>{t.auditNoResults}</td></tr> : null}
          </tbody>
        </table>
      </div>
      <footer className="ai-audit-pagination">
        <span>{total}</span>
        <div>
          <button aria-label={t.auditPrevious} className="icon-button compact" disabled={page <= 1 || loading} onClick={() => setPage((current) => current - 1)} title={t.auditPrevious} type="button"><ChevronLeft aria-hidden="true" /></button>
          <strong>{page} / {pageCount}</strong>
          <button aria-label={t.auditNext} className="icon-button compact" disabled={page >= pageCount || loading} onClick={() => setPage((current) => current + 1)} title={t.auditNext} type="button"><ChevronRight aria-hidden="true" /></button>
        </div>
      </footer>
    </section>
  );
}

function AuditRunRow({ onOpen, run, t }: { onOpen: () => void; run: AdminAiAuditRun; t: ReturnType<typeof useI18n>['resources']['adminAi'] }) {
  const budget = run.promptTokenBudget;
  const estimate = run.finalRequestTokenEstimate;
  return <tr>
    <td>{formatDateTime(run.endedAt ?? run.startedAt)}</td>
    <td className="ai-audit-id-cell"><strong>{run.scenario || 'UNKNOWN'}</strong><small>{[run.purpose, run.source].filter(Boolean).join(' / ') || '—'}</small><small>#{run.taskId} / #{run.turnId} / #{run.runId}</small></td>
    <td>{run.userDisplayName || (run.userId ? `#${run.userId}` : '—')}</td>
    <td><strong>{run.provider || '—'}</strong><small>{run.model || '—'}</small></td>
    <td>{run.stepCount}{run.failedStepCount ? ` / ${run.failedStepCount}` : ''}</td>
    <td>{run.toolCallCount}{run.failedToolCallCount ? ` / ${run.failedToolCallCount}` : ''}</td>
    <td className={isOverBudget(run) ? 'ai-warning-cell' : undefined}>{formatTokens(estimate)} / {formatTokens(budget)}</td>
    <td className={isOverBudget(run) ? 'ai-warning-cell' : undefined}>{formatTokens(run.actualInputTokens)}{isOverBudget(run) ? <small>+{formatTokens(run.overBudgetTokens)}</small> : null}</td>
    <td>{formatTokens(run.cachedTokens)}<small>{formatRatio(cacheRatio(run.cachedTokens, run.actualInputTokens))}</small></td>
    <td>{run.compactionApplied ? `${run.compactionActionCount}` : '—'}</td>
    <td><StatusBadge error={run.providerError} value={run.status} /></td>
    <td><button aria-label={`${t.auditOpen} ${run.runId}`} className="icon-button compact" onClick={onOpen} title={t.auditOpen} type="button"><Eye aria-hidden="true" /></button></td>
  </tr>;
}

function AuditRunStatistics({ statistics, t }: {
  statistics: AdminAiAuditRunStatistics;
  t: ReturnType<typeof useI18n>['resources']['adminAi'];
}) {
  return (
    <dl className="ai-audit-list-statistics" aria-label={t.auditStatistics}>
      <div><dt>{t.auditStatisticsRuns}</dt><dd>{formatTokens(statistics.runCount)}</dd></div>
      <div><dt>{t.auditStatisticsOverBudget}</dt><dd>{formatTokens(statistics.overBudgetRunCount)} · {formatRatio(statistics.overBudgetRate)}</dd></div>
      <div><dt>{t.auditStatisticsCache}</dt><dd>{formatTokens(statistics.cachedTokens)} / {formatTokens(statistics.inputTokens)} · {formatRatio(statistics.cacheRatio)}</dd></div>
      <div><dt>{t.auditStatisticsCompaction}</dt><dd>{formatTokens(statistics.compactionRunCount)} · {formatRatio(statistics.compactionRate)}</dd></div>
    </dl>
  );
}

function AuditRunDetail({
  activeTab,
  detail,
  error,
  loading,
  onBack,
  onLoadToolResult,
  onSelectStep,
  onTabChange,
  selectedStep,
  stepError,
  stepLoading,
  t,
  toolResultLoading,
  toolResults,
}: {
  activeTab: SnapshotTab;
  detail?: AdminAiAuditRunDetail;
  error: string;
  loading: boolean;
  onBack: () => void;
  onLoadToolResult: (toolCallId: string) => void;
  onSelectStep: (step: AdminAiAuditStep) => void;
  onTabChange: (tab: SnapshotTab) => void;
  selectedStep?: AdminAiAuditStepDetail;
  stepError: string;
  stepLoading: boolean;
  t: ReturnType<typeof useI18n>['resources']['adminAi'];
  toolResultLoading?: string;
  toolResults: Record<string, AdminAiAuditToolResult>;
}) {
  if (loading) return <section className="ai-audit-panel" aria-busy="true" aria-label={t.auditTitle} />;
  if (!detail) return <section className="ai-audit-panel"><button className="secondary-button compact" onClick={onBack} type="button">{t.auditBackToRuns}</button><p className="error-text" role="alert">{error || t.auditRunLoadFailed}</p></section>;
  const run = detail.run;
  const totalUsage = detail.totalUsage;
  const budgetStatus = budgetStatusForRun(run);
  return <section className="ai-audit-panel ai-audit-detail" aria-label={t.auditTitle}>
    <header className="ai-panel-heading">
      <div><p className="section-kicker">{run.scenario || 'UNKNOWN'}</p><h2>Run #{run.runId}</h2></div>
      <button className="secondary-button compact" onClick={onBack} type="button">{t.auditBackToRuns}</button>
    </header>
    <div className="ai-audit-summary-grid">
      <Metric label={t.auditRun} value={`#${run.taskId} / #${run.turnId} / #${run.runId}`} />
      <Metric label={t.auditUser} value={run.userDisplayName || (run.userId ? `#${run.userId}` : '—')} />
      <Metric label={t.auditAttempt} value={String(detail.attemptNo)} />
      <Metric label={t.auditProviderModel} value={[run.provider, run.model].filter(Boolean).join(' / ') || '—'} />
      <Metric label={t.auditFinishReason} value={run.finishReason || '—'} />
      <Metric label={t.auditDuration} value={formatDuration(run.startedAt, run.endedAt)} />
      <Metric label={t.auditEstimateBudget} value={`${formatTokens(run.finalRequestTokenEstimate)} / ${formatTokens(run.promptTokenBudget)}`} warning={isOverBudget(run)} />
      <Metric label={t.auditBudgetStatus} value={budgetStatus} warning={isOverBudget(run)} />
      <Metric label={t.auditActualInput} value={formatTokens(totalUsage.inputTokens)} warning={isOverBudget(run)} />
      <Metric label={t.auditCached} value={formatTokens(totalUsage.cachedTokens)} />
      <Metric label={t.auditUncachedInput} value={formatTokens(totalUsage.uncachedInputTokens)} />
      <Metric label={t.auditCacheRatio} value={formatRatio(totalUsage.cacheRatio)} />
      <Metric label={t.auditOutputTokens} value={formatTokens(totalUsage.outputTokens)} />
      <Metric label={t.auditReasoningTokens} value={formatTokens(totalUsage.reasoningTokens)} />
      <Metric label={t.auditTotalTokens} value={formatTokens(totalUsage.totalTokens)} />
      <Metric label={t.auditTools} value={String(run.toolCallCount)} />
      <Metric label={t.auditFailedTools} value={String(run.failedToolCallCount)} warning={run.failedToolCallCount > 0} />
      <Metric label={t.auditCompaction} value={run.compactionApplied ? String(run.compactionActionCount) : '—'} />
      <Metric label={t.auditState} value={run.status || 'UNKNOWN'} warning={run.providerError} />
      <Metric label={t.auditTimeline} value={`${formatDateTime(run.startedAt)} - ${formatDateTime(run.endedAt)}`} />
    </div>
    {detail.errorMessage ? <p className="error-text">{t.auditError}: {detail.errorCode || 'UNKNOWN'} {detail.errorMessage}</p> : null}

    <section className="ai-audit-timeline" aria-label={t.auditTimeline}>
      <h3>{t.auditTimeline}</h3>
      {detail.steps.map((step) => <button className={`ai-audit-step${selectedStep?.step.stepIndex === step.stepIndex ? ' selected' : ''}`} key={step.stepIndex} onClick={() => onSelectStep(step)} type="button">
        <span>Step {step.stepIndex}</span>
        <span className="ai-audit-step-metrics">
          <strong>{formatTokens(step.finalRequestTokenEstimate)} / {formatTokens(step.promptTokenBudget)}</strong>
          <small>{t.auditDuration} {formatDuration(step.startedAt, step.endedAt)} · {t.auditFinishReason} {step.finishReason || '—'}</small>
        </span>
        <span className="ai-audit-step-metrics">
          <small>{step.messageCount ?? 0} {t.auditMessages} · {t.auditMessageRoles} {formatRoleCounts(step.roleCounts)} · {step.toolsCount ?? 0} {t.auditTools} · {formatTokens(step.messageTokenEstimate)} messages · {formatTokens(step.toolsTokenEstimate)} schema</small>
          <small>{t.auditActualInput} {formatTokens(step.usage.inputTokens)} · {t.auditCached} {formatTokens(step.usage.cachedTokens)} ({formatRatio(step.usage.cacheRatio)}) · {t.auditOutputTokens} {formatTokens(step.usage.outputTokens)}</small>
        </span>
        <span className="ai-audit-step-metrics">
          <small>{step.compactionApplied ? `${t.auditCompaction}: ${compactionActions(step.compaction).join(', ') || 'applied'}` : `${t.auditCompaction}: —`}</small>
          <small>{t.auditToolCalls} {step.toolCallCount}{step.failedToolCallCount ? ` · ${t.auditFailedTools} ${step.failedToolCallCount}` : ''}</small>
          {step.errorMessage ? <small className="error-text">{t.auditError}: {step.errorCode || 'UNKNOWN'} {step.errorMessage}</small> : null}
        </span>
        <StatusBadge error={Boolean(step.errorMessage)} value={step.status} />
      </button>)}
    </section>

    <section className="ai-audit-session" aria-label={t.auditSessionTurns}>
      <h3>{t.auditSessionTurns}</h3>
      <ol>{detail.taskTurns.map((turn) => <li key={turn.turnId}><strong>#{turn.sequenceNo}</strong><span><b>U</b> {truncate(turn.userMessage)}<br /><b>A</b> {truncate(turn.assistantMessage)}</span><small>{t.auditRunAttempts} {formatRunAttempts(turn.runAttempts, turn.runAttemptCount)} · {formatTokens(turn.usage.inputTokens)} input · {formatRatio(turn.usage.cacheRatio)} · {turn.hasTools ? t.auditTools : '—'}</small></li>)}</ol>
      {selectedStep ? <AuditHistoricalMessages messages={selectedStep.messages} t={t} /> : null}
    </section>

    <SnapshotViewer
      activeTab={activeTab}
      detail={selectedStep}
      error={stepError}
      loading={stepLoading}
      onLoadToolResult={onLoadToolResult}
      onTabChange={onTabChange}
      t={t}
      toolResultLoading={toolResultLoading}
      toolResults={toolResults}
    />
  </section>;
}

function SnapshotViewer({
  activeTab,
  detail,
  error,
  loading,
  onLoadToolResult,
  onTabChange,
  t,
  toolResultLoading,
  toolResults,
}: {
  activeTab: SnapshotTab;
  detail?: AdminAiAuditStepDetail;
  error: string;
  loading: boolean;
  onLoadToolResult: (toolCallId: string) => void;
  onTabChange: (tab: SnapshotTab) => void;
  t: ReturnType<typeof useI18n>['resources']['adminAi'];
  toolResultLoading?: string;
  toolResults: Record<string, AdminAiAuditToolResult>;
}) {
  return <section className="ai-audit-snapshot" aria-label={t.auditRequest}>
    <h3>{t.auditRequest}</h3>
    {loading ? <div aria-busy="true" className="ai-audit-loading" /> : null}
    {error ? <p className="error-text" role="alert">{error}</p> : null}
    {!loading && !detail ? <p className="empty-state">{t.auditNoSnapshot}</p> : null}
    {detail ? <>
      <div aria-label={t.auditRequest} className="ai-audit-tabs" role="tablist">
        <SnapshotTabButton active={activeTab === 'overview'} label={t.auditOverview} onClick={() => onTabChange('overview')} />
        <SnapshotTabButton active={activeTab === 'messages'} label={t.auditMessages} onClick={() => onTabChange('messages')} />
        <SnapshotTabButton active={activeTab === 'tools'} label={t.auditTools} onClick={() => onTabChange('tools')} />
        <SnapshotTabButton active={activeTab === 'compaction'} label={t.auditCompaction} onClick={() => onTabChange('compaction')} />
        <SnapshotTabButton active={activeTab === 'raw'} label={t.auditRawJson} onClick={() => onTabChange('raw')} />
      </div>
      {activeTab === 'overview' ? <SnapshotOverview detail={detail} t={t} /> : null}
      {activeTab === 'messages' ? <Messages messages={detail.messages} t={t} /> : null}
      {activeTab === 'tools' ? <Tools detail={detail} onLoadToolResult={onLoadToolResult} t={t} toolResultLoading={toolResultLoading} toolResults={toolResults} /> : null}
      {activeTab === 'compaction' ? <Compaction metadata={detail.metadata} t={t} /> : null}
      {activeTab === 'raw' ? <JsonBlock value={detail.requestSnapshot} /> : null}
    </> : null}
  </section>;
}

function SnapshotOverview({ detail, t }: { detail: AdminAiAuditStepDetail; t: ReturnType<typeof useI18n>['resources']['adminAi'] }) {
  const step = detail.step;
  return <dl className="ai-audit-definition-list">
    <div><dt>{t.auditMessageCount}</dt><dd>{formatTokens(step.messageTokenEstimate)} ({step.messageCount ?? 0})</dd></div>
    <div><dt>{t.auditToolsCount}</dt><dd>{formatTokens(step.toolsTokenEstimate)} ({step.toolsCount ?? 0})</dd></div>
    <div><dt>{t.auditFinalEstimate}</dt><dd>{formatTokens(step.finalRequestTokenEstimate)}</dd></div>
    <div><dt>{t.auditAssemblyEstimate}</dt><dd>{formatTokens(numericMetadata(detail.metadata, 'assemblyTokenEstimate'))}</dd></div>
    <div><dt>{t.auditBudgetStatus}</dt><dd>{textMetadata(detail.metadata, 'budgetStatus')}</dd></div>
    <div><dt>{t.auditRemainingBudget}</dt><dd>{formatTokens(step.remainingBudgetTokens)}</dd></div>
    <div><dt>{t.auditDuration}</dt><dd>{formatDuration(step.startedAt, step.endedAt)}</dd></div>
    <div><dt>{t.auditStartedAt}</dt><dd>{formatDateTime(step.startedAt)}</dd></div>
    <div><dt>Provider overhead</dt><dd>{formatTokens(step.providerOverheadTokenEstimate)}</dd></div>
    <div><dt>{t.auditActualInput}</dt><dd>{formatTokens(step.usage.inputTokens)}</dd></div>
    <div><dt>{t.auditCached}</dt><dd>{formatTokens(step.usage.cachedTokens)}</dd></div>
    <div><dt>{t.auditUncachedInput}</dt><dd>{formatTokens(step.usage.uncachedInputTokens)}</dd></div>
    <div><dt>{t.auditOutputTokens}</dt><dd>{formatTokens(step.usage.outputTokens)}</dd></div>
    <div><dt>{t.auditReasoningTokens}</dt><dd>{formatTokens(step.usage.reasoningTokens)}</dd></div>
    <div><dt>{t.auditTotalTokens}</dt><dd>{formatTokens(step.usage.totalTokens)}</dd></div>
    <div><dt>{t.auditCacheRatio}</dt><dd>{formatRatio(step.usage.cacheRatio)}</dd></div>
    <div><dt>Tool choice</dt><dd>{toJson(detail.toolChoice)}</dd></div>
    <div><dt>Generation</dt><dd>{toJson(detail.generationOptions)}</dd></div>
    <div><dt>Hash</dt><dd>{detail.requestHash || '—'}</dd></div>
    <div><dt>Redaction</dt><dd>{detail.redactionPolicyVersion || '—'}</dd></div>
  </dl>;
}

function Messages({ messages, t }: { messages?: unknown[] | null; t: ReturnType<typeof useI18n>['resources']['adminAi'] }) {
  if (!messages?.length) return <p className="empty-state">—</p>;
  return <ol className="ai-audit-message-list">{messages.map((message, index) => {
    const record = asRecord(message);
    const role = typeof record.role === 'string' ? record.role : 'UNKNOWN';
    const source = messageSource(record);
    const content = messageContent(record);
    const toolCallId = stringValue(record.toolCallId);
    const sectionId = messageSectionId(record);
    return <li key={index}>
      <header><strong>{role}</strong>{source ? <StatusBadge value={source} /> : null}<small>{t.auditMessageCharacters} {content.length} · {t.auditMessageTokenEstimate} {estimateTextTokens(content)}</small></header>
      <dl className="ai-audit-inline-definition-list">
        <div><dt>{t.auditMessageSource}</dt><dd>{source || '—'}</dd></div>
        <div><dt>{t.auditMessageSection}</dt><dd>{sectionId || '—'}</dd></div>
        <div><dt>{t.auditMessageToolCallId}</dt><dd>{toolCallId || '—'}</dd></div>
        <div><dt>{t.auditMessageCharacters}</dt><dd>{content.length}</dd></div>
      </dl>
      <div><small className="ai-audit-content-label">{t.auditMessageContent}</small><pre className="ai-audit-json">{content || '—'}</pre></div>
    </li>;
  })}</ol>;
}

function AuditHistoricalMessages({ messages, t }: {
  messages?: unknown[] | null;
  t: ReturnType<typeof useI18n>['resources']['adminAi'];
}) {
  const historicalMessages = (messages ?? []).flatMap((message) => {
    const record = asRecord(message);
    const sectionId = messageSectionId(record);
    return isHistoricalSection(sectionId)
      ? [{ sectionId, role: stringValue(record.role) || 'UNKNOWN', content: messageContent(record) }]
      : [];
  });
  return <div className="ai-audit-history-reference" aria-label={t.auditHistoricalMessages}>
    <h4>{t.auditHistoricalMessages}</h4>
    {historicalMessages.length ? <ol>{historicalMessages.map((message, index) => <li key={`${message.sectionId}:${index}`}>
      <strong>{message.sectionId}</strong><span>{message.role} · {truncate(message.content)}</span>
    </li>)}</ol> : <p className="empty-state">{t.auditNoHistoricalMessages}</p>}
  </div>;
}

function Tools({ detail, onLoadToolResult, t, toolResultLoading, toolResults }: {
  detail: AdminAiAuditStepDetail;
  onLoadToolResult: (toolCallId: string) => void;
  t: ReturnType<typeof useI18n>['resources']['adminAi'];
  toolResultLoading?: string;
  toolResults: Record<string, AdminAiAuditToolResult>;
}) {
  const tools = detail.tools ?? [];
  return <div className="ai-audit-tools-view">
    <div className="ai-audit-tools-summary"><strong>{t.auditToolSchema}</strong><span>{t.auditToolSchemaCount} {tools.length} · {t.auditToolSchemaEstimate} {formatTokens(detail.step.toolsTokenEstimate)}</span></div>
    {tools.length ? <ol className="ai-audit-tool-schema-list">{tools.map((tool, index) => <ToolSchema key={index} tool={tool} t={t} />)}</ol> : <p className="empty-state">—</p>}
    <h4>{t.auditToolCalls}</h4>
    {detail.toolCalls.length ? <ol className="ai-audit-tool-call-list">{detail.toolCalls.map((call) => {
      const result = toolResults[call.toolCallId];
      const displayedResult = call.preview ?? call.result;
      const preview = call.preview != null || isAuditPreview(displayedResult);
      return <li key={call.toolCallId}><header><strong>{call.toolName || call.toolCallId}</strong><StatusBadge value={call.status} /><small>{formatDuration(call.startedAt, call.endedAt, call.durationMillis)} · {formatTokens(call.resultTokenEstimate)} tokens</small></header>
        <div className="ai-audit-tool-sequence"><b>{t.auditToolArguments}</b><JsonBlock value={call.arguments} /><b>{preview ? t.auditToolResultPreview : t.auditToolResult}</b><JsonBlock value={displayedResult} /></div>
        {call.resultStorageMode || call.resultRef ? <div className="ai-audit-tool-result-control"><span>{t.auditToolResultStorageMode}: {call.resultStorageMode || '—'}</span>{call.resultRef ? <span>{t.auditToolResultReference}: {call.resultRef}</span> : null}{call.resultStorageMode === 'blob' || call.resultRef ? <button className="secondary-button compact" disabled={toolResultLoading === call.toolCallId} onClick={() => onLoadToolResult(call.toolCallId)} type="button">{t.auditViewContent}</button> : null}</div> : null}
        {call.errorMessage ? <p className="error-text">{t.auditError}: {call.errorCode || 'UNKNOWN'} {call.errorMessage}</p> : null}
        {result ? result.content ? <pre className="ai-audit-json">{result.content}</pre> : <p className="empty-state">{t.auditContentUnavailable}</p> : null}
      </li>;
    })}</ol> : <p className="empty-state">—</p>}
  </div>;
}

function ToolSchema({ tool, t }: { tool: unknown; t: ReturnType<typeof useI18n>['resources']['adminAi'] }) {
  const record = asRecord(tool);
  const name = stringValue(record.name) || stringValue(asRecord(record.function).name) || 'UNKNOWN';
  const description = stringValue(record.description) || stringValue(asRecord(record.function).description);
  const schema = record.inputSchema ?? record.parameters ?? asRecord(record.function).parameters;
  return <li><header><strong>{name}</strong><small>{t.auditToolDescription} {description || '—'}</small></header><small className="ai-audit-content-label">{t.auditToolParameters}</small><JsonBlock value={schema} /></li>;
}

function Compaction({ metadata, t }: { metadata?: Record<string, unknown> | null; t: ReturnType<typeof useI18n>['resources']['adminAi'] }) {
  const values = pickCompaction(metadata);
  const beforeChars = numericMetadata(metadata, 'compactionBeforeChars');
  const afterChars = numericMetadata(metadata, 'compactionAfterChars');
  const beforeTokens = numericMetadata(metadata, 'compactionBeforeTokenEstimate');
  const afterTokens = numericMetadata(metadata, 'compactionAfterTokenEstimate');
  const actions = compactionActions(metadata);
  return <div className="ai-audit-compaction-view">
    <dl className="ai-audit-definition-list">
      <div><dt>{t.auditCompactionBefore}</dt><dd>{formatTokens(beforeChars)} chars / {formatTokens(beforeTokens)} tokens</dd></div>
      <div><dt>{t.auditCompactionAfter}</dt><dd>{formatTokens(afterChars)} chars / {formatTokens(afterTokens)} tokens</dd></div>
      <div><dt>{t.auditCompactionActions}</dt><dd>{actions.join(', ') || '—'}</dd></div>
      <div><dt>{t.auditCompaction}</dt><dd>{booleanMetadata(metadata, 'compactionApplied') ? 'applied' : '—'}</dd></div>
    </dl>
    <JsonBlock value={values} />
  </div>;
}

function SnapshotTabButton({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return <button aria-selected={active} className="ai-tab-button" onClick={onClick} role="tab" type="button">{label}</button>;
}

function Metric({ label, value, warning = false }: { label: string; value: string; warning?: boolean }) {
  return <div><span>{label}</span><strong className={warning ? 'ai-warning-cell' : undefined}>{value}</strong></div>;
}

function StatusBadge({ error = false, value }: { error?: boolean; value?: string | null }) {
  return <span className={`ai-status-badge${error ? ' warning' : ''}`}>{value || 'UNKNOWN'}</span>;
}

function JsonBlock({ value }: { value: unknown }) {
  return <pre className="ai-audit-json">{toJson(value)}</pre>;
}

function defaultFilters(): AuditFilters {
  const now = new Date();
  const previous = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  return {
    from: toDateTimeLocal(previous), to: toDateTimeLocal(now), userId: '', scenario: '', purpose: '', source: '', taskId: '', turnId: '',
    provider: '', model: '', status: '', finishReason: '', runId: '', minCachedTokens: '', maxCachedTokens: '', minCacheRatio: '', maxCacheRatio: '',
    hasTools: false, hasCompaction: false, overBudget: false, providerError: false, sort: 'requestedAt', direction: 'desc',
  };
}

function toQuery(filters: AuditFilters, page: number): AdminAiAuditRunQuery {
  return {
    from: filters.from ? new Date(filters.from).toISOString() : undefined,
    to: filters.to ? new Date(filters.to).toISOString() : undefined,
    page,
    pageSize: PAGE_SIZE,
    userId: positive(filters.userId),
    scenario: blank(filters.scenario),
    purpose: blank(filters.purpose),
    source: blank(filters.source),
    taskId: positive(filters.taskId),
    turnId: positive(filters.turnId),
    provider: blank(filters.provider),
    model: blank(filters.model),
    status: blank(filters.status),
    finishReason: blank(filters.finishReason),
    runId: positive(filters.runId),
    hasTools: filters.hasTools || undefined,
    hasCompaction: filters.hasCompaction || undefined,
    overBudget: filters.overBudget || undefined,
    providerError: filters.providerError || undefined,
    minCachedTokens: nonNegative(filters.minCachedTokens),
    maxCachedTokens: nonNegative(filters.maxCachedTokens),
    minCacheRatio: ratio(filters.minCacheRatio),
    maxCacheRatio: ratio(filters.maxCacheRatio),
    sort: filters.sort,
    direction: filters.direction,
  };
}

function toDateTimeLocal(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function positive(value: string): number | undefined {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function nonNegative(value: string): number | undefined {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : undefined;
}

function ratio(value: string): number | undefined {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 1 ? parsed : undefined;
}

function blank(value: string): string | undefined { return value.trim() || undefined; }
function formatTokens(value: number | null | undefined): string { return value === null || value === undefined ? '—' : new Intl.NumberFormat().format(value); }
function formatRatio(value: number | null | undefined): string { return value === null || value === undefined ? '—' : `${(value * 100).toFixed(1)}%`; }
function cacheRatio(cached: number | null | undefined, input: number | null | undefined): number | undefined {
  return cached === null || cached === undefined || input === null || input === undefined || input <= 0
    ? undefined
    : cached / input;
}
function formatDuration(start?: string | null, end?: string | null, durationMillis?: number | null): string {
  const duration = durationMillis ?? durationBetween(start, end);
  if (duration === null || duration < 0) return '—';
  if (duration < 1_000) return `${duration} ms`;
  if (duration < 60_000) return `${(duration / 1_000).toFixed(1)} s`;
  return `${Math.floor(duration / 60_000)}m ${Math.floor((duration % 60_000) / 1_000)}s`;
}
function durationBetween(start?: string | null, end?: string | null): number | null {
  if (!start || !end) return null;
  const duration = new Date(end).getTime() - new Date(start).getTime();
  return Number.isFinite(duration) ? duration : null;
}
function isOverBudget(run: AdminAiAuditRun): boolean { return (run.overBudgetTokens ?? 0) > 0; }
function budgetStatusForRun(run: AdminAiAuditRun): string {
  if (isOverBudget(run)) return 'PROVIDER_ACTUAL_OVER_BUDGET';
  if (!run.promptTokenBudget) return 'UNKNOWN_PROVIDER_USAGE';
  return (run.finalRequestTokenEstimate ?? 0) > run.promptTokenBudget ? 'ESTIMATE_OVER_BUDGET' : 'WITHIN_ESTIMATE';
}
function formatRunAttempts(attempts: AdminAiAuditRunDetail['taskTurns'][number]['runAttempts'], fallbackCount: number): string {
  return attempts.length
    ? attempts.map((attempt) => `#${attempt.runId} (${attempt.status || 'UNKNOWN'})`).join(', ')
    : String(fallbackCount);
}
function truncate(value?: string | null): string { return !value ? '—' : value.length > 160 ? `${value.slice(0, 160)}...` : value; }
function toJson(value: unknown): string { return value === undefined || value === null ? '—' : JSON.stringify(value, null, 2); }
function asRecord(value: unknown): Record<string, unknown> { return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function stringValue(value: unknown): string | undefined { return typeof value === 'string' ? value : undefined; }
function messageSource(record: Record<string, unknown>): string | undefined {
  return stringValue(record.auditSource) ?? stringValue(asRecord(record.metadata).auditSource);
}
function messageSectionId(record: Record<string, unknown>): string | undefined {
  return stringValue(record.auditSectionId) ?? stringValue(asRecord(record.metadata).auditSectionId);
}
function isHistoricalSection(sectionId?: string): boolean {
  return sectionId?.includes('.history.') ?? false;
}
function isAuditPreview(value: unknown): boolean {
  return stringValue(asRecord(value).type) === 'audit_tool_result_preview';
}
function formatRoleCounts(roleCounts?: Record<string, number>): string {
  const entries = Object.entries(roleCounts ?? {}).filter(([, count]) => Number.isFinite(count) && count > 0);
  return entries.length ? entries.sort(([left], [right]) => left.localeCompare(right)).map(([role, count]) => `${role}: ${count}`).join(', ') : '—';
}
function messageContent(record: Record<string, unknown>): string {
  const content = record.content;
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) return content.map(contentPart).filter(Boolean).join('\n');
  return content === undefined || content === null ? '' : toJson(content);
}
function contentPart(value: unknown): string {
  const record = asRecord(value);
  return stringValue(record.text) ?? stringValue(record.content) ?? toJson(record.result ?? value);
}
function estimateTextTokens(value: string): number { return Math.ceil(value.length / 4); }
function pickCompaction(metadata?: Record<string, unknown> | null): Record<string, unknown> {
  if (!metadata) return {};
  return Object.fromEntries(Object.entries(metadata).filter(([key]) => key.toLowerCase().includes('compaction') || key.includes('snipped') || key.includes('truncated') || key.includes('dropped') || key.includes('toolResult')));
}
function compactionActions(metadata?: Record<string, unknown> | null): string[] {
  const actions = metadata?.compactionActions;
  return Array.isArray(actions) ? actions.filter((action): action is string => typeof action === 'string') : [];
}
function numericMetadata(metadata: Record<string, unknown> | null | undefined, key: string): number | undefined {
  const value = metadata?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
function booleanMetadata(metadata: Record<string, unknown> | null | undefined, key: string): boolean {
  return metadata?.[key] === true;
}
function textMetadata(metadata: Record<string, unknown> | null | undefined, key: string): string {
  return stringValue(metadata?.[key]) || '—';
}
function errorMessage(error: unknown, fallback: string): string { return error instanceof ApiRequestError ? error.message || fallback : error instanceof Error ? error.message : fallback; }

interface AuditFilters {
  from: string;
  to: string;
  userId: string;
  scenario: string;
  purpose: string;
  source: string;
  taskId: string;
  turnId: string;
  provider: string;
  model: string;
  status: string;
  finishReason: string;
  runId: string;
  minCachedTokens: string;
  maxCachedTokens: string;
  minCacheRatio: string;
  maxCacheRatio: string;
  hasTools: boolean;
  hasCompaction: boolean;
  overBudget: boolean;
  providerError: boolean;
  sort: 'requestedAt' | 'overBudget' | 'cacheRatio';
  direction: 'asc' | 'desc';
}
