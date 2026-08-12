import { ArrowDown, ArrowUp, CircleAlert, Pencil, Plus, RefreshCw, Trash2, X } from 'lucide-react';
import { FormEvent, useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import {
  ApiRequestError,
  createAdminPolicy,
  deleteAdminPolicy,
  getAdminPolicies,
  reorderAdminPolicies,
  requireApiData,
  requireApiSuccess,
  updateAdminPolicy,
} from '../../services/api';
import type {
  AdminGenericPolicy,
  AdminGenericPolicyWriteRequest,
  GenericPolicyStatus,
  LearningPlanAiRevisionPolicyContent,
  PolicySubjectRange,
} from '../../types/api';
import PolicySubjectScopeFieldset from '../policies/PolicySubjectScopeFieldset';
import { defaultLearningPlanAiRevisionPolicyContent, LEARNING_PLAN_AI_REVISION_POLICY_TYPE } from './learningPlanAiRevisionPolicy';

type Policy = AdminGenericPolicy<LearningPlanAiRevisionPolicyContent>;

const labels = {
  title: '学习计划 AI 修订策略',
  description: '按优先级控制模板草案、正式计划扩展提案和 AI 个性化草案的修订入口。',
  create: '新建策略', refresh: '刷新策略列表', loading: '正在加载策略...', empty: '尚未配置策略，运行时默认关闭。',
  loadFailed: '策略列表加载失败。', saveFailed: '策略保存失败。', saveSucceeded: '策略已保存。', orderFailed: '策略优先级更新失败。',
  deleteFailed: '策略删除失败。', deleteSucceeded: '策略已删除。', name: '策略名称', descriptionField: '说明', status: '状态',
  enabled: '已启用', disabled: '已停用', scope: '适用范围', allUsers: '全体用户', actions: '操作', priority: '优先级',
  template: '模板草案 AI 修订', saved: '已保存计划 AI 修订（扩展提案）', personalized: 'AI 个性化草案 AI 修订', subjectRequired: '请选择适用主体。',
  save: '保存', cancel: '取消', close: '关闭', deleting: '删除中...', delete: '删除', deleteTitle: '删除策略',
};

export default function LearningPlanAiRevisionPolicyPage() {
  const { locale } = useI18n();
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editing, setEditing] = useState<Policy | null | undefined>(undefined);
  const [deleting, setDeleting] = useState<Policy>();

  useEffect(() => { void load(); }, []);

  async function load() {
    setLoading(true); setError('');
    try {
      const page = requireApiData(await getAdminPolicies<LearningPlanAiRevisionPolicyContent>({ typeCode: LEARNING_PLAN_AI_REVISION_POLICY_TYPE, page: 1, pageSize: 100 }), labels.loadFailed);
      setPolicies([...page.items].sort((a, b) => a.priority - b.priority));
    } catch (caught) { setError(errorMessage(caught, labels.loadFailed)); }
    finally { setLoading(false); }
  }

  async function move(index: number, direction: -1 | 1) {
    const next = index + direction; if (next < 0 || next >= policies.length) return;
    const ordered = [...policies]; const [item] = ordered.splice(index, 1); ordered.splice(next, 0, item);
    try {
      requireApiSuccess(await reorderAdminPolicies(LEARNING_PLAN_AI_REVISION_POLICY_TYPE, { policyIds: ordered.map((p) => p.id), versions: Object.fromEntries(ordered.map((p) => [p.id, p.version])) }), labels.orderFailed);
      setNotice('策略优先级已更新。'); await load();
    } catch (caught) { setError(errorMessage(caught, labels.orderFailed)); await load(); }
  }

  async function confirmDelete() {
    if (!deleting) return;
    try { requireApiData(await deleteAdminPolicy(deleting.id, deleting.version), labels.deleteFailed); setDeleting(undefined); setNotice(labels.deleteSucceeded); await load(); }
    catch (caught) { setError(errorMessage(caught, labels.deleteFailed)); }
  }

  return <section aria-label={labels.title} className="admin-data-page session-policy-page">
    <header className="admin-page-toolbar"><div><h1>{labels.title}</h1><p>{labels.description}</p></div><div className="admin-page-commands"><button className="primary-button compact" onClick={() => setEditing(null)} type="button"><Plus aria-hidden="true" /><span>{labels.create}</span></button><HeaderActionTooltip id="ai-revision-policy-refresh" label={labels.refresh}><button aria-label={labels.refresh} className="icon-button" disabled={loading} onClick={() => void load()} type="button"><RefreshCw aria-hidden="true" /></button></HeaderActionTooltip></div></header>
    {error ? <p className="error-text" role="alert"><CircleAlert aria-hidden="true" />{error}</p> : null}{notice ? <p role="status">{notice}</p> : null}
    <div className="admin-table-wrap"><table className="admin-data-table"><thead><tr><th>{labels.priority}</th><th>{labels.name}</th><th>{labels.scope}</th><th>{labels.template}</th><th>{labels.saved}</th><th>{labels.personalized}</th><th>{labels.status}</th><th>{labels.actions}</th></tr></thead><tbody>
      {loading && policies.length === 0 ? <tr><td colSpan={8}>{labels.loading}</td></tr> : null}{!loading && policies.length === 0 ? <tr><td colSpan={8}>{labels.empty}</td></tr> : null}
      {policies.map((policy, index) => <tr key={policy.id}><td>{policy.priority}</td><td><strong>{policy.name}</strong>{policy.description ? <small>{policy.description}</small> : null}</td><td>{policy.subjectRange.allSubject ? labels.allUsers : `${policy.subjectRange.subjects.length} 个指定主体`}</td><td>{policy.content.templateDraftRevisionEnabled ? '是' : '否'}</td><td>{policy.content.savedPlanRevisionEnabled ? '是' : '否'}</td><td>{policy.content.personalizedDraftRevisionEnabled ? '是' : '否'}</td><td>{policy.status === 'ENABLED' ? labels.enabled : labels.disabled}</td><td><HeaderActionTooltip id={`ai-up-${policy.id}`} label="上移"><button aria-label={`上移策略 ${policy.name}`} className="icon-button compact" disabled={index === 0} onClick={() => void move(index, -1)} type="button"><ArrowUp aria-hidden="true" /></button></HeaderActionTooltip><HeaderActionTooltip id={`ai-down-${policy.id}`} label="下移"><button aria-label={`下移策略 ${policy.name}`} className="icon-button compact" disabled={index === policies.length - 1} onClick={() => void move(index, 1)} type="button"><ArrowDown aria-hidden="true" /></button></HeaderActionTooltip><button aria-label={`编辑策略 ${policy.name}`} className="icon-button compact" onClick={() => setEditing(policy)} type="button"><Pencil aria-hidden="true" /></button><button aria-label={`删除策略 ${policy.name}`} className="icon-button compact danger-icon-button" onClick={() => setDeleting(policy)} type="button"><Trash2 aria-hidden="true" /></button></td></tr>)}
    </tbody></table></div>
    {editing !== undefined ? <PolicyDialog policy={editing ?? undefined} onClose={() => setEditing(undefined)} onSaved={async () => { setEditing(undefined); setNotice(labels.saveSucceeded); await load(); }} /> : null}
    {deleting ? <div className="admin-dialog-backdrop" role="presentation"><section className="admin-dialog compact-dialog" role="dialog"><h2>{labels.deleteTitle}</h2><p>将删除策略“{deleting.name}”。</p><footer><button className="secondary-button" onClick={() => setDeleting(undefined)} type="button">{labels.cancel}</button><button className="danger-button" onClick={() => void confirmDelete()} type="button"><Trash2 aria-hidden="true" /><span>{labels.delete}</span></button></footer></section></div> : null}
  </section>;
}

function PolicyDialog({ policy, onClose, onSaved }: { policy?: Policy; onClose: () => void; onSaved: (policy: Policy) => void }) {
  const initial = policy?.content ?? defaultLearningPlanAiRevisionPolicyContent();
  const [name, setName] = useState(policy?.name ?? ''); const [description, setDescription] = useState(policy?.description ?? ''); const [status, setStatus] = useState<GenericPolicyStatus>(policy?.status ?? 'ENABLED'); const [scope, setScope] = useState<PolicySubjectRange>(policy?.subjectRange ?? { allSubject: true, subjects: [] });
  const [content, setContent] = useState(initial); const [error, setError] = useState(''); const [saving, setSaving] = useState(false);
  async function save(event: FormEvent) { event.preventDefault(); if (!name.trim() || (!scope.allSubject && scope.subjects.length === 0)) { setError('请输入策略名称并选择适用范围。'); return; } setSaving(true); setError(''); const request: AdminGenericPolicyWriteRequest<LearningPlanAiRevisionPolicyContent> = { typeCode: LEARNING_PLAN_AI_REVISION_POLICY_TYPE, name: name.trim(), description: description.trim(), status, subjectRange: scope.allSubject ? { allSubject: true, subjects: [] } : scope, content }; try { const saved = policy ? await updateAdminPolicy<LearningPlanAiRevisionPolicyContent>(policy.id, { ...request, version: policy.version }) : await createAdminPolicy<LearningPlanAiRevisionPolicyContent>(request); onSaved(requireApiData(saved, labels.saveFailed)); } catch (caught) { setError(errorMessage(caught, labels.saveFailed)); } finally { setSaving(false); } }
  return <div className="admin-dialog-backdrop" role="presentation"><form className="admin-dialog session-policy-dialog" role="dialog" aria-modal="true" onSubmit={(e) => void save(e)}><header><div><h2>{policy ? '编辑学习计划 AI 修订策略' : '新建学习计划 AI 修订策略'}</h2><p>策略类型固定为 {LEARNING_PLAN_AI_REVISION_POLICY_TYPE}。</p></div><button aria-label={labels.close} className="icon-button" onClick={onClose} type="button"><X aria-hidden="true" /></button></header>{error ? <p className="error-text" role="alert">{error}</p> : null}<label><span>{labels.name}</span><input value={name} onChange={(e) => setName(e.target.value)} /></label><label><span>{labels.descriptionField}</span><textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} /></label><label><span>{labels.status}</span><select value={status} onChange={(e) => setStatus(e.target.value as GenericPolicyStatus)}><option value="ENABLED">{labels.enabled}</option><option value="DISABLED">{labels.disabled}</option></select></label><fieldset><legend>能力开关</legend><label><input type="checkbox" checked={content.templateDraftRevisionEnabled} onChange={(e) => setContent({ ...content, templateDraftRevisionEnabled: e.target.checked })} />{labels.template}</label><label><input type="checkbox" checked={content.savedPlanRevisionEnabled} onChange={(e) => setContent({ ...content, savedPlanRevisionEnabled: e.target.checked })} />{labels.saved}</label><label><input type="checkbox" checked={content.personalizedDraftRevisionEnabled} onChange={(e) => setContent({ ...content, personalizedDraftRevisionEnabled: e.target.checked })} />{labels.personalized}</label></fieldset><PolicySubjectScopeFieldset labels={{ scope: labels.scope, subjectType: '主体类型', subjectTypes: { USER: '用户', GROUP: '用户组' }, subjectSearchPlaceholder: '搜索用户或用户组', subjectLoading: '加载中...', subjectLoadFailed: '加载失败', savedUserSubject: '用户', savedGroupSubject: '用户组', removeSubject: (value: string) => `移除 ${value}`, remove: '移除', subjectEmpty: '暂无', subjectRequired: labels.subjectRequired, allUsers: labels.allUsers, selectedSubjects: '指定主体' }} name="learning-plan-ai-revision-policy-scope" onChange={setScope} value={scope} /><footer><button className="secondary-button" onClick={onClose} type="button">{labels.cancel}</button><button className="primary-button" disabled={saving} type="submit">{saving ? '保存中...' : labels.save}</button></footer></form></div>;
}

function errorMessage(error: unknown, fallback: string): string { return error instanceof ApiRequestError ? error.message || fallback : error instanceof Error ? error.message : fallback; }
