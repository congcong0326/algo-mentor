import { AlertTriangle, CirclePlus, Pencil, Power, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  createAdminAiModelPrice,
  getAdminAiModelPrices,
  requireApiData,
  updateAdminAiModelPrice,
} from '../../services/api';
import type {
  AdminAiModelPrice,
  AdminAiModelPricePage,
  AdminAiModelPriceWriteRequest,
} from '../../types/api';
import AiModelPriceDialog from './AiModelPriceDialog';
import { formatDateTime, formatNumber } from './aiFormat';

interface AiModelPricingPanelProps {
  from: string;
  onPricingChanged: () => void;
  refreshKey: number;
  to: string;
}

interface DialogState {
  initial?: Pick<AdminAiModelPriceWriteRequest, 'provider' | 'model'>;
  price?: AdminAiModelPrice;
}

const emptyPrices: AdminAiModelPricePage = { items: [], unpricedModels: [] };

export default function AiModelPricingPanel({
  from,
  onPricingChanged,
  refreshKey,
  to,
}: AiModelPricingPanelProps) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const [page, setPage] = useState<AdminAiModelPricePage>(emptyPrices);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<DialogState>();
  const [disableCandidate, setDisableCandidate] = useState<AdminAiModelPrice>();
  const requestIdRef = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [from, refreshKey, to]);

  async function load(signal?: AbortSignal) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const current = () => requestIdRef.current === requestId && !signal?.aborted;
    setLoading(true);
    setError('');
    try {
      const data = requireApiData(await getAdminAiModelPrices({ from, to }, signal), t.pricingLoadFailed);
      if (current()) {
        setPage(data);
      }
    } catch (caught) {
      if (current()) {
        setError(errorMessage(caught, t.pricingLoadFailed));
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  async function savePrice(request: AdminAiModelPriceWriteRequest): Promise<boolean> {
    setSaving(true);
    setError('');
    try {
      if (dialog?.price) {
        requireApiData(await updateAdminAiModelPrice(dialog.price.id, request), t.priceSaveFailed);
      } else {
        requireApiData(await createAdminAiModelPrice(request), t.priceSaveFailed);
      }
      await load();
      onPricingChanged();
      return true;
    } catch (caught) {
      setError(errorMessage(caught, t.priceSaveFailed));
      return false;
    } finally {
      setSaving(false);
    }
  }

  async function disablePrice() {
    if (!disableCandidate || saving) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      requireApiData(await updateAdminAiModelPrice(disableCandidate.id, priceRequest(disableCandidate, false)), t.priceSaveFailed);
      setDisableCandidate(undefined);
      await load();
      onPricingChanged();
    } catch (caught) {
      setError(errorMessage(caught, t.priceSaveFailed));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="ai-pricing-panel" aria-label={t.pricingTitle}>
      <div className="ai-panel-heading">
        <div>
          <p className="section-kicker">{t.pricingTab}</p>
          <h2>{t.pricingTitle}</h2>
          <p>{t.historicalPriceNotice}</p>
        </div>
        <div className="ai-panel-actions">
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
          <button className="primary-button" disabled={saving} onClick={() => setDialog({})} type="button">
            <CirclePlus aria-hidden="true" />
            <span>{t.addPrice}</span>
          </button>
        </div>
      </div>

      {error ? <p className="error-text" role="alert">{error}</p> : null}

      {page.unpricedModels.length > 0 ? (
        <section className="ai-unpriced-warning" aria-label={t.unpricedModels}>
          <div className="ai-unpriced-warning-heading">
            <AlertTriangle aria-hidden="true" />
            <h3>{t.unpricedModels}</h3>
          </div>
          <div className="ai-unpriced-model-list">
            {page.unpricedModels.map((model) => (
              <div className="ai-unpriced-model-row" key={`${model.provider ?? 'unknown'}:${model.model ?? 'unknown'}`}>
                <div>
                  <strong>{model.provider || 'UNKNOWN'} / {model.model || 'UNKNOWN'}</strong>
                  <span>{formatNumber(model.modelCallCount)} {t.modelCalls} · {formatNumber(model.totalTokens)} {t.totalTokens}</span>
                  <small>{t.lastSeenAt}: {formatDateTime(model.lastSeenAt)}</small>
                </div>
                <button
                  className="secondary-button compact"
                  onClick={() => setDialog({ initial: { provider: model.provider || '', model: model.model || '' } })}
                  type="button"
                >
                  <CirclePlus aria-hidden="true" />
                  <span>{t.configurePrice}</span>
                </button>
              </div>
            ))}
          </div>
        </section>
      ) : null}

      <div className="ai-data-table-wrap">
        <table className="ai-data-table ai-pricing-table">
          <thead>
            <tr>
              <th>{t.provider}</th>
              <th>{t.model}</th>
              <th>{t.inputPricePerMillion}</th>
              <th>{t.cachedInputPricePerMillion}</th>
              <th>{t.outputPricePerMillion}</th>
              <th>{t.costMultiplier}</th>
              <th>{t.priceStatus}</th>
              <th>{t.updatedAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? Array.from({ length: 4 }, (_, index) => (
              <tr className="ai-table-skeleton" key={`pricing-loading-${index}`}><td colSpan={9}>{t.pricingLoadFailed}</td></tr>
            )) : null}
            {!loading && page.items.map((price) => (
              <tr key={price.id}>
                <td>{price.provider}</td>
                <td>{price.model}</td>
                <td>{price.inputPricePerMillion}</td>
                <td>{price.cachedInputPricePerMillion}</td>
                <td>{price.outputPricePerMillion}</td>
                <td>{price.costMultiplier}</td>
                <td><span className={price.enabled ? 'ai-status-badge active' : 'ai-status-badge inactive'}>{price.enabled ? t.active : t.inactive}</span></td>
                <td>{formatDateTime(price.updatedAt)}</td>
                <td>
                  <div className="ai-table-actions">
                    <button className="secondary-button compact" disabled={saving} onClick={() => setDialog({ price })} type="button">
                      <Pencil aria-hidden="true" />
                      <span>{t.editPrice}</span>
                    </button>
                    {price.enabled ? (
                      <button className="secondary-button compact danger-button" disabled={saving} onClick={() => setDisableCandidate(price)} type="button">
                        <Power aria-hidden="true" />
                        <span>{t.disablePrice}</span>
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
            {!loading && page.items.length === 0 ? <tr><td colSpan={9}>{t.noResults}</td></tr> : null}
          </tbody>
        </table>
      </div>

      {dialog ? (
        <AiModelPriceDialog
          initial={dialog.initial}
          key={`${dialog.price?.id ?? 'new'}:${dialog.initial?.provider ?? ''}:${dialog.initial?.model ?? ''}`}
          onClose={() => setDialog(undefined)}
          onSubmit={savePrice}
          price={dialog.price}
          saving={saving}
        />
      ) : null}

      {disableCandidate ? (
        <div className="admin-confirm-dialog-backdrop">
          <div aria-labelledby="disable-price-confirm-title" aria-modal="true" className="admin-confirm-dialog" role="dialog">
            <h2 id="disable-price-confirm-title">{t.confirmDisablePriceTitle}</h2>
            <p>{t.confirmDisablePriceDescription}</p>
            <div className="button-row">
              <button className="secondary-button" disabled={saving} onClick={() => setDisableCandidate(undefined)} type="button">
                {t.cancel}
              </button>
              <button className="primary-button" disabled={saving} onClick={() => void disablePrice()} type="button">
                {saving ? t.saving : t.confirm}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function priceRequest(price: AdminAiModelPrice, enabled: boolean): AdminAiModelPriceWriteRequest {
  return {
    provider: price.provider,
    model: price.model,
    inputPricePerMillion: price.inputPricePerMillion,
    cachedInputPricePerMillion: price.cachedInputPricePerMillion,
    outputPricePerMillion: price.outputPricePerMillion,
    costMultiplier: price.costMultiplier,
    enabled,
  };
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
