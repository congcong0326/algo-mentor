import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { ApiRequestError, getAdminAiSettings, requireApiData, updateAdminAiSettings } from '../../services/api';
import type { AdminAiSettings } from '../../types/api';
import AiModelPricingPanel from './AiModelPricingPanel';
import AiModelRoutingPanel from './AiModelRoutingPanel';
import AiProviderModelPanel from './AiProviderModelPanel';
import AiRuntimePolicyBar from './AiRuntimePolicyBar';
import AiUsagePanel, { type AiUsageDimension, type AiUsageRouteState } from './AiUsagePanel';
import { aiGovernanceTabFromParam, type AiGovernanceTab } from './aiGovernanceRoute';
import { isoDateToday } from './aiFormat';

interface AiGovernancePageProps {
  onNavigate: (path: string) => void;
  search: string;
}

export default function AiGovernancePage({ onNavigate, search }: AiGovernancePageProps) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const route = routeFromSearch(search);
  const [settings, setSettings] = useState<AdminAiSettings>();
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [settingsUpdating, setSettingsUpdating] = useState(false);
  const [settingsError, setSettingsError] = useState('');
  const [usageRefreshKey, setUsageRefreshKey] = useState(0);
  const settingsRequestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    void loadSettings(controller.signal);
    return () => controller.abort();
  }, []);

  async function loadSettings(signal?: AbortSignal) {
    const requestId = settingsRequestIdRef.current + 1;
    settingsRequestIdRef.current = requestId;
    const current = () => settingsRequestIdRef.current === requestId && !signal?.aborted;
    setSettingsLoading(true);
    setSettingsError('');
    try {
      const data = requireApiData(await getAdminAiSettings(signal), t.settingsLoadFailed);
      if (current()) {
        setSettings(data);
      }
    } catch (caught) {
      if (current()) {
        setSettingsError(errorMessage(caught, t.settingsLoadFailed));
      }
    } finally {
      if (current()) {
        setSettingsLoading(false);
      }
    }
  }

  async function updateSettings(next: { aiEnabled: boolean; defaultDailyRequestLimit: number }): Promise<boolean> {
    setSettingsUpdating(true);
    setSettingsError('');
    try {
      const updated = requireApiData(await updateAdminAiSettings(next), t.settingsUpdateFailed);
      setSettings(updated);
      return true;
    } catch (caught) {
      setSettingsError(errorMessage(caught, t.settingsUpdateFailed));
      return false;
    } finally {
      setSettingsUpdating(false);
    }
  }

  function updateRoute(next: AiUsageRouteState, tab: AiGovernanceTab = route.tab) {
    onNavigate(`/admin/ai${searchForRoute(next, tab)}`);
  }

  return (
    <section className="ai-governance-page" aria-label={t.ariaLabel}>
      <AiRuntimePolicyBar
        error={settingsError}
        loading={settingsLoading}
        onRefresh={() => void loadSettings()}
        onUpdate={updateSettings}
        settings={settings}
        updating={settingsUpdating}
      />

      <div className="ai-governance-tabs" role="tablist" aria-label={t.title}>
        <button
          aria-controls="ai-providers-tab-panel"
          aria-selected={route.tab === 'providers'}
          className="ai-tab-button"
          onClick={() => updateRoute(route.filters, 'providers')}
          role="tab"
          type="button"
        >
          提供商与模型
        </button>
        <button
          aria-controls="ai-routing-tab-panel"
          aria-selected={route.tab === 'routing'}
          className="ai-tab-button"
          onClick={() => updateRoute(route.filters, 'routing')}
          role="tab"
          type="button"
        >
          模型路由
        </button>
        <button
          aria-controls="ai-usage-tab-panel"
          aria-selected={route.tab === 'usage'}
          className="ai-tab-button"
          onClick={() => updateRoute(route.filters, 'usage')}
          role="tab"
          type="button"
        >
          {t.usageTab}
        </button>
        <button
          aria-controls="ai-pricing-tab-panel"
          aria-selected={route.tab === 'pricing'}
          className="ai-tab-button"
          onClick={() => updateRoute(route.filters, 'pricing')}
          role="tab"
          type="button"
        >
          {t.pricingTab}
        </button>
      </div>

      {route.tab === 'providers' ? (
        <div id="ai-providers-tab-panel" role="tabpanel"><AiProviderModelPanel /></div>
      ) : route.tab === 'routing' ? (
        <div id="ai-routing-tab-panel" role="tabpanel"><AiModelRoutingPanel /></div>
      ) : route.tab === 'usage' ? (
        <div id="ai-usage-tab-panel" role="tabpanel">
          <AiUsagePanel
            filters={route.filters}
            onFiltersChange={(next) => updateRoute(next)}
            onNavigate={onNavigate}
            refreshKey={usageRefreshKey}
          />
        </div>
      ) : (
        <div id="ai-pricing-tab-panel" role="tabpanel">
          <AiModelPricingPanel
            from={route.filters.from}
            onPricingChanged={() => setUsageRefreshKey((current) => current + 1)}
            refreshKey={usageRefreshKey}
            to={route.filters.to}
          />
        </div>
      )}
    </section>
  );
}

function routeFromSearch(search: string): { filters: AiUsageRouteState; tab: AiGovernanceTab } {
  const params = new URLSearchParams(search);
  const today = isoDateToday();
  const dimension = params.get('dimension');
  const userId = parseUserId(params.get('userId'));
  return {
    tab: aiGovernanceTabFromParam(params.get('tab')),
    filters: {
      from: validDate(params.get('from')) ?? today,
      to: validDate(params.get('to')) ?? today,
      dimension: isDimension(dimension) ? dimension : 'user',
      userId,
      provider: blankToUndefined(params.get('provider')),
      model: blankToUndefined(params.get('model')),
      purpose: blankToUndefined(params.get('purpose')),
      source: blankToUndefined(params.get('source')),
    },
  };
}

function searchForRoute(filters: AiUsageRouteState, tab: AiGovernanceTab): string {
  const params = new URLSearchParams();
  params.set('tab', tab);
  params.set('from', filters.from);
  params.set('to', filters.to);
  params.set('dimension', filters.dimension);
  if (filters.userId) {
    params.set('userId', String(filters.userId));
  }
  setOptional(params, 'provider', filters.provider);
  setOptional(params, 'model', filters.model);
  setOptional(params, 'purpose', filters.purpose);
  setOptional(params, 'source', filters.source);
  return `?${params.toString()}`;
}

function setOptional(params: URLSearchParams, key: string, value: string | undefined) {
  if (value) {
    params.set(key, value);
  }
}

function isDimension(value: string | null): value is AiUsageDimension {
  return value === 'user' || value === 'model' || value === 'source';
}

function validDate(value: string | null): string | undefined {
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : undefined;
}

function parseUserId(value: string | null): number | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

function blankToUndefined(value: string | null): string | undefined {
  return value?.trim() || undefined;
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
