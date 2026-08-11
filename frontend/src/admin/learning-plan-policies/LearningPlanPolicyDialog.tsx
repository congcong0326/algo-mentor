import { X } from 'lucide-react';
import { FormEvent, useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { createAdminPolicy, requireApiData, updateAdminPolicy } from '../../services/api';
import type {
  AdminGenericPolicy,
  AdminGenericPolicyWriteRequest,
  GenericPolicyStatus,
  LearningPlanCreationPolicyContent,
  PolicySubjectRange,
} from '../../types/api';
import PolicySubjectScopeFieldset from '../policies/PolicySubjectScopeFieldset';
import {
  defaultLearningPlanCreationPolicyContent,
  isPolicyIntegerInRange,
  LEARNING_PLAN_CREATION_POLICY_TYPE,
  MAX_DRAFT_RETENTION_DAYS,
  MAX_LEARNING_PLAN_POLICY_LIMIT,
} from './learningPlanPolicy';

type LearningPlanPolicy = AdminGenericPolicy<LearningPlanCreationPolicyContent>;

interface LearningPlanPolicyDialogProps {
  onClose: () => void;
  onSaved: (policy: LearningPlanPolicy) => void;
  policy?: LearningPlanPolicy;
}

export default function LearningPlanPolicyDialog({ policy, onClose, onSaved }: LearningPlanPolicyDialogProps) {
  const { resources } = useI18n();
  const t = resources.learningPlanPolicy;
  const content = policy?.content ?? defaultLearningPlanCreationPolicyContent();
  const [name, setName] = useState(policy?.name ?? '');
  const [description, setDescription] = useState(policy?.description ?? '');
  const [status, setStatus] = useState<GenericPolicyStatus>(policy?.status ?? 'ENABLED');
  const [subjectRange, setSubjectRange] = useState<PolicySubjectRange>(policy?.subjectRange ?? {
    allSubject: true,
    subjects: [],
  });
  const [maxSavedPlans, setMaxSavedPlans] = useState(String(content.maxSavedPlans));
  const [dailyDraftCreationLimit, setDailyDraftCreationLimit] = useState(String(content.dailyDraftCreationLimit));
  const [draftRetentionDays, setDraftRetentionDays] = useState(String(content.draftRetentionDays));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !saving) {
        onClose();
      }
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [onClose, saving]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) {
      return;
    }
    const normalizedName = name.trim();
    if (!normalizedName) {
      setError(t.nameRequired);
      return;
    }
    if (!isPolicyIntegerInRange(maxSavedPlans, 0, MAX_LEARNING_PLAN_POLICY_LIMIT)
      || !isPolicyIntegerInRange(dailyDraftCreationLimit, 0, MAX_LEARNING_PLAN_POLICY_LIMIT)
      || !isPolicyIntegerInRange(draftRetentionDays, 1, MAX_DRAFT_RETENTION_DAYS)) {
      setError(t.valueInvalid);
      return;
    }
    if (!subjectRange.allSubject && subjectRange.subjects.length === 0) {
      setError(t.subjectRequired);
      return;
    }

    const request: AdminGenericPolicyWriteRequest<LearningPlanCreationPolicyContent> = {
      typeCode: LEARNING_PLAN_CREATION_POLICY_TYPE,
      name: normalizedName,
      description: description.trim(),
      status,
      subjectRange: {
        allSubject: subjectRange.allSubject,
        subjects: subjectRange.allSubject ? [] : subjectRange.subjects,
      },
      content: {
        maxSavedPlans: Number(maxSavedPlans),
        dailyDraftCreationLimit: Number(dailyDraftCreationLimit),
        draftRetentionDays: Number(draftRetentionDays),
      },
    };

    setSaving(true);
    setError('');
    try {
      const saved = policy
        ? requireApiData(await updateExistingPolicy(policy, request), t.saveFailed)
        : requireApiData(await createAdminPolicy(request), t.saveFailed);
      onSaved(saved);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.saveFailed);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-dialog-backdrop" role="presentation">
      <form aria-labelledby="learning-plan-policy-dialog-title" aria-modal="true" className="admin-dialog session-policy-dialog" noValidate onSubmit={(event) => void save(event)} role="dialog">
        <header>
          <div>
            <h2 id="learning-plan-policy-dialog-title">{policy ? t.editTitle : t.createTitle}</h2>
            <p>{t.dialogDescription}</p>
          </div>
          <button aria-label={resources.common.close} className="icon-button" disabled={saving} onClick={onClose} type="button">
            <X aria-hidden="true" />
          </button>
        </header>

        {error ? <p className="error-text" role="alert">{error}</p> : null}

        <div className="session-policy-form-grid learning-plan-policy-form-grid">
          <label className="session-policy-name-field">
            <span>{t.name}</span>
            <input autoFocus maxLength={120} onChange={(event) => setName(event.target.value)} value={name} />
          </label>
          <label>
            <span>{t.status}</span>
            <select onChange={(event) => setStatus(event.target.value as GenericPolicyStatus)} value={status}>
              <option value="ENABLED">{t.statuses.ENABLED}</option>
              <option value="DISABLED">{t.statuses.DISABLED}</option>
            </select>
          </label>
          <label>
            <span>{t.maxSavedPlans}</span>
            <input inputMode="numeric" max={MAX_LEARNING_PLAN_POLICY_LIMIT} min="0" onChange={(event) => setMaxSavedPlans(event.target.value)} type="number" value={maxSavedPlans} />
          </label>
          <label>
            <span>{t.dailyDraftCreationLimit}</span>
            <input inputMode="numeric" max={MAX_LEARNING_PLAN_POLICY_LIMIT} min="0" onChange={(event) => setDailyDraftCreationLimit(event.target.value)} type="number" value={dailyDraftCreationLimit} />
          </label>
          <label>
            <span>{t.draftRetentionDays}</span>
            <input inputMode="numeric" max={MAX_DRAFT_RETENTION_DAYS} min="1" onChange={(event) => setDraftRetentionDays(event.target.value)} type="number" value={draftRetentionDays} />
          </label>
        </div>

        <label>
          <span>{t.description}</span>
          <textarea maxLength={500} onChange={(event) => setDescription(event.target.value)} rows={3} value={description} />
        </label>

        <PolicySubjectScopeFieldset
          labels={t}
          name="learning-plan-policy-scope"
          onChange={(range) => { setSubjectRange(range); setError(''); }}
          value={subjectRange}
        />

        <footer>
          <button className="secondary-button" disabled={saving} onClick={onClose} type="button">{resources.common.cancel}</button>
          <button className="primary-button" disabled={saving} type="submit">{saving ? t.saving : t.save}</button>
        </footer>
      </form>
    </div>
  );
}

function updateExistingPolicy(
  policy: LearningPlanPolicy,
  request: AdminGenericPolicyWriteRequest<LearningPlanCreationPolicyContent>,
) {
  const { typeCode: _, ...updateRequest } = request;
  return updateAdminPolicy<LearningPlanCreationPolicyContent>(policy.id, {
    ...updateRequest,
    version: policy.version,
  });
}
