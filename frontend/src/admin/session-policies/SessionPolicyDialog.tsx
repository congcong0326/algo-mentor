import { X } from 'lucide-react';
import { FormEvent, useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  createAdminPolicy,
  requireApiData,
  updateAdminPolicy,
} from '../../services/api';
import type {
  AdminGenericPolicy,
  AdminGenericPolicyWriteRequest,
  GenericPolicyStatus,
  PolicySubjectRange,
} from '../../types/api';
import PolicySubjectScopeFieldset from '../policies/PolicySubjectScopeFieldset';
import {
  AUTH_USER_SESSION_POLICY_TYPE,
  defaultUserSessionPolicyContent,
  isPositivePolicyInteger,
  policySubjectRange,
  userSessionPolicyContent,
} from './sessionPolicy';

interface SessionPolicyDialogProps {
  policy?: AdminGenericPolicy;
  onClose: () => void;
  onSaved: (policy: AdminGenericPolicy) => void;
}

export default function SessionPolicyDialog({ policy, onClose, onSaved }: SessionPolicyDialogProps) {
  const { resources } = useI18n();
  const t = resources.sessionPolicy;
  const existingContent = policy ? userSessionPolicyContent(policy) : defaultUserSessionPolicyContent();
  const existingRange = policy ? policySubjectRange(policy) : { allSubject: true, subjects: [] };
  const [name, setName] = useState(policy?.name ?? '');
  const [description, setDescription] = useState(policy?.description ?? '');
  const [status, setStatus] = useState<GenericPolicyStatus>(policy?.status ?? 'ENABLED');
  const [subjectRange, setSubjectRange] = useState<PolicySubjectRange>(existingRange);
  const [maxSessions, setMaxSessions] = useState(String(existingContent.maxSessions));
  const [absoluteTimeoutSeconds, setAbsoluteTimeoutSeconds] = useState(String(existingContent.absoluteTimeoutSeconds));
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
    if (!isPositivePolicyInteger(maxSessions) || !isPositivePolicyInteger(absoluteTimeoutSeconds)) {
      setError(t.valueInvalid);
      return;
    }
    if (!subjectRange.allSubject && subjectRange.subjects.length === 0) {
      setError(t.subjectRequired);
      return;
    }

    const request: AdminGenericPolicyWriteRequest = {
      typeCode: AUTH_USER_SESSION_POLICY_TYPE,
      name: normalizedName,
      description: description.trim(),
      status,
      subjectRange: {
        allSubject: subjectRange.allSubject,
        subjects: subjectRange.allSubject ? [] : subjectRange.subjects,
      },
      content: {
        maxSessions: Number(maxSessions),
        absoluteTimeoutSeconds: Number(absoluteTimeoutSeconds),
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
      <form aria-labelledby="session-policy-dialog-title" aria-modal="true" className="admin-dialog session-policy-dialog" onSubmit={(event) => void save(event)} role="dialog">
        <header>
          <div>
            <h2 id="session-policy-dialog-title">{policy ? t.editTitle : t.createTitle}</h2>
            <p>{t.dialogDescription}</p>
          </div>
          <button aria-label={resources.common.close} className="icon-button" disabled={saving} onClick={onClose} type="button">
            <X aria-hidden="true" />
          </button>
        </header>

        {error ? <p className="error-text" role="alert">{error}</p> : null}

        <div className="session-policy-form-grid">
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
            <span>{t.maxSessions}</span>
            <input inputMode="numeric" max={2_147_483_647} min="1" onChange={(event) => setMaxSessions(event.target.value)} type="number" value={maxSessions} />
          </label>
          <label>
            <span>{t.absoluteTimeoutSeconds}</span>
            <input inputMode="numeric" max={2_147_483_647} min="1" onChange={(event) => setAbsoluteTimeoutSeconds(event.target.value)} type="number" value={absoluteTimeoutSeconds} />
          </label>
        </div>

        <label>
          <span>{t.description}</span>
          <textarea maxLength={500} onChange={(event) => setDescription(event.target.value)} rows={3} value={description} />
        </label>

        <PolicySubjectScopeFieldset
          labels={t}
          name="session-policy-scope"
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
  policy: AdminGenericPolicy,
  request: AdminGenericPolicyWriteRequest,
) {
  const { typeCode: _, ...updateRequest } = request;
  return updateAdminPolicy(policy.id, { ...updateRequest, version: policy.version });
}
