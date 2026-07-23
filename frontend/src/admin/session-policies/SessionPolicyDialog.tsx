import { Search, Trash2, X } from 'lucide-react';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import {
  createAdminPolicy,
  getAdminUserDetail,
  getAdminUsers,
  getUserGroup,
  getUserGroups,
  requireApiData,
  updateAdminPolicy,
} from '../../services/api';
import type {
  AdminGenericPolicy,
  AdminGenericPolicyWriteRequest,
  AdminUserPage,
  GenericPolicyStatus,
  PolicySubject,
  PolicySubjectType,
  UserGroupPage,
} from '../../types/api';
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

interface SubjectSelection extends PolicySubject {
  label: string;
  detail?: string;
}

export default function SessionPolicyDialog({ policy, onClose, onSaved }: SessionPolicyDialogProps) {
  const { resources } = useI18n();
  const t = resources.sessionPolicy;
  const existingContent = policy ? userSessionPolicyContent(policy) : defaultUserSessionPolicyContent();
  const existingRange = policy ? policySubjectRange(policy) : { allSubject: true, subjects: [] };
  const [name, setName] = useState(policy?.name ?? '');
  const [description, setDescription] = useState(policy?.description ?? '');
  const [status, setStatus] = useState<GenericPolicyStatus>(policy?.status ?? 'ENABLED');
  const [allSubject, setAllSubject] = useState(existingRange.allSubject);
  const [subjects, setSubjects] = useState<SubjectSelection[]>(() => existingRange.subjects.map((subject) => ({
    ...subject,
    label: subject.type === 'USER' ? t.savedUserSubject : t.savedGroupSubject,
  })));
  const [maxSessions, setMaxSessions] = useState(String(existingContent.maxSessions));
  const [absoluteTimeoutSeconds, setAbsoluteTimeoutSeconds] = useState(String(existingContent.absoluteTimeoutSeconds));
  const [subjectType, setSubjectType] = useState<PolicySubjectType>('GROUP');
  const [subjectKeyword, setSubjectKeyword] = useState('');
  const [usersPage, setUsersPage] = useState<AdminUserPage>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [groupsPage, setGroupsPage] = useState<UserGroupPage>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [subjectsLoading, setSubjectsLoading] = useState(false);
  const [subjectsError, setSubjectsError] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const subjectRequestIdRef = useRef(0);
  const savedSubjectRequestIdRef = useRef(0);

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape' && !saving) {
        onClose();
      }
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [onClose, saving]);

  useEffect(() => {
    if (!policy || existingRange.allSubject || existingRange.subjects.length === 0) {
      return undefined;
    }
    const controller = new AbortController();
    const requestId = savedSubjectRequestIdRef.current + 1;
    savedSubjectRequestIdRef.current = requestId;

    void resolveSavedSubjects(controller.signal, requestId);
    return () => controller.abort();
  }, [policy?.id]);

  async function resolveSavedSubjects(signal: AbortSignal, requestId: number) {
    const resolved = await Promise.all(existingRange.subjects.map(async (subject): Promise<SubjectSelection> => {
      try {
        if (subject.type === 'USER') {
          const user = requireApiData(await getAdminUserDetail(subject.id, signal), t.subjectLoadFailed);
          const label = user.displayName || user.email || t.savedUserSubject;
          return { type: subject.type, id: subject.id, label, detail: user.email && user.email !== label ? user.email : undefined };
        }
        const group = requireApiData(await getUserGroup(subject.id, signal), t.subjectLoadFailed);
        return { type: subject.type, id: subject.id, label: group.name, detail: group.code };
      } catch {
        return {
          ...subject,
          label: subject.type === 'USER' ? t.savedUserSubject : t.savedGroupSubject,
        };
      }
    }));
    if (savedSubjectRequestIdRef.current === requestId && !signal.aborted) {
      setSubjects(resolved);
    }
  }

  useEffect(() => {
    if (allSubject) {
      return undefined;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadSubjects(controller.signal), 220);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [allSubject, subjectKeyword, subjectType]);

  async function loadSubjects(signal?: AbortSignal) {
    const requestId = subjectRequestIdRef.current + 1;
    subjectRequestIdRef.current = requestId;
    const current = () => subjectRequestIdRef.current === requestId && !signal?.aborted;
    setSubjectsLoading(true);
    setSubjectsError('');
    try {
      if (subjectType === 'USER') {
        const page = requireApiData(await getAdminUsers({
          page: 1,
          pageSize: 20,
          keyword: subjectKeyword.trim(),
          status: 'ACTIVE',
        }, signal), t.subjectLoadFailed);
        if (current()) {
          setUsersPage(page);
        }
      } else {
        const page = requireApiData(await getUserGroups({
          page: 1,
          pageSize: 20,
          keyword: subjectKeyword.trim(),
          status: 'ACTIVE',
        }, signal), t.subjectLoadFailed);
        if (current()) {
          setGroupsPage(page);
        }
      }
    } catch (caught) {
      if (current()) {
        setSubjectsError(caught instanceof Error ? caught.message : t.subjectLoadFailed);
      }
    } finally {
      if (current()) {
        setSubjectsLoading(false);
      }
    }
  }

  function addSubject(subject: SubjectSelection) {
    if (subjects.some((current) => current.type === subject.type && current.id === subject.id)) {
      return;
    }
    setSubjects((current) => [...current, subject]);
    setError('');
  }

  function removeSubject(subject: PolicySubject) {
    setSubjects((current) => current.filter((currentSubject) => (
      currentSubject.type !== subject.type || currentSubject.id !== subject.id
    )));
  }

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
    if (!allSubject && subjects.length === 0) {
      setError(t.subjectRequired);
      return;
    }

    const request: AdminGenericPolicyWriteRequest = {
      typeCode: AUTH_USER_SESSION_POLICY_TYPE,
      name: normalizedName,
      description: description.trim(),
      status,
      subjectRange: {
        allSubject,
        subjects: allSubject ? [] : subjects.map(({ type, id }) => ({ type, id })),
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

        <fieldset className="session-policy-scope-fieldset">
          <legend>{t.scope}</legend>
          <label className="session-policy-scope-option">
            <input checked={allSubject} name="session-policy-scope" onChange={() => setAllSubject(true)} type="radio" />
            <span>{t.allUsers}</span>
          </label>
          <label className="session-policy-scope-option">
            <input checked={!allSubject} name="session-policy-scope" onChange={() => setAllSubject(false)} type="radio" />
            <span>{t.selectedSubjects}</span>
          </label>

          {!allSubject ? (
            <div className="session-policy-subject-editor">
              <div aria-label={t.subjectType} className="session-policy-picker-tabs" role="tablist">
                {(['GROUP', 'USER'] as const).map((type) => (
                  <button
                    aria-selected={subjectType === type}
                    className={subjectType === type ? 'selected' : ''}
                    key={type}
                    onClick={() => setSubjectType(type)}
                    role="tab"
                    type="button"
                  >
                    {t.subjectTypes[type]}
                  </button>
                ))}
              </div>
              <label className="session-policy-subject-search">
                <Search aria-hidden="true" />
                <span className="visually-hidden">{t.subjectSearchPlaceholder}</span>
                <input aria-label={t.subjectSearchPlaceholder} onChange={(event) => setSubjectKeyword(event.target.value)} placeholder={t.subjectSearchPlaceholder} type="search" value={subjectKeyword} />
              </label>
              {subjectsError ? <p className="error-text" role="alert">{subjectsError}</p> : null}
              <div aria-busy={subjectsLoading} className="session-policy-subject-picker">
                {subjectsLoading ? <p>{t.subjectLoading}</p> : null}
                {!subjectsLoading && subjectType === 'USER' ? usersPage.items.map((user) => {
                  const label = user.displayName || user.email || resources.app.unknownUser(user.id);
                  const selected = subjects.some((subject) => subject.type === 'USER' && subject.id === user.id);
                  return (
                    <button disabled={selected} key={user.id} onClick={() => addSubject({ type: 'USER', id: user.id, label, detail: user.email && user.email !== label ? user.email : undefined })} type="button">
                      <strong>{label}</strong>
                      {user.email && user.email !== label ? <small>{user.email}</small> : null}
                    </button>
                  );
                }) : null}
                {!subjectsLoading && subjectType === 'GROUP' ? groupsPage.items.map((group) => {
                  const selected = subjects.some((subject) => subject.type === 'GROUP' && subject.id === group.id);
                  return (
                    <button disabled={selected} key={group.id} onClick={() => addSubject({ type: 'GROUP', id: group.id, label: group.name, detail: group.code })} type="button">
                      <strong>{group.name}</strong>
                      <small>{group.code}</small>
                    </button>
                  );
                }) : null}
                {!subjectsLoading && !subjectsError && (subjectType === 'USER' ? usersPage.items : groupsPage.items).length === 0 ? <p>{t.subjectEmpty}</p> : null}
              </div>
              <ul aria-label={t.selectedSubjects} className="session-policy-subject-list">
                {subjects.map((subject) => (
                  <li key={`${subject.type}-${subject.id}`}>
                    <span><strong>{subject.label}</strong>{subject.detail ? <small>{subject.detail}</small> : null}</span>
                    <button aria-label={t.removeSubject(subject.label)} className="icon-button compact danger-icon-button" onClick={() => removeSubject(subject)} title={t.remove} type="button">
                      <Trash2 aria-hidden="true" />
                    </button>
                  </li>
                ))}
                {subjects.length === 0 ? <li className="session-policy-subject-empty">{t.subjectRequired}</li> : null}
              </ul>
            </div>
          ) : null}
        </fieldset>

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
