import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import type { AdminAiModelPrice, AdminAiModelPriceWriteRequest } from '../../types/api';
import { isNonNegativeDecimal, isPositiveDecimal } from './aiFormat';

interface AiModelPriceDialogProps {
  initial?: Pick<AdminAiModelPriceWriteRequest, 'provider' | 'model'>;
  onClose: () => void;
  onSubmit: (request: AdminAiModelPriceWriteRequest) => Promise<boolean>;
  price?: AdminAiModelPrice;
  saving: boolean;
}

interface PriceFormState {
  provider: string;
  model: string;
  inputPricePerMillion: string;
  cachedInputPricePerMillion: string;
  outputPricePerMillion: string;
  costMultiplier: string;
  enabled: boolean;
}

function formFromPrice(
  price?: AdminAiModelPrice,
  initial?: Pick<AdminAiModelPriceWriteRequest, 'provider' | 'model'>,
): PriceFormState {
  return {
    provider: price?.provider ?? initial?.provider ?? '',
    model: price?.model ?? initial?.model ?? '',
    inputPricePerMillion: price?.inputPricePerMillion ?? '',
    cachedInputPricePerMillion: price?.cachedInputPricePerMillion ?? '',
    outputPricePerMillion: price?.outputPricePerMillion ?? '',
    costMultiplier: price?.costMultiplier ?? '1.000000',
    enabled: price?.enabled ?? true,
  };
}

export default function AiModelPriceDialog({
  initial,
  onClose,
  onSubmit,
  price,
  saving,
}: AiModelPriceDialogProps) {
  const { resources } = useI18n();
  const t = resources.adminAi;
  const [form, setForm] = useState<PriceFormState>(() => formFromPrice(price, initial));
  const [error, setError] = useState('');

  useEffect(() => {
    setForm(formFromPrice(price, initial));
    setError('');
  }, [initial?.model, initial?.provider, price?.id]);

  function setField<Key extends keyof PriceFormState>(key: Key, value: PriceFormState[Key]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit() {
    const provider = form.provider.trim().toLowerCase();
    const model = form.model.trim();
    if (!provider) {
      setError(t.providerRequired);
      return;
    }
    if (!model) {
      setError(t.modelRequired);
      return;
    }
    if (!isNonNegativeDecimal(form.inputPricePerMillion)
      || !isNonNegativeDecimal(form.cachedInputPricePerMillion)
      || !isNonNegativeDecimal(form.outputPricePerMillion)
      || !isPositiveDecimal(form.costMultiplier)) {
      setError(t.priceInvalid);
      return;
    }
    setError('');
    const succeeded = await onSubmit({
      provider,
      model,
      inputPricePerMillion: form.inputPricePerMillion.trim(),
      cachedInputPricePerMillion: form.cachedInputPricePerMillion.trim(),
      outputPricePerMillion: form.outputPricePerMillion.trim(),
      costMultiplier: form.costMultiplier.trim(),
      enabled: form.enabled,
    });
    if (succeeded) {
      onClose();
    }
  }

  const isEdit = !!price;

  return (
    <div className="admin-confirm-dialog-backdrop">
      <div aria-labelledby="ai-model-price-dialog-title" aria-modal="true" className="ai-model-price-dialog" role="dialog">
        <div className="ai-model-price-dialog-heading">
          <h2 id="ai-model-price-dialog-title">{isEdit ? t.priceDialogTitleEdit : t.priceDialogTitleCreate}</h2>
          <p>{t.historicalPriceNotice}</p>
        </div>
        {error ? <p className="error-text" role="alert">{error}</p> : null}
        <div className="ai-model-price-form">
          <label>
            <span>{t.provider}</span>
            <input
              autoComplete="off"
              onChange={(event) => setField('provider', event.target.value)}
              value={form.provider}
            />
          </label>
          <label>
            <span>{t.model}</span>
            <input
              autoComplete="off"
              onChange={(event) => setField('model', event.target.value)}
              value={form.model}
            />
          </label>
          <label>
            <span>{t.inputPricePerMillion}</span>
            <input
              inputMode="decimal"
              onChange={(event) => setField('inputPricePerMillion', event.target.value)}
              value={form.inputPricePerMillion}
            />
          </label>
          <label>
            <span>{t.cachedInputPricePerMillion}</span>
            <input
              inputMode="decimal"
              onChange={(event) => setField('cachedInputPricePerMillion', event.target.value)}
              value={form.cachedInputPricePerMillion}
            />
          </label>
          <label>
            <span>{t.outputPricePerMillion}</span>
            <input
              inputMode="decimal"
              onChange={(event) => setField('outputPricePerMillion', event.target.value)}
              value={form.outputPricePerMillion}
            />
          </label>
          <label>
            <span>{t.costMultiplier}</span>
            <input
              inputMode="decimal"
              onChange={(event) => setField('costMultiplier', event.target.value)}
              value={form.costMultiplier}
            />
          </label>
          <label className="ai-model-price-enabled-control">
            <span>{t.priceEnabled}</span>
            <input
              checked={form.enabled}
              onChange={(event) => setField('enabled', event.target.checked)}
              type="checkbox"
            />
          </label>
        </div>
        <div className="button-row">
          <button className="secondary-button" disabled={saving} onClick={onClose} type="button">
            {t.cancel}
          </button>
          <button className="primary-button" disabled={saving} onClick={() => void submit()} type="button">
            {saving ? t.saving : (isEdit ? t.updatePrice : t.createPrice)}
          </button>
        </div>
      </div>
    </div>
  );
}
