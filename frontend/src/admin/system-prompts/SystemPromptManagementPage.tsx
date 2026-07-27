import { ArrowDown, ArrowUp, Pencil, Plus, RefreshCw, Save, Search, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
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

  useEffect(() => { void loadTypes(); }, []);
  useEffect(() => { if (selected) void loadType(selected.typeCode); }, [selected?.typeCode]);

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
      const data = requireApiData(await getSystemPromptTypes(), '无法加载系统提示词目录。');
      setTypes(data.items);
      setSelected((current) => data.items.find((item) => item.typeCode === current?.typeCode) ?? data.items[0]);
    } catch (error) {
      setMessage(errorMessage(error, '无法加载系统提示词目录。'));
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
      setDetail(requireApiData(type, '无法加载提示词详情。'));
      setPolicies(requireApiData(policyPage, '无法加载策略。').items);
      closeEditor();
    } catch (error) {
      setMessage(errorMessage(error, '无法加载提示词详情。'));
    } finally {
      setLoading(false);
    }
  }

  function openEditor(policy: PromptPolicy | null) {
    setEditing(policy);
    setName(policy?.name ?? `${selected?.displayName ?? ''} 配置`);
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
      setMessage('请输入有效的用户或用户组 ID。');
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
      setMessage('策略名称不能为空。');
      return;
    }
    if (!allSubject && subjects.length === 0) {
      setMessage('指定范围至少需要一个用户或用户组。');
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
      setMessage('策略已保存。');
    } catch (error) {
      setMessage(errorMessage(error, '无法保存策略。'));
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
      setMessage(policy.status === 'ENABLED' ? '策略已禁用。' : '策略已启用。');
    } catch (error) {
      setMessage(errorMessage(error, '无法更新策略状态。'));
    }
  }

  async function confirmDelete() {
    if (!deleting) return;
    try {
      await deleteAdminPolicy(deleting.id, deleting.version);
      setDeleting(undefined);
      await loadType(deleting.typeCode);
      setMessage('策略已删除。');
    } catch (error) {
      setMessage(errorMessage(error, '无法删除策略。'));
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
      setMessage('策略优先级已更新。');
    } catch (error) {
      setMessage(errorMessage(error, '无法更新策略优先级。'));
      await loadType(selected.typeCode);
    }
  }

  async function simulate() {
    if (!selected || !/^\d+$/.test(simulationUserId) || Number(simulationUserId) < 1) {
      setMessage('请输入有效的用户 ID。');
      return;
    }
    try {
      setSimulation(requireApiData(
        await getEffectiveSystemPrompt(selected.typeCode, Number(simulationUserId)),
        '无法模拟策略命中。',
      ));
    } catch (error) {
      setMessage(errorMessage(error, '无法模拟策略命中。'));
    }
  }

  return <section aria-label="系统提示词" className="admin-data-page system-prompt-page">
    <header className="admin-page-toolbar">
      <div><h1>系统提示词</h1><p>代码默认值始终可用，管理员策略仅保存 section 覆盖项。</p></div>
      <div className="admin-page-commands">
        <button className="primary-button compact" disabled={!selected} onClick={() => openEditor(null)} type="button"><Plus aria-hidden="true" /><span>新建策略</span></button>
        <HeaderActionTooltip id="system-prompt-refresh" label="刷新">
          <button aria-describedby="system-prompt-refresh" aria-label="刷新" className="icon-button" disabled={loading} onClick={() => void loadTypes()} type="button"><RefreshCw aria-hidden="true" /></button>
        </HeaderActionTooltip>
      </div>
    </header>
    {message ? <p className="error-text system-prompt-message" role="status">{message}</p> : null}
    <div className="system-prompt-workspace">
      <aside className="system-prompt-catalog">
        <label className="system-prompt-search"><Search aria-hidden="true" /><span className="visually-hidden">搜索提示词类型</span><input aria-label="搜索提示词类型" onChange={(event) => setTypeKeyword(event.target.value)} placeholder="搜索类型" value={typeKeyword} /></label>
        <label><span>分类</span><select aria-label="分类" onChange={(event) => setCategory(event.target.value)} value={category}><option value="">全部分类</option>{categories.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        <nav aria-label="提示词类型">{filteredTypes.map((item) => <button className={item.typeCode === selected?.typeCode ? 'selected' : ''} key={item.typeCode} onClick={() => setSelected(item)} type="button"><strong>{item.displayName}</strong><small>{item.typeCode}</small><span>{item.livePolicyCount ? `${item.livePolicyCount} 条策略` : '代码默认'}</span></button>)}</nav>
      </aside>
      <main className="system-prompt-detail">
        {loading && !detail ? <p>加载中...</p> : null}
        {detail && selected ? <>
          <header className="system-prompt-detail-header"><div><h2>{selected.displayName}</h2><p>{selected.description}</p><code>{detail.typeCode}</code></div><dl><div><dt>代码 revision</dt><dd>{detail.sourceRevision}</dd></div><div><dt>快照范围</dt><dd>{detail.snapshotScope}</dd></div></dl></header>
          <section className="system-prompt-policy-list" aria-labelledby="system-prompt-policies-title"><h3 id="system-prompt-policies-title">管理员策略</h3><div className="admin-table-wrap"><table className="admin-data-table"><thead><tr><th>优先级</th><th>名称</th><th>范围</th><th>状态</th><th>操作</th></tr></thead><tbody>{policies.length ? policies.map((policy, index) => <tr key={policy.id}><td>{policy.priority}</td><td><strong>{policy.name}</strong>{policy.description ? <small>{policy.description}</small> : null}</td><td>{formatScope(policy.subjectRange.allSubject, policy.subjectRange.subjects)}</td><td><span className={`admin-status-badge ${policy.status.toLowerCase()}`}>{policy.status === 'ENABLED' ? '启用' : '禁用'}</span></td><td><div className="admin-row-actions"><HeaderActionTooltip id={`system-prompt-up-${policy.id}`} label="上移"><button aria-describedby={`system-prompt-up-${policy.id}`} aria-label="上移" className="icon-button compact" disabled={index === 0} onClick={() => void movePolicy(index, -1)} type="button"><ArrowUp aria-hidden="true" /></button></HeaderActionTooltip><HeaderActionTooltip id={`system-prompt-down-${policy.id}`} label="下移"><button aria-describedby={`system-prompt-down-${policy.id}`} aria-label="下移" className="icon-button compact" disabled={index === policies.length - 1} onClick={() => void movePolicy(index, 1)} type="button"><ArrowDown aria-hidden="true" /></button></HeaderActionTooltip><HeaderActionTooltip id={`system-prompt-edit-${policy.id}`} label="编辑"><button aria-describedby={`system-prompt-edit-${policy.id}`} aria-label="编辑" className="icon-button compact" onClick={() => openEditor(policy)} type="button"><Pencil aria-hidden="true" /></button></HeaderActionTooltip><button className="secondary-button compact" onClick={() => void togglePolicy(policy)} type="button">{policy.status === 'ENABLED' ? '禁用' : '启用'}</button><HeaderActionTooltip id={`system-prompt-delete-${policy.id}`} label="删除"><button aria-describedby={`system-prompt-delete-${policy.id}`} aria-label="删除" className="icon-button compact danger-icon-button" onClick={() => setDeleting(policy)} type="button"><Trash2 aria-hidden="true" /></button></HeaderActionTooltip></div></td></tr>) : <tr><td className="system-prompt-empty" colSpan={5}>尚未配置，运行时使用代码默认值。</td></tr>}</tbody></table></div></section>
          <section className="system-prompt-sections" aria-labelledby="system-prompt-sections-title"><header><h3 id="system-prompt-sections-title">{editing === undefined ? '代码默认 section' : editing ? '编辑策略覆盖' : '新建策略覆盖'}</h3>{editing !== undefined ? <button className="secondary-button compact" disabled={saving} onClick={closeEditor} type="button">取消</button> : null}</header>{editing !== undefined ? <div className="system-prompt-policy-form"><label><span>策略名称</span><input maxLength={120} onChange={(event) => setName(event.target.value)} value={name} /></label><label><span>说明</span><input maxLength={500} onChange={(event) => setDescription(event.target.value)} value={description} /></label><label><span>状态</span><select onChange={(event) => setStatus(event.target.value as GenericPolicyStatus)} value={status}><option value="ENABLED">启用</option><option value="DISABLED">禁用</option></select></label><fieldset><legend>生效范围</legend><label><input checked={allSubject} name="system-prompt-scope" onChange={() => setAllSubject(true)} type="radio" />全部用户</label><label><input checked={!allSubject} name="system-prompt-scope" onChange={() => setAllSubject(false)} type="radio" />指定用户或用户组</label>{!allSubject ? <div className="system-prompt-subject-editor"><div><select aria-label="主体类型" onChange={(event) => setSubjectType(event.target.value as PolicySubjectType)} value={subjectType}><option value="USER">用户</option><option value="GROUP">用户组</option></select><input aria-label="主体 ID" inputMode="numeric" onChange={(event) => setSubjectId(event.target.value)} placeholder="ID" value={subjectId} /><button className="secondary-button compact" onClick={addSubject} type="button">添加</button></div><ul>{subjects.map((subject) => <li key={`${subject.type}-${subject.id}`}><span>{subject.type === 'USER' ? '用户' : '用户组'} #{subject.id}</span><button aria-label={`移除 ${subject.type} ${subject.id}`} className="icon-button compact" onClick={() => removeSubject(subject)} type="button"><Trash2 aria-hidden="true" /></button></li>)}</ul></div> : null}</fieldset></div> : null}{orderedSections.map((section) => { const overridden = Object.hasOwn(overrides, section.key); return <article className="system-prompt-section" key={section.key}><header><div><h4>{section.displayName}</h4><p>{section.description}</p><code>{section.key}</code></div>{editing !== undefined ? <label className="system-prompt-override-toggle"><input checked={overridden} onChange={(event) => setSectionOverride(section.key, event.target.checked, section.defaultText)} type="checkbox" />覆盖此 section</label> : null}</header><textarea aria-label={section.displayName} disabled={editing === undefined || !overridden} maxLength={section.maxLength} onChange={(event) => setOverrides((current) => ({ ...current, [section.key]: event.target.value }))} value={overridden ? overrides[section.key] : section.defaultText} /><footer><span>{(overridden ? overrides[section.key] : section.defaultText).length} / {section.maxLength} 字符</span><span>{overridden ? '数据库覆盖' : '代码默认'}</span>{editing !== undefined && overridden ? <button className="secondary-button compact" onClick={() => setSectionOverride(section.key, false, section.defaultText)} type="button">恢复代码默认</button> : null}</footer></article>; })}{editing !== undefined ? <footer className="system-prompt-save"><button className="primary-button" disabled={saving} onClick={() => void save()} type="button"><Save aria-hidden="true" /><span>{saving ? '保存中...' : '保存策略'}</span></button></footer> : null}</section>
          <section className="system-prompt-simulation" aria-labelledby="system-prompt-simulation-title"><h3 id="system-prompt-simulation-title">按用户模拟</h3><div><input aria-label="用户 ID" inputMode="numeric" onChange={(event) => setSimulationUserId(event.target.value)} placeholder="用户 ID" value={simulationUserId} /><button className="secondary-button compact" onClick={() => void simulate()} type="button"><Search aria-hidden="true" /><span>模拟</span></button></div>{simulation ? <div className="system-prompt-simulation-result"><p>来源：{simulation.resolutionSource}；命中策略：{simulation.policyId ?? '代码默认'}{simulation.matchSource ? `（${simulation.matchSource}）` : ''}</p>{simulation.sections.map((section) => <details key={section.key}><summary>{section.key} <span>{section.source}，{section.charCount} 字符</span></summary><pre>{section.text}</pre></details>)}</div> : null}</section>
        </> : null}
      </main>
    </div>
    {deleting ? <div className="admin-dialog-backdrop" role="presentation"><section aria-labelledby="delete-system-prompt-policy-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog"><h2 id="delete-system-prompt-policy-title">删除策略</h2><p>删除“{deleting.name}”后，受影响用户会重新匹配下一条策略或使用代码默认值。</p><footer><button className="secondary-button" onClick={() => setDeleting(undefined)} type="button">取消</button><button className="danger-button" onClick={() => void confirmDelete()} type="button"><Trash2 aria-hidden="true" /><span>删除</span></button></footer></section></div> : null}
  </section>;
}

function formatScope(allSubject: boolean, subjects: PolicySubject[]): string {
  if (allSubject) return '全部用户';
  const users = subjects.filter((subject) => subject.type === 'USER').length;
  const groups = subjects.length - users;
  return `用户 ${users}，用户组 ${groups}`;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
