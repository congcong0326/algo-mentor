import { ArrowDown, ArrowUp, Pencil, Plus, RefreshCw, Save, Search, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import { useI18n } from '../../i18n/I18nProvider';
import type { LocaleResources } from '../../i18n/locales';
import {
  createAdminPolicy,
  deleteAdminPolicy,
  getAdminPolicies,
  getEffectiveSystemPrompt,
  getSystemPromptType,
  getSystemPromptTypes,
  reorderAdminPolicies,
  requireApiData,
  updateAdminPolicy,
} from '../../services/api';
import type {
  AdminGenericPolicy,
  GenericPolicyStatus,
  ManagedSystemPromptPolicyContent,
  PolicySubject,
  PolicySubjectType,
  SystemPromptEffective,
  SystemPromptTypeDetail,
  SystemPromptTypeSummary,
} from '../../types/api';

type PromptPolicy = AdminGenericPolicy<ManagedSystemPromptPolicyContent>;
type EditorMode = PromptPolicy | null | undefined;

export default function SystemPromptManagementPage() {
  const { locale, resources } = useI18n();
  const [types, setTypes] = useState<SystemPromptTypeSummary[]>([]);
  const [selected, setSelected] = useState<SystemPromptTypeSummary>();
  const [detail, setDetail] = useState<SystemPromptTypeDetail>();
  const [policies, setPolicies] = useState<PromptPolicy[]>([]);
  const [editing, setEditing] = useState<EditorMode>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<GenericPolicyStatus>('ENABLED');
  const [allSubject, setAllSubject] = useState(true);
  const [subjects, setSubjects] = useState<PolicySubject[]>([]);
  const [subjectType, setSubjectType] = useState<PolicySubjectType>('USER');
  const [subjectId, setSubjectId] = useState('');
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const [simulationUserId, setSimulationUserId] = useState('');
  const [simulation, setSimulation] = useState<SystemPromptEffective>();
  const [typeKeyword, setTypeKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [deleting, setDeleting] = useState<PromptPolicy>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => { void loadTypes(); }, [locale]);
  useEffect(() => { if (selected) void loadType(selected.typeCode); }, [locale, selected?.typeCode]);

  const categories = useMemo(() => Array.from(new Set(types.map((item) => item.categoryCode))).sort(), [types]);
  const filteredTypes = useMemo(() => {
    const keyword = typeKeyword.trim().toLowerCase();
    return types.filter((item) => (
      (!category || item.categoryCode === category)
      && (!keyword || `${item.displayName} ${item.typeCode} ${item.description}`.toLowerCase().includes(keyword))
    ));
  }, [category, typeKeyword, types]);
  const orderedSections = useMemo(
    () => detail?.sections.slice().sort((left, right) => left.displayOrder - right.displayOrder) ?? [],
    [detail],
  );

  async function loadTypes() {
    setLoading(true);
    setMessage('');
    try {
      const data = requireApiData(await getSystemPromptTypes(), resources.adminSystemPrompts.loadCatalogFailed);
      setTypes(data.items);
      setSelected((current) => data.items.find((item) => item.typeCode === current?.typeCode) ?? data.items[0]);
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.loadCatalogFailed));
    } finally {
      setLoading(false);
    }
  }

  async function loadType(typeCode: string) {
    setLoading(true);
    setMessage('');
    setSimulation(undefined);
    try {
      const [type, policyPage] = await Promise.all([
        getSystemPromptType(typeCode),
        getAdminPolicies<ManagedSystemPromptPolicyContent>({ typeCode, page: 1, pageSize: 100 }),
      ]);
      setDetail(requireApiData(type, resources.adminSystemPrompts.loadDetailFailed));
      setPolicies(requireApiData(policyPage, resources.adminSystemPrompts.loadPoliciesFailed).items);
      closeEditor();
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.loadDetailFailed));
    } finally {
      setLoading(false);
    }
  }

  function openEditor(policy: PromptPolicy | null) {
    setEditing(policy);
    setName(policy?.name ?? resources.adminSystemPrompts.defaultPolicyName(selected?.displayName ?? ''));
    setDescription(policy?.description ?? '');
    setStatus(policy?.status ?? 'ENABLED');
    setAllSubject(policy?.subjectRange.allSubject ?? true);
    setSubjects(policy?.subjectRange.subjects ?? []);
    setOverrides(policy?.content.sectionOverrides ?? {});
    setMessage('');
  }

  function closeEditor() {
    setEditing(undefined);
    setName('');
    setDescription('');
    setStatus('ENABLED');
    setAllSubject(true);
    setSubjects([]);
    setSubjectId('');
    setOverrides({});
  }

  function addSubject() {
    const id = Number(subjectId);
    if (!Number.isSafeInteger(id) || id < 1) {
      setMessage(resources.adminSystemPrompts.invalidSubjectId);
      return;
    }
    if (!subjects.some((subject) => subject.type === subjectType && subject.id === id)) {
      setSubjects((current) => [...current, { type: subjectType, id }]);
    }
    setSubjectId('');
  }

  function removeSubject(subject: PolicySubject) {
    setSubjects((current) => current.filter((candidate) => (
      candidate.type !== subject.type || candidate.id !== subject.id
    )));
  }

  function setSectionOverride(key: string, enabled: boolean, defaultText: string) {
    setOverrides((current) => {
      const next = { ...current };
      if (enabled) next[key] = defaultText;
      else delete next[key];
      return next;
    });
  }

  async function save() {
    if (!selected || editing === undefined || saving) return;
    if (!name.trim()) {
      setMessage(resources.adminSystemPrompts.nameRequired);
      return;
    }
    if (!allSubject && subjects.length === 0) {
      setMessage(resources.adminSystemPrompts.subjectRequired);
      return;
    }
    setSaving(true);
    setMessage('');
    const request = {
      typeCode: selected.typeCode,
      name: name.trim(),
      description: description.trim(),
      status,
      subjectRange: { allSubject, subjects: allSubject ? [] : subjects },
      content: { sectionOverrides: overrides },
    };
    try {
      if (editing) {
        await updateAdminPolicy<ManagedSystemPromptPolicyContent>(editing.id, { ...request, version: editing.version });
      } else {
        await createAdminPolicy<ManagedSystemPromptPolicyContent>(request);
      }
      await loadType(selected.typeCode);
      setMessage(resources.adminSystemPrompts.saved);
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.saveFailed));
    } finally {
      setSaving(false);
    }
  }

  async function togglePolicy(policy: PromptPolicy) {
    try {
      await updateAdminPolicy<ManagedSystemPromptPolicyContent>(policy.id, {
        name: policy.name,
        description: policy.description ?? '',
        status: policy.status === 'ENABLED' ? 'DISABLED' : 'ENABLED',
        subjectRange: policy.subjectRange,
        content: policy.content,
        version: policy.version,
      });
      await loadType(policy.typeCode);
      setMessage(policy.status === 'ENABLED'
        ? resources.adminSystemPrompts.disabledSuccess
        : resources.adminSystemPrompts.enabledSuccess);
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.statusUpdateFailed));
    }
  }

  async function confirmDelete() {
    if (!deleting) return;
    try {
      await deleteAdminPolicy(deleting.id, deleting.version);
      setDeleting(undefined);
      await loadType(deleting.typeCode);
      setMessage(resources.adminSystemPrompts.deleted);
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.deleteFailed));
    }
  }

  async function movePolicy(index: number, direction: -1 | 1) {
    if (!selected || index + direction < 0 || index + direction >= policies.length) return;
    const ordered = [...policies];
    const [moving] = ordered.splice(index, 1);
    ordered.splice(index + direction, 0, moving);
    setPolicies(ordered);
    try {
      await reorderAdminPolicies(selected.typeCode, {
        policyIds: ordered.map((policy) => policy.id),
        versions: Object.fromEntries(ordered.map((policy) => [policy.id, policy.version])),
      });
      await loadType(selected.typeCode);
      setMessage(resources.adminSystemPrompts.priorityUpdated);
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.priorityUpdateFailed));
      await loadType(selected.typeCode);
    }
  }

  async function simulate() {
    if (!selected || !/^\d+$/.test(simulationUserId) || Number(simulationUserId) < 1) {
      setMessage(resources.adminSystemPrompts.invalidUserId);
      return;
    }
    try {
      setSimulation(requireApiData(
        await getEffectiveSystemPrompt(selected.typeCode, Number(simulationUserId)),
        resources.adminSystemPrompts.simulationFailed,
      ));
    } catch (error) {
      setMessage(errorMessage(error, resources.adminSystemPrompts.simulationFailed));
    }
  }

  const promptResources = resources.adminSystemPrompts;
  const subjectLabel = (type: PolicySubjectType) => type === 'USER' ? promptResources.user : promptResources.group;

  return (
    <section aria-label={promptResources.ariaLabel} className="admin-data-page system-prompt-page">
      <header className="admin-page-toolbar">
        <div><h1>{promptResources.title}</h1><p>{promptResources.description}</p></div>
        <div className="admin-page-commands">
          <button className="primary-button compact" disabled={!selected} onClick={() => openEditor(null)} type="button">
            <Plus aria-hidden="true" /><span>{promptResources.createPolicy}</span>
          </button>
          <HeaderActionTooltip id="system-prompt-refresh" label={promptResources.refresh}>
            <button aria-describedby="system-prompt-refresh" aria-label={promptResources.refresh} className="icon-button" disabled={loading} onClick={() => void loadTypes()} type="button">
              <RefreshCw aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
        </div>
      </header>
      {message ? <p className="error-text system-prompt-message" role="status">{message}</p> : null}
      <div className="system-prompt-workspace">
        <aside className="system-prompt-catalog">
          <label className="system-prompt-search">
            <Search aria-hidden="true" />
            <span className="visually-hidden">{promptResources.searchTypes}</span>
            <input aria-label={promptResources.searchTypes} onChange={(event) => setTypeKeyword(event.target.value)} placeholder={promptResources.searchPlaceholder} value={typeKeyword} />
          </label>
          <label>
            <span>{promptResources.category}</span>
            <select aria-label={promptResources.category} onChange={(event) => setCategory(event.target.value)} value={category}>
              <option value="">{promptResources.allCategories}</option>
              {categories.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <nav aria-label={promptResources.typeNavigation}>
            {filteredTypes.map((item) => (
              <button className={item.typeCode === selected?.typeCode ? 'selected' : ''} key={item.typeCode} onClick={() => setSelected(item)} type="button">
                <strong>{item.displayName}</strong><small>{item.typeCode}</small>
                <span>{item.livePolicyCount ? promptResources.policyCount(item.livePolicyCount) : promptResources.codeDefault}</span>
              </button>
            ))}
          </nav>
        </aside>
        <main className="system-prompt-detail">
          {loading && !detail ? <p>{promptResources.loading}</p> : null}
          {detail && selected ? (
            <>
              <header className="system-prompt-detail-header">
                <div><h2>{selected.displayName}</h2><p>{selected.description}</p><code>{detail.typeCode}</code></div>
                <dl>
                  <div><dt>{promptResources.codeRevision}</dt><dd>{detail.sourceRevision}</dd></div>
                  <div><dt>{promptResources.snapshotScope}</dt><dd>{detail.snapshotScope}</dd></div>
                </dl>
              </header>
              <section className="system-prompt-policy-list" aria-labelledby="system-prompt-policies-title">
                <h3 id="system-prompt-policies-title">{promptResources.policies}</h3>
                <div className="admin-table-wrap">
                  <table className="admin-data-table">
                    <thead><tr><th>{promptResources.priority}</th><th>{promptResources.name}</th><th>{promptResources.scope}</th><th>{promptResources.status}</th><th>{promptResources.actions}</th></tr></thead>
                    <tbody>
                      {policies.length ? policies.map((policy, index) => (
                        <tr key={policy.id}>
                          <td>{policy.priority}</td>
                          <td><strong>{policy.name}</strong>{policy.description ? <small>{policy.description}</small> : null}</td>
                          <td>{formatScope(policy.subjectRange.allSubject, policy.subjectRange.subjects, promptResources)}</td>
                          <td><span className={`admin-status-badge ${policy.status.toLowerCase()}`}>{policy.status === 'ENABLED' ? promptResources.enabled : promptResources.disabled}</span></td>
                          <td>
                            <div className="admin-row-actions">
                              <HeaderActionTooltip id={`system-prompt-up-${policy.id}`} label={promptResources.moveUp}>
                                <button aria-describedby={`system-prompt-up-${policy.id}`} aria-label={promptResources.moveUp} className="icon-button compact" disabled={index === 0} onClick={() => void movePolicy(index, -1)} type="button"><ArrowUp aria-hidden="true" /></button>
                              </HeaderActionTooltip>
                              <HeaderActionTooltip id={`system-prompt-down-${policy.id}`} label={promptResources.moveDown}>
                                <button aria-describedby={`system-prompt-down-${policy.id}`} aria-label={promptResources.moveDown} className="icon-button compact" disabled={index === policies.length - 1} onClick={() => void movePolicy(index, 1)} type="button"><ArrowDown aria-hidden="true" /></button>
                              </HeaderActionTooltip>
                              <HeaderActionTooltip id={`system-prompt-edit-${policy.id}`} label={promptResources.edit}>
                                <button aria-describedby={`system-prompt-edit-${policy.id}`} aria-label={promptResources.edit} className="icon-button compact" onClick={() => openEditor(policy)} type="button"><Pencil aria-hidden="true" /></button>
                              </HeaderActionTooltip>
                              <button className="secondary-button compact" onClick={() => void togglePolicy(policy)} type="button">
                                {policy.status === 'ENABLED' ? promptResources.disabled : promptResources.enabled}
                              </button>
                              <HeaderActionTooltip id={`system-prompt-delete-${policy.id}`} label={promptResources.delete}>
                                <button aria-describedby={`system-prompt-delete-${policy.id}`} aria-label={promptResources.delete} className="icon-button compact danger-icon-button" onClick={() => setDeleting(policy)} type="button"><Trash2 aria-hidden="true" /></button>
                              </HeaderActionTooltip>
                            </div>
                          </td>
                        </tr>
                      )) : <tr><td className="system-prompt-empty" colSpan={5}>{promptResources.emptyPolicies}</td></tr>}
                    </tbody>
                  </table>
                </div>
              </section>
              <section className="system-prompt-sections" aria-labelledby="system-prompt-sections-title">
                <header>
                  <h3 id="system-prompt-sections-title">
                    {editing === undefined ? promptResources.defaultSections : editing ? promptResources.editOverrides : promptResources.createOverrides}
                  </h3>
                  {editing !== undefined ? <button className="secondary-button compact" disabled={saving} onClick={closeEditor} type="button">{promptResources.cancel}</button> : null}
                </header>
                {editing !== undefined ? (
                  <div className="system-prompt-policy-form">
                    <label><span>{promptResources.policyName}</span><input maxLength={120} onChange={(event) => setName(event.target.value)} value={name} /></label>
                    <label><span>{promptResources.policyDescription}</span><input maxLength={500} onChange={(event) => setDescription(event.target.value)} value={description} /></label>
                    <label>
                      <span>{promptResources.status}</span>
                      <select onChange={(event) => setStatus(event.target.value as GenericPolicyStatus)} value={status}>
                        <option value="ENABLED">{promptResources.enabled}</option><option value="DISABLED">{promptResources.disabled}</option>
                      </select>
                    </label>
                    <fieldset>
                      <legend>{promptResources.effectiveScope}</legend>
                      <label><input checked={allSubject} name="system-prompt-scope" onChange={() => setAllSubject(true)} type="radio" />{promptResources.allUsers}</label>
                      <label><input checked={!allSubject} name="system-prompt-scope" onChange={() => setAllSubject(false)} type="radio" />{promptResources.selectedSubjects}</label>
                      {!allSubject ? (
                        <div className="system-prompt-subject-editor">
                          <div>
                            <select aria-label={promptResources.subjectType} onChange={(event) => setSubjectType(event.target.value as PolicySubjectType)} value={subjectType}>
                              <option value="USER">{promptResources.user}</option><option value="GROUP">{promptResources.group}</option>
                            </select>
                            <input aria-label={promptResources.subjectId} inputMode="numeric" onChange={(event) => setSubjectId(event.target.value)} placeholder="ID" value={subjectId} />
                            <button className="secondary-button compact" onClick={addSubject} type="button">{promptResources.add}</button>
                          </div>
                          <ul>
                            {subjects.map((subject) => (
                              <li key={`${subject.type}-${subject.id}`}>
                                <span>{promptResources.subjectLabel(subjectLabel(subject.type), subject.id)}</span>
                                <button aria-label={promptResources.removeSubject(subjectLabel(subject.type), subject.id)} className="icon-button compact" onClick={() => removeSubject(subject)} type="button"><Trash2 aria-hidden="true" /></button>
                              </li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                    </fieldset>
                  </div>
                ) : null}
                {orderedSections.map((section) => {
                  const overridden = Object.hasOwn(overrides, section.key);
                  const text = overridden ? overrides[section.key] : section.defaultText;
                  return (
                    <article className="system-prompt-section" key={section.key}>
                      <header>
                        <div><h4>{section.displayName}</h4><p>{section.description}</p><code>{section.key}</code></div>
                        {editing !== undefined ? (
                          <label className="system-prompt-override-toggle">
                            <input checked={overridden} onChange={(event) => setSectionOverride(section.key, event.target.checked, section.defaultText)} type="checkbox" />
                            {promptResources.overrideSection}
                          </label>
                        ) : null}
                      </header>
                      <textarea aria-label={section.displayName} disabled={editing === undefined || !overridden} maxLength={section.maxLength} onChange={(event) => setOverrides((current) => ({ ...current, [section.key]: event.target.value }))} value={text} />
                      <footer>
                        <span>{promptResources.characterCount(text.length, section.maxLength)}</span>
                        <span>{overridden ? promptResources.databaseOverride : promptResources.codeDefault}</span>
                        {editing !== undefined && overridden ? (
                          <button className="secondary-button compact" onClick={() => setSectionOverride(section.key, false, section.defaultText)} type="button">{promptResources.restoreDefault}</button>
                        ) : null}
                      </footer>
                    </article>
                  );
                })}
                {editing !== undefined ? (
                  <footer className="system-prompt-save">
                    <button className="primary-button" disabled={saving} onClick={() => void save()} type="button">
                      <Save aria-hidden="true" /><span>{saving ? promptResources.saving : promptResources.savePolicy}</span>
                    </button>
                  </footer>
                ) : null}
              </section>
              <section className="system-prompt-simulation" aria-labelledby="system-prompt-simulation-title">
                <h3 id="system-prompt-simulation-title">{promptResources.simulateByUser}</h3>
                <div>
                  <input aria-label={promptResources.userId} inputMode="numeric" onChange={(event) => setSimulationUserId(event.target.value)} placeholder={promptResources.userId} value={simulationUserId} />
                  <button className="secondary-button compact" onClick={() => void simulate()} type="button"><Search aria-hidden="true" /><span>{promptResources.simulate}</span></button>
                </div>
                {simulation ? (
                  <div className="system-prompt-simulation-result">
                    <p>{promptResources.simulationSummary(
                      simulation.resolutionSource,
                      String(simulation.policyId ?? promptResources.codeDefault),
                      simulation.matchSource,
                    )}</p>
                    {simulation.sections.map((section) => (
                      <details key={section.key}>
                        <summary>{section.key} <span>{promptResources.simulationSectionSummary(section.source, section.charCount)}</span></summary>
                        <pre>{section.text}</pre>
                      </details>
                    ))}
                  </div>
                ) : null}
              </section>
            </>
          ) : null}
        </main>
      </div>
      {deleting ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section aria-labelledby="delete-system-prompt-policy-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog">
            <h2 id="delete-system-prompt-policy-title">{promptResources.deleteTitle}</h2>
            <p>{promptResources.deleteDescription(deleting.name)}</p>
            <footer>
              <button className="secondary-button" onClick={() => setDeleting(undefined)} type="button">{promptResources.cancel}</button>
              <button className="danger-button" onClick={() => void confirmDelete()} type="button"><Trash2 aria-hidden="true" /><span>{promptResources.delete}</span></button>
            </footer>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function formatScope(
  allSubject: boolean,
  subjects: PolicySubject[],
  resources: LocaleResources['adminSystemPrompts'],
): string {
  if (allSubject) return resources.allUsers;
  const users = subjects.filter((subject) => subject.type === 'USER').length;
  const groups = subjects.length - users;
  return resources.scopeSummary(users, groups);
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
