import { ArrowDown, ArrowUp, CircleAlert, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  deleteAdminPolicy,
  getAdminPolicies,
  reorderAdminPolicies,
  requireApiData,
  requireApiSuccess,
} from '../../services/api';
import type { AdminGenericPolicy, LearningPlanCreationPolicyContent, PolicySubjectRange } from '../../types/api';
import LearningPlanPolicyDialog from './LearningPlanPolicyDialog';
import { LEARNING_PLAN_CREATION_POLICY_TYPE } from './learningPlanPolicy';

type LearningPlanPolicy = AdminGenericPolicy<LearningPlanCreationPolicyContent>;

export default function LearningPlanPolicyPage() {
  const { locale, resources } = useI18n();
  const t = resources.learningPlanPolicy;
  const [policies, setPolicies] = useState<LearningPlanPolicy[]>([]);
  const [loading, setLoading] = useState(true);
  const [ordering, setOrdering] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editingPolicy, setEditingPolicy] = useState<LearningPlanPolicy | null | undefined>(undefined);
  const [deletingPolicy, setDeletingPolicy] = useState<LearningPlanPolicy>();
  const abortRef = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    void load();
    return () => abortRef.current?.abort();
  }, []);

  async function load() {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError('');
    try {
      const page = requireApiData(await getAdminPolicies<LearningPlanCreationPolicyContent>({
        typeCode: LEARNING_PLAN_CREATION_POLICY_TYPE,
        page: 1,
        pageSize: 100,
      }, controller.signal), t.loadFailed);
      if (!controller.signal.aborted) {
        setPolicies([...page.items].sort((left, right) => left.priority - right.priority));
      }
    } catch (caught) {
      if (!controller.signal.aborted) {
        setError(errorMessage(caught, t.loadFailed));
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
      }
    }
  }

  async function movePolicy(index: number, direction: -1 | 1) {
    const destination = index + direction;
    if (ordering || destination < 0 || destination >= policies.length) {
      return;
    }
    const ordered = [...policies];
    const [moving] = ordered.splice(index, 1);
    ordered.splice(destination, 0, moving);
    setOrdering(true);
    setError('');
    setNotice('');
    try {
      requireApiSuccess(await reorderAdminPolicies(LEARNING_PLAN_CREATION_POLICY_TYPE, {
        policyIds: ordered.map((policy) => policy.id),
        versions: Object.fromEntries(ordered.map((policy) => [policy.id, policy.version])),
      }), t.orderFailed);
      setNotice(t.orderSucceeded);
      await load();
    } catch (caught) {
      const message = errorMessage(caught, t.orderFailed);
      await load();
      setError(message);
    } finally {
      setOrdering(false);
    }
  }

  async function confirmDelete() {
    if (!deletingPolicy || deleting) {
      return;
    }
    setDeleting(true);
    setError('');
    setNotice('');
    try {
      requireApiData(await deleteAdminPolicy(deletingPolicy.id, deletingPolicy.version), t.deleteFailed);
      setDeletingPolicy(undefined);
      setNotice(t.deleteSucceeded);
      await load();
    } catch (caught) {
      setError(errorMessage(caught, t.deleteFailed));
    } finally {
      setDeleting(false);
    }
  }

  function saved() {
    setEditingPolicy(undefined);
    setNotice(t.saveSucceeded);
    void load();
  }

  return (
    <section aria-label={t.ariaLabel} className="admin-data-page session-policy-page learning-plan-policy-page">
      <header className="admin-page-toolbar">
        <div>
          <h1>{t.title}</h1>
          <p className="session-policy-page-description">{t.pageDescription}</p>
        </div>
        <div className="admin-page-commands">
          <button className="primary-button compact" onClick={() => setEditingPolicy(null)} type="button">
            <Plus aria-hidden="true" />
            <span>{t.create}</span>
          </button>
          <HeaderActionTooltip id="learning-plan-policy-refresh-tooltip" label={t.refresh}>
            <button aria-describedby="learning-plan-policy-refresh-tooltip" aria-label={t.refresh} className="icon-button" disabled={loading} onClick={() => void load()} type="button">
              <RefreshCw aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
        </div>
      </header>

      {error ? <p className="error-text session-policy-message" role="alert"><CircleAlert aria-hidden="true" />{error}</p> : null}
      {notice ? <p className="session-policy-notice" role="status">{notice}</p> : null}

      <div className="admin-table-wrap">
        <table className="admin-data-table session-policy-table learning-plan-policy-table">
          <thead>
            <tr>
              <th>{t.priority}</th>
              <th>{t.name}</th>
              <th>{t.scope}</th>
              <th>{t.maxSavedPlans}</th>
              <th>{t.dailyDraftCreationLimit}</th>
              <th>{t.draftRetentionDays}</th>
              <th>{t.status}</th>
              <th>{t.updatedAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {loading && policies.length === 0 ? <tr><td className="session-policy-empty" colSpan={9}>{t.loading}</td></tr> : null}
            {!loading && policies.length === 0 ? <tr><td className="session-policy-empty" colSpan={9}>{t.empty}</td></tr> : null}
            {policies.map((policy, index) => <PolicyRow index={index} key={policy.id} policy={policy} />)}
          </tbody>
        </table>
      </div>

      {editingPolicy !== undefined ? (
        <LearningPlanPolicyDialog
          policy={editingPolicy ?? undefined}
          onClose={() => setEditingPolicy(undefined)}
          onSaved={saved}
        />
      ) : null}
      {deletingPolicy ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section aria-labelledby="delete-learning-plan-policy-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog">
            <h2 id="delete-learning-plan-policy-title">{t.deleteTitle}</h2>
            <p>{t.deleteDescription(deletingPolicy.name)}</p>
            <footer>
              <button className="secondary-button" disabled={deleting} onClick={() => setDeletingPolicy(undefined)} type="button">{resources.common.cancel}</button>
              <button className="danger-button" disabled={deleting} onClick={() => void confirmDelete()} type="button"><Trash2 aria-hidden="true" /><span>{deleting ? t.deleting : t.delete}</span></button>
            </footer>
          </section>
        </div>
      ) : null}
    </section>
  );

  function PolicyRow({ policy, index }: { policy: LearningPlanPolicy; index: number }) {
    const moveUpTooltipId = `learning-plan-policy-up-${policy.id}`;
    const moveDownTooltipId = `learning-plan-policy-down-${policy.id}`;
    const editTooltipId = `learning-plan-policy-edit-${policy.id}`;
    const deleteTooltipId = `learning-plan-policy-delete-${policy.id}`;
    return (
      <tr>
        <td>{policy.priority}</td>
        <td>
          <strong>{policy.name}</strong>
          {policy.description ? <small className="session-policy-description">{policy.description}</small> : null}
        </td>
        <td>{formatScope(policy.subjectRange)}</td>
        <td>{formatNumber(policy.content.maxSavedPlans)}</td>
        <td>{formatNumber(policy.content.dailyDraftCreationLimit)}</td>
        <td>{t.retentionValue(formatNumber(policy.content.draftRetentionDays))}</td>
        <td><span className={`admin-status-badge ${policy.status.toLowerCase()}`}>{t.statuses[policy.status]}</span></td>
        <td>{formatDateTime(policy.updatedAt, locale)}</td>
        <td>
          <div className="admin-row-actions learning-plan-policy-actions">
            <HeaderActionTooltip id={moveUpTooltipId} label={t.moveUpPolicy(policy.name)}>
              <button aria-describedby={moveUpTooltipId} aria-label={t.moveUpPolicy(policy.name)} className="icon-button compact" disabled={ordering || index === 0} onClick={() => void movePolicy(index, -1)} type="button"><ArrowUp aria-hidden="true" /></button>
            </HeaderActionTooltip>
            <HeaderActionTooltip id={moveDownTooltipId} label={t.moveDownPolicy(policy.name)}>
              <button aria-describedby={moveDownTooltipId} aria-label={t.moveDownPolicy(policy.name)} className="icon-button compact" disabled={ordering || index === policies.length - 1} onClick={() => void movePolicy(index, 1)} type="button"><ArrowDown aria-hidden="true" /></button>
            </HeaderActionTooltip>
            <HeaderActionTooltip id={editTooltipId} label={t.editPolicy(policy.name)}>
              <button aria-describedby={editTooltipId} aria-label={t.editPolicy(policy.name)} className="icon-button compact" disabled={ordering} onClick={() => setEditingPolicy(policy)} type="button"><Pencil aria-hidden="true" /></button>
            </HeaderActionTooltip>
            <HeaderActionTooltip id={deleteTooltipId} label={t.deletePolicy(policy.name)}>
              <button aria-describedby={deleteTooltipId} aria-label={t.deletePolicy(policy.name)} className="icon-button compact danger-icon-button" disabled={ordering} onClick={() => setDeletingPolicy(policy)} type="button"><Trash2 aria-hidden="true" /></button>
            </HeaderActionTooltip>
          </div>
        </td>
      </tr>
    );
  }

  function formatScope(range: PolicySubjectRange): string {
    if (range.allSubject) {
      return t.allUsers;
    }
    const userCount = range.subjects.filter((subject) => subject.type === 'USER').length;
    return t.scopeSummary(userCount, range.subjects.length - userCount);
  }

  function formatNumber(value: number): string {
    return new Intl.NumberFormat(locale).format(value);
  }
}

function formatDateTime(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
