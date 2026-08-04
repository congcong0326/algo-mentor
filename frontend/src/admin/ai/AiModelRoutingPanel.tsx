import { ArrowDown, ArrowUp, CirclePlus, Pencil, RefreshCw, Save, Search, Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  ApiRequestError,
  createAdminPolicy,
  deleteAdminPolicy,
  getAdminAiEffectiveRoute,
  getAdminAiProviderTypes,
  getAdminAiProviderModels,
  getAdminAiProviders,
  getAdminAiRoutingScenarios,
  getAdminPolicies,
  reorderAdminPolicies,
  requireApiData,
  updateAdminPolicy,
} from '../../services/api';
import { LLM_REASONING_EFFORT_VALUES } from '../../types/api';
import type {
  AdminAiConfiguredModel,
  AdminAiEffectiveRoute,
  AdminAiProvider,
  AdminAiProviderType,
  AdminAiRoutingScenario,
  AdminGenericPolicy,
  AiModelRoutePolicyContent,
  GenericPolicyStatus,
  LlmReasoningEffort,
  PolicySubject,
  PolicySubjectType,
} from '../../types/api';

interface RouteModelOption {
  model: AdminAiConfiguredModel;
  provider: AdminAiProvider;
}

export default function AiModelRoutingPanel() {
  const [scenarios, setScenarios] = useState<AdminAiRoutingScenario[]>([]);
  const [selected, setSelected] = useState<AdminAiRoutingScenario>();
  const [policies, setPolicies] = useState<AdminGenericPolicy<AiModelRoutePolicyContent>[]>([]);
  const [models, setModels] = useState<RouteModelOption[]>([]);
  const [providerTypes, setProviderTypes] = useState<AdminAiProviderType[]>([]);
  const [editing, setEditing] = useState<AdminGenericPolicy<AiModelRoutePolicyContent> | null | undefined>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<GenericPolicyStatus>('ENABLED');
  const [modelId, setModelId] = useState('');
  const [reasoningEffort, setReasoningEffort] = useState<LlmReasoningEffort | null>(null);
  const [reasoningEffortError, setReasoningEffortError] = useState('');
  const [priority, setPriority] = useState('1');
  const [allSubject, setAllSubject] = useState(true);
  const [subjects, setSubjects] = useState<PolicySubject[]>([]);
  const [subjectType, setSubjectType] = useState<PolicySubjectType>('USER');
  const [subjectId, setSubjectId] = useState('');
  const [userId, setUserId] = useState('');
  const [effective, setEffective] = useState<AdminAiEffectiveRoute>();
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => { void load(); }, []);

  async function load() {
    setLoading(true);
    setMessage('');
    try {
      const [scenarioPage, providerPage, providerTypePage] = await Promise.all([
        requireApiData(await getAdminAiRoutingScenarios(), 'Routing scenarios failed'),
        requireApiData(await getAdminAiProviders(), 'Provider list failed'),
        requireApiData(await getAdminAiProviderTypes(), 'Provider type list failed'),
      ]);
      const nextModels = await loadModels(providerPage.items);
      setScenarios(scenarioPage.items);
      setModels(nextModels);
      setProviderTypes(providerTypePage.items);
      const nextSelected = selected
        ? scenarioPage.items.find((scenario) => scenario.scenarioCode === selected.scenarioCode)
        : scenarioPage.items[0];
      if (nextSelected) await selectScenario(nextSelected);
      else {
        setSelected(undefined);
        setPolicies([]);
      }
    } catch (caught) {
      setMessage(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  async function loadModels(providers: AdminAiProvider[]): Promise<RouteModelOption[]> {
    const pages = await Promise.all(providers.map(async (provider) => ({
      provider,
      page: requireApiData(await getAdminAiProviderModels(provider.id), 'Provider models failed'),
    })));
    return pages.flatMap(({ provider, page }) => page.items.map((model) => ({ model, provider })));
  }

  async function selectScenario(scenario: AdminAiRoutingScenario) {
    setSelected(scenario);
    setEffective(undefined);
    setEditing(undefined);
    setMessage('');
    try {
      const page = requireApiData(await getAdminPolicies<AiModelRoutePolicyContent>({
        typeCode: scenario.policyTypeCode,
        pageSize: 100,
      }), 'Routing rules failed');
      setPolicies(page.items);
    } catch (caught) {
      setMessage(errorMessage(caught));
      setPolicies([]);
    }
  }

  function openEditor(policy: AdminGenericPolicy<AiModelRoutePolicyContent> | null) {
    setEditing(policy);
    setMessage('');
    setName(policy?.name ?? '');
    setDescription(policy?.description ?? '');
    setStatus(policy?.status ?? 'ENABLED');
    const nextModelId = policy ? String(policy.content.modelId) : models[0] ? String(models[0].model.id) : '';
    const savedEffort = policy?.content.reasoningEffort ?? null;
    const availableEfforts = reasoningEffortsFor(nextModelId, models, providerTypes);
    const directoryError = reasoningEffortDirectoryError(nextModelId, models, providerTypes);
    setModelId(nextModelId);
    if (directoryError) {
      setReasoningEffort(null);
      setReasoningEffortError(directoryError);
    } else if (savedEffort !== null && (!isKnownReasoningEffort(savedEffort) || !availableEfforts.includes(savedEffort))) {
      setReasoningEffort(null);
      setReasoningEffortError('The route contains an unknown or unsupported reasoning effort. Select a valid value before saving.');
    } else {
      setReasoningEffort(savedEffort);
      setReasoningEffortError('');
    }
    setPriority(String(policy?.priority ?? policies.length + 1));
    setAllSubject(policy?.subjectRange.allSubject ?? true);
    setSubjects(policy?.subjectRange.subjects ?? []);
    setSubjectType('USER');
    setSubjectId('');
  }

  function closeEditor() {
    setEditing(undefined);
    setSubjectId('');
    setReasoningEffortError('');
  }

  function changeModel(nextModelId: string) {
    const nextEfforts = reasoningEffortsFor(nextModelId, models, providerTypes);
    const directoryError = reasoningEffortDirectoryError(nextModelId, models, providerTypes);
    setModelId(nextModelId);
    if (directoryError) {
      setReasoningEffort(null);
      setReasoningEffortError(directoryError);
      return;
    }
    setReasoningEffortError('');
    if (reasoningEffort !== null && !nextEfforts.includes(reasoningEffort)) {
      setReasoningEffort(null);
      setMessage('Reasoning effort reset to Provider default because it is not supported by the selected provider.');
    }
  }

  function addSubject() {
    const parsed = Number(subjectId);
    if (!Number.isSafeInteger(parsed) || parsed < 1) {
      setMessage('Enter a valid subject ID.');
      return;
    }
    if (subjects.some((subject) => subject.type === subjectType && subject.id === parsed)) {
      setMessage('This subject is already included.');
      return;
    }
    setSubjects((current) => [...current, { type: subjectType, id: parsed }]);
    setSubjectId('');
  }

  function removeSubject(subject: PolicySubject) {
    setSubjects((current) => current.filter((item) => item.type !== subject.type || item.id !== subject.id));
  }

  async function saveRule() {
    if (!selected) return;
    const parsedModelId = Number(modelId);
    const requestedPriority = Number(priority);
    if (!name.trim() || !Number.isSafeInteger(parsedModelId) || parsedModelId < 1) {
      setMessage('Rule name and target model are required.');
      return;
    }
    if (!allSubject && subjects.length === 0) {
      setMessage('Add at least one user or group, or choose all users.');
      return;
    }
    if (!Number.isSafeInteger(requestedPriority) || requestedPriority < 1) {
      setMessage('Priority must be a positive integer.');
      return;
    }
    const availableEfforts = reasoningEffortsFor(modelId, models, providerTypes);
    const directoryError = reasoningEffortDirectoryError(modelId, models, providerTypes);
    if (reasoningEffortError || directoryError || (reasoningEffort !== null && !availableEfforts.includes(reasoningEffort))) {
      setMessage(reasoningEffortError || directoryError || 'Select a reasoning effort supported by the target provider.');
      return;
    }
    setSaving(true);
    setMessage('');
    try {
      const subjectRange = { allSubject, subjects: allSubject ? [] : subjects };
      const content: AiModelRoutePolicyContent = { modelId: parsedModelId, reasoningEffort };
      const saved = editing
        ? requireApiData(await updateAdminPolicy<AiModelRoutePolicyContent>(editing.id, {
          name: name.trim(),
          description: description.trim(),
          status,
          subjectRange,
          content,
          version: editing.version,
        }), 'Route rule update failed')
        : requireApiData(await createAdminPolicy<AiModelRoutePolicyContent>({
          typeCode: selected.policyTypeCode,
          name: name.trim(),
          description: description.trim(),
          status,
          subjectRange,
          content,
        }), 'Route rule creation failed');
      const reordered = editing
        ? policies.map((policy) => policy.id === saved.id ? saved : policy)
        : [...policies, saved];
      await applyPriority(reordered, saved.id, requestedPriority);
      closeEditor();
      await refreshSelectedScenario();
      setMessage('Route rule saved.');
    } catch (caught) {
      setMessage(errorMessage(caught));
      await refreshSelectedScenario();
    } finally {
      setSaving(false);
    }
  }

  async function applyPriority(
    current: AdminGenericPolicy<AiModelRoutePolicyContent>[],
    policyId: number,
    requestedPriority: number,
  ) {
    if (!selected) return;
    const ordered = [...current].sort((left, right) => left.priority - right.priority);
    const index = ordered.findIndex((policy) => policy.id === policyId);
    if (index < 0) return;
    const [moving] = ordered.splice(index, 1);
    ordered.splice(Math.min(Math.max(requestedPriority, 1), ordered.length + 1) - 1, 0, moving);
    if (ordered.every((policy, nextIndex) => policy.id === current.slice().sort((left, right) => left.priority - right.priority)[nextIndex]?.id)) {
      return;
    }
    await reorderAdminPolicies(selected.policyTypeCode, {
      policyIds: ordered.map((policy) => policy.id),
      versions: Object.fromEntries(ordered.map((policy) => [policy.id, policy.version])),
    });
  }

  async function movePolicy(index: number, direction: -1 | 1) {
    if (!selected || index + direction < 0 || index + direction >= policies.length) return;
    setSaving(true);
    setMessage('');
    try {
      const ordered = [...policies];
      const [moving] = ordered.splice(index, 1);
      ordered.splice(index + direction, 0, moving);
      await reorderAdminPolicies(selected.policyTypeCode, {
        policyIds: ordered.map((policy) => policy.id),
        versions: Object.fromEntries(ordered.map((policy) => [policy.id, policy.version])),
      });
      await refreshSelectedScenario();
    } catch (caught) {
      setMessage(errorMessage(caught));
      await refreshSelectedScenario();
    } finally {
      setSaving(false);
    }
  }

  async function togglePolicy(policy: AdminGenericPolicy<AiModelRoutePolicyContent>) {
    setSaving(true);
    setMessage('');
    try {
      await updateAdminPolicy<AiModelRoutePolicyContent>(policy.id, {
        name: policy.name,
        description: policy.description ?? '',
        status: policy.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
        subjectRange: policy.subjectRange,
        content: policy.content,
        version: policy.version,
      });
      await refreshSelectedScenario();
    } catch (caught) {
      setMessage(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  }

  async function deletePolicy(policy: AdminGenericPolicy<AiModelRoutePolicyContent>) {
    if (!window.confirm(`Delete route rule "${policy.name}"?`)) return;
    setSaving(true);
    setMessage('');
    try {
      await deleteAdminPolicy(policy.id, policy.version);
      await refreshSelectedScenario();
      setMessage('Route rule deleted.');
    } catch (caught) {
      setMessage(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  }

  async function refreshSelectedScenario() {
    if (!selected) return;
    await selectScenario(selected);
    try {
      const page = requireApiData(await getAdminAiRoutingScenarios(), 'Routing scenarios failed');
      setScenarios(page.items);
      setSelected(page.items.find((scenario) => scenario.scenarioCode === selected.scenarioCode) ?? selected);
    } catch (caught) {
      setMessage(errorMessage(caught));
    }
  }

  async function simulate() {
    if (!selected || !/^\d+$/.test(userId) || Number(userId) < 1) {
      setMessage('Enter a valid user ID.');
      return;
    }
    setMessage('');
    try {
      setEffective(requireApiData(
        await getAdminAiEffectiveRoute(selected.scenarioCode, Number(userId)),
        'Route simulation failed',
      ));
    } catch (caught) {
      setMessage(errorMessage(caught));
    }
  }

  return <section className="ai-pricing-panel" aria-label="Model routing">
    <div className="ai-panel-heading">
      <div><p className="section-kicker">Runtime</p><h2>Model routing</h2></div>
      <div className="ai-panel-actions">
        <button aria-label="Refresh routes" className="icon-button" disabled={loading || saving} onClick={() => void load()} title="Refresh" type="button"><RefreshCw aria-hidden="true" /></button>
        <button className="secondary-button compact" disabled={!selected || saving} onClick={() => openEditor(null)} type="button"><CirclePlus aria-hidden="true" /><span>New route rule</span></button>
      </div>
    </div>
    {message ? <p className="error-text" role="status">{message}</p> : null}
    <div className="ai-routing-layout">
      <aside aria-label="Routing scenarios">
        {scenarios.map((scenario) => <button className={selected?.scenarioCode === scenario.scenarioCode ? 'ai-routing-scenario selected' : 'ai-routing-scenario'} key={scenario.scenarioCode} onClick={() => void selectScenario(scenario)} type="button">
          <strong>{scenario.displayName}</strong>
          <span>{scenario.enabledPolicyCount ? `${scenario.enabledPolicyCount} enabled` : 'Not configured'}</span>
        </button>)}
      </aside>
      <div className="ai-routing-detail">
        {selected ? <>
          <header className="ai-routing-detail-heading"><div><h3>{selected.displayName}</h3><p>{selected.description}</p></div><small>{selected.policyTypeCode}</small></header>
          <div className="ai-data-table-wrap"><table className="ai-data-table ai-routing-table"><thead><tr><th>Priority</th><th>Rule</th><th>Scope</th><th>Target model</th><th>Effort</th><th>Availability</th><th>Status</th><th>Updated</th><th>Actions</th></tr></thead><tbody>
            {policies.map((policy, index) => <RouteRuleRow
              key={policy.id}
              model={modelFor(policy.content.modelId, models)}
              onDelete={() => void deletePolicy(policy)}
              onEdit={() => openEditor(policy)}
              onMoveDown={() => void movePolicy(index, 1)}
              onMoveUp={() => void movePolicy(index, -1)}
              onToggle={() => void togglePolicy(policy)}
              policy={policy}
              saving={saving}
              canMoveDown={index < policies.length - 1}
              canMoveUp={index > 0}
            />)}
            {!loading && policies.length === 0 ? <tr><td colSpan={9}>No route rules configured for this scenario.</td></tr> : null}
          </tbody></table></div>
          {editing !== undefined ? <section className="ai-routing-editor" aria-label={editing ? 'Edit route rule' : 'New route rule'}>
            <header><h3>{editing ? 'Edit route rule' : 'New route rule'}</h3><button aria-label="Close rule editor" className="icon-button compact" onClick={closeEditor} title="Close" type="button"><X aria-hidden="true" /></button></header>
            <div className="ai-routing-editor-fields">
              <label><span>Rule name</span><input maxLength={120} onChange={(event) => setName(event.target.value)} value={name} /></label>
              <label><span>Priority</span><input inputMode="numeric" min="1" onChange={(event) => setPriority(event.target.value)} type="number" value={priority} /></label>
              <label><span>Target model</span><select onChange={(event) => changeModel(event.target.value)} value={modelId}><option value="">Select a model</option>{models.map((option) => <option key={option.model.id} value={option.model.id}>{modelOptionLabel(option)}</option>)}</select></label>
              <label><span>Reasoning effort</span><select aria-describedby={reasoningEffortError ? 'reasoning-effort-error' : undefined} aria-invalid={Boolean(reasoningEffortError)} aria-label="Reasoning effort" onChange={(event) => { setReasoningEffort(event.target.value ? event.target.value as LlmReasoningEffort : null); setReasoningEffortError(''); }} value={reasoningEffort ?? ''}><option value="">Provider default</option>{reasoningEffortsFor(modelId, models, providerTypes).map((effort) => <option key={effort} value={effort}>{effort}</option>)}</select>{reasoningEffortError ? <small className="error-text" id="reasoning-effort-error" role="alert">{reasoningEffortError}</small> : null}</label>
              <label><span>Status</span><select onChange={(event) => setStatus(event.target.value as GenericPolicyStatus)} value={status}><option value="ENABLED">Enabled</option><option value="DISABLED">Disabled</option></select></label>
              <label className="ai-routing-description"><span>Description</span><input maxLength={500} onChange={(event) => setDescription(event.target.value)} value={description} /></label>
            </div>
            <fieldset className="ai-routing-scope"><legend>Scope</legend><label><input checked={allSubject} name="route-scope" onChange={() => setAllSubject(true)} type="radio" />All users</label><label><input checked={!allSubject} name="route-scope" onChange={() => setAllSubject(false)} type="radio" />Specific users or groups</label>{!allSubject ? <div className="ai-routing-subject-editor"><div><select aria-label="Subject type" onChange={(event) => setSubjectType(event.target.value as PolicySubjectType)} value={subjectType}><option value="USER">User</option><option value="GROUP">Group</option></select><input aria-label="Subject ID" inputMode="numeric" onChange={(event) => setSubjectId(event.target.value)} placeholder="ID" value={subjectId} /><button className="secondary-button compact" onClick={addSubject} type="button">Add</button></div><ul>{subjects.map((subject) => <li key={`${subject.type}-${subject.id}`}><span>{subject.type === 'USER' ? 'User' : 'Group'} #{subject.id}</span><button aria-label={`Remove ${subject.type} ${subject.id}`} className="icon-button compact" onClick={() => removeSubject(subject)} title="Remove" type="button"><Trash2 aria-hidden="true" /></button></li>)}</ul></div> : null}</fieldset>
            <footer><button className="secondary-button" disabled={saving} onClick={closeEditor} type="button">Cancel</button><button className="primary-button" disabled={saving || models.length === 0} onClick={() => void saveRule()} type="button"><Save aria-hidden="true" /><span>{saving ? 'Saving...' : 'Save route rule'}</span></button></footer>
          </section> : null}
          <section className="ai-route-simulation" aria-label="Route simulation"><label>User ID<input inputMode="numeric" onChange={(event) => setUserId(event.target.value)} value={userId} /></label><button className="secondary-button" onClick={() => void simulate()} type="button"><Search aria-hidden="true" /><span>Simulate</span></button>{effective ? <output>{effective.matched ? <><strong>{effective.model?.displayName ?? `Model #${effective.model?.id}`}</strong><span>{effective.matchSource} priority {effective.priority}, effort {formatReasoningEffort(effective.reasoningEffort)}{effective.model?.providerInstanceName ? ` via ${effective.model.providerInstanceName}` : ''}{effective.reason ? ` (${effective.reason})` : ''}</span></> : effective.reason}</output> : null}</section>
        </> : null}
      </div>
    </div>
  </section>;
}

function RouteRuleRow({
  policy,
  model,
  saving,
  canMoveUp,
  canMoveDown,
  onMoveUp,
  onMoveDown,
  onEdit,
  onToggle,
  onDelete,
}: {
  policy: AdminGenericPolicy<AiModelRoutePolicyContent>;
  model: RouteModelOption | undefined;
  saving: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onEdit: () => void;
  onToggle: () => void;
  onDelete: () => void;
}) {
  const available = Boolean(model?.model.enabled && model.provider.enabled);
  return <tr><td>{policy.priority}</td><td><strong>{policy.name}</strong>{policy.description ? <small>{policy.description}</small> : null}</td><td>{formatScope(policy.subjectRange.allSubject, policy.subjectRange.subjects)}</td><td>{model ? <><strong>{model.model.displayName}</strong><small>{model.model.modelId} via {model.provider.name}</small></> : <span className="error-text">Missing model #{policy.content.modelId}</span>}</td><td className="ai-route-effort">{formatReasoningEffort(policy.content.reasoningEffort)}</td><td><span className={available ? 'ai-status-badge active' : 'ai-status-badge inactive'}>{available ? 'Available' : 'Unavailable'}</span></td><td><span className={policy.status === 'ENABLED' ? 'ai-status-badge active' : 'ai-status-badge inactive'}>{policy.status === 'ENABLED' ? 'Enabled' : 'Disabled'}</span></td><td>{formatTimestamp(policy.updatedAt)}</td><td><div className="ai-row-actions"><button aria-label={`Move ${policy.name} up`} className="icon-button compact" disabled={!canMoveUp || saving} onClick={onMoveUp} title="Move up" type="button"><ArrowUp aria-hidden="true" /></button><button aria-label={`Move ${policy.name} down`} className="icon-button compact" disabled={!canMoveDown || saving} onClick={onMoveDown} title="Move down" type="button"><ArrowDown aria-hidden="true" /></button><button aria-label={`Edit ${policy.name}`} className="icon-button compact" disabled={saving} onClick={onEdit} title="Edit" type="button"><Pencil aria-hidden="true" /></button><button aria-label={`${policy.status === 'ENABLED' ? 'Disable' : 'Enable'} ${policy.name}`} className="icon-button compact" disabled={saving} onClick={onToggle} title={policy.status === 'ENABLED' ? 'Disable' : 'Enable'} type="button"><span aria-hidden="true">{policy.status === 'ENABLED' ? '||' : '>'}</span></button><button aria-label={`Delete ${policy.name}`} className="icon-button compact danger-icon-button" disabled={saving} onClick={onDelete} title="Delete" type="button"><Trash2 aria-hidden="true" /></button></div></td></tr>;
}

function modelFor(modelId: number, models: RouteModelOption[]): RouteModelOption | undefined {
  return models.find((option) => option.model.id === modelId);
}

function modelOptionLabel(option: RouteModelOption): string {
  const unavailable = !option.model.enabled || !option.provider.enabled ? ' [unavailable]' : '';
  return `${option.model.displayName} (${option.model.modelId}) - ${option.provider.name}${unavailable}`;
}

function reasoningEffortsFor(
  modelId: string,
  models: RouteModelOption[],
  providerTypes: AdminAiProviderType[],
): LlmReasoningEffort[] {
  const model = models.find((option) => String(option.model.id) === modelId);
  const advertised = providerTypes.find((type) => type.code === model?.provider.providerType)?.reasoningEfforts ?? [];
  return advertised.filter(isKnownReasoningEffort);
}

function reasoningEffortDirectoryError(
  modelId: string,
  models: RouteModelOption[],
  providerTypes: AdminAiProviderType[],
): string {
  const model = models.find((option) => String(option.model.id) === modelId);
  const advertised = providerTypes.find((type) => type.code === model?.provider.providerType)?.reasoningEfforts ?? [];
  return advertised.some((effort) => !isKnownReasoningEffort(effort))
    ? 'The selected provider returned an unknown reasoning effort.'
    : '';
}

function isKnownReasoningEffort(value: unknown): value is LlmReasoningEffort {
  return typeof value === 'string' && LLM_REASONING_EFFORT_VALUES.includes(value as LlmReasoningEffort);
}

function formatReasoningEffort(effort?: LlmReasoningEffort | null): string {
  if (effort === null || effort === undefined) return 'Provider default';
  return isKnownReasoningEffort(effort) ? effort : `Unknown (${String(effort)})`;
}

function formatScope(allSubject: boolean, subjects: PolicySubject[]): string {
  if (allSubject) return 'All users';
  const users = subjects.filter((subject) => subject.type === 'USER').length;
  const groups = subjects.length - users;
  return `Users ${users}, groups ${groups}`;
}

function formatTimestamp(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function errorMessage(error: unknown): string {
  return error instanceof ApiRequestError ? error.message : error instanceof Error ? error.message : 'Request failed';
}
