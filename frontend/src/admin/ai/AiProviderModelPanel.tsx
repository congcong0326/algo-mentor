import { CirclePlus, Pencil, RefreshCw, Save } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  ApiRequestError,
  createAdminAiProvider,
  createAdminAiProviderModel,
  getAdminAiProvider,
  getAdminAiProviderModels,
  getAdminAiProviders,
  getAdminAiProviderTypes,
  requireApiData,
  updateAdminAiModel,
  updateAdminAiProvider,
} from '../../services/api';
import type { AdminAiConfiguredModel, AdminAiProvider, AdminAiProviderType } from '../../types/api';

export default function AiProviderModelPanel() {
  const [providers, setProviders] = useState<AdminAiProvider[]>([]);
  const [types, setTypes] = useState<AdminAiProviderType[]>([]);
  const [selected, setSelected] = useState<AdminAiProvider>();
  const [models, setModels] = useState<AdminAiConfiguredModel[]>([]);
  const [name, setName] = useState('');
  const [providerType, setProviderType] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [configText, setConfigText] = useState('{}');
  const [modelName, setModelName] = useState('');
  const [modelId, setModelId] = useState('');
  const [modelEnabled, setModelEnabled] = useState(true);
  const [editingModel, setEditingModel] = useState<AdminAiConfiguredModel>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { void load(); }, []);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [nextProviders, nextTypes] = await Promise.all([
        requireApiData(await getAdminAiProviders(), 'Provider list failed'),
        requireApiData(await getAdminAiProviderTypes(), 'Provider type list failed'),
      ]);
      setProviders(nextProviders.items);
      setTypes(nextTypes.items);
      if (!selected) {
        const nextProviderType = nextTypes.items.some((type) => type.code === providerType)
          ? providerType
          : nextTypes.items[0]?.code ?? '';
        if (nextProviderType !== providerType) {
          setProviderType(nextProviderType);
          setConfigText(configTemplate(nextTypes.items, nextProviderType));
        }
      }
      if (selected) {
        const current = nextProviders.items.find((item) => item.id === selected.id);
        if (current) await selectProvider(current.id);
      }
    } catch (caught) {
      setError(message(caught));
    } finally {
      setLoading(false);
    }
  }

  async function selectProvider(id: number) {
    setError('');
    try {
      const [provider, modelPage] = await Promise.all([
        requireApiData(await getAdminAiProvider(id), 'Provider detail failed'),
        requireApiData(await getAdminAiProviderModels(id), 'Provider models failed'),
      ]);
      setSelected(provider);
      setModels(modelPage.items);
      setName(provider.name);
      setProviderType(provider.providerType);
      setEnabled(provider.enabled);
      setConfigText(JSON.stringify(provider.config ?? {}, null, 2));
      setEditingModel(undefined);
    } catch (caught) {
      setError(message(caught));
    }
  }

  function newProvider() {
    setSelected(undefined);
    setModels([]);
    setName('');
    setEnabled(true);
    const nextProviderType = providerType || types[0]?.code || '';
    setProviderType(nextProviderType);
    setConfigText(configTemplate(types, nextProviderType));
    setEditingModel(undefined);
  }

  function changeProviderType(nextProviderType: string) {
    const currentTemplate = configTemplate(types, providerType);
    setProviderType(nextProviderType);
    if (!selected && configText === currentTemplate) {
      setConfigText(configTemplate(types, nextProviderType));
    }
  }

  async function saveProvider() {
    const config = parseConfig();
    if (!config || !name.trim() || !providerType) return;
    setSaving(true); setError('');
    try {
      const provider = selected
        ? requireApiData(await updateAdminAiProvider(selected.id, { name: name.trim(), enabled, config }), 'Provider update failed')
        : requireApiData(await createAdminAiProvider({ name: name.trim(), providerType, enabled, config }), 'Provider create failed');
      await load();
      await selectProvider(provider.id);
    } catch (caught) {
      setError(message(caught));
    } finally { setSaving(false); }
  }

  async function saveModel() {
    if (!selected || !modelName.trim() || !modelId.trim()) return;
    setSaving(true); setError('');
    try {
      const request = { displayName: modelName.trim(), modelId: modelId.trim(), enabled: modelEnabled };
      if (editingModel) await updateAdminAiModel(editingModel.id, request);
      else await createAdminAiProviderModel(selected.id, request);
      await selectProvider(selected.id);
      setModelName(''); setModelId(''); setModelEnabled(true); setEditingModel(undefined);
    } catch (caught) { setError(message(caught)); }
    finally { setSaving(false); }
  }

  function parseConfig(): Record<string, unknown> | undefined {
    try {
      const parsed: unknown = JSON.parse(configText);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Configuration must be an object.');
      return parsed as Record<string, unknown>;
    } catch (caught) {
      setError(message(caught));
      return undefined;
    }
  }

  function editModel(model: AdminAiConfiguredModel) {
    setEditingModel(model); setModelName(model.displayName); setModelId(model.modelId); setModelEnabled(model.enabled);
  }

  return <section className="ai-pricing-panel" aria-label="Providers and models">
    <div className="ai-panel-heading"><div><p className="section-kicker">Runtime</p><h2>Providers and models</h2></div><div className="ai-panel-actions">
      <button aria-label="Refresh providers" className="icon-button" disabled={loading || saving} onClick={() => void load()} title="Refresh" type="button"><RefreshCw aria-hidden="true" /></button>
      <button className="secondary-button compact" onClick={newProvider} type="button"><CirclePlus aria-hidden="true" /><span>New provider</span></button>
    </div></div>
    {error ? <p className="error-text" role="alert">{error}</p> : null}
    <div className="ai-data-table-wrap"><table className="ai-data-table"><thead><tr><th>Name</th><th>Type</th><th>Base URL</th><th>Models</th><th>Status</th><th>Updated</th><th>Action</th></tr></thead><tbody>
      {providers.map((provider) => <tr key={provider.id}><td>{provider.name}</td><td>{provider.providerType}</td><td>{baseUrl(provider)}</td><td>{provider.modelCount}</td><td><span className={provider.enabled ? 'ai-status-badge active' : 'ai-status-badge inactive'}>{provider.enabled ? 'Enabled' : 'Disabled'}</span></td><td>{formatTimestamp(provider.updatedAt)}</td><td><button className="secondary-button compact" onClick={() => void selectProvider(provider.id)} type="button"><Pencil aria-hidden="true" /><span>Edit</span></button></td></tr>)}
      {!loading && providers.length === 0 ? <tr><td colSpan={7}>No provider instances</td></tr> : null}
    </tbody></table></div>
    <div className="ai-provider-editor">
      <label>Name<input onChange={(event) => setName(event.target.value)} value={name} /></label>
      <label>Provider type<select disabled={Boolean(selected)} onChange={(event) => changeProviderType(event.target.value)} value={providerType}>{types.map((type) => <option key={type.code} value={type.code}>{type.displayName}</option>)}</select></label>
      <label className="checkbox-label"><input checked={enabled} onChange={(event) => setEnabled(event.target.checked)} type="checkbox" />Enabled</label>
      <label className="ai-provider-config">Configuration<textarea onChange={(event) => setConfigText(event.target.value)} rows={8} spellCheck={false} value={configText} /></label>
      <button className="primary-button" disabled={saving} onClick={() => void saveProvider()} type="button"><Save aria-hidden="true" /><span>{selected ? 'Save provider' : 'Create provider'}</span></button>
    </div>
    {selected ? <><div className="ai-panel-heading"><div><h3>Models for {selected.name}</h3></div></div><div className="ai-data-table-wrap"><table className="ai-data-table"><thead><tr><th>Name</th><th>Upstream model</th><th>Status</th><th>Updated</th><th>Action</th></tr></thead><tbody>{models.map((model) => <tr key={model.id}><td>{model.displayName}</td><td>{model.modelId}</td><td><span className={model.enabled ? 'ai-status-badge active' : 'ai-status-badge inactive'}>{model.enabled ? 'Enabled' : 'Disabled'}</span></td><td>{formatTimestamp(model.updatedAt)}</td><td><button className="secondary-button compact" onClick={() => editModel(model)} type="button"><Pencil aria-hidden="true" /><span>Edit</span></button></td></tr>)}</tbody></table></div><div className="ai-provider-editor compact-editor"><label>Display name<input onChange={(event) => setModelName(event.target.value)} value={modelName} /></label><label>Model ID<input onChange={(event) => setModelId(event.target.value)} value={modelId} /></label><label className="checkbox-label"><input checked={modelEnabled} onChange={(event) => setModelEnabled(event.target.checked)} type="checkbox" />Enabled</label><button className="secondary-button" disabled={saving} onClick={() => void saveModel()} type="button">{editingModel ? 'Save model' : 'Add model'}</button></div></> : null}
  </section>;
}

function message(error: unknown): string { return error instanceof ApiRequestError ? error.message : error instanceof Error ? error.message : 'Request failed'; }

function configTemplate(types: AdminAiProviderType[], providerType: string): string {
  return JSON.stringify(types.find((type) => type.code === providerType)?.defaultConfig ?? {}, null, 2);
}

function baseUrl(provider: AdminAiProvider): string {
  if (provider.baseUrl) return provider.baseUrl;
  return typeof provider.config?.baseUrl === 'string' && provider.config.baseUrl ? provider.config.baseUrl : '-';
}

function formatTimestamp(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
