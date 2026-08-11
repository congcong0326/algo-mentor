import { Search, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import type { LocaleResources } from '../../i18n/locales';
import {
  getAdminUserDetail,
  getAdminUsers,
  getUserGroup,
  getUserGroups,
  requireApiData,
} from '../../services/api';
import type {
  AdminUserPage,
  PolicySubject,
  PolicySubjectRange,
  PolicySubjectType,
  UserGroupPage,
} from '../../types/api';

type PolicySubjectScopeLabels = Pick<LocaleResources['sessionPolicy'],
  | 'allUsers'
  | 'remove'
  | 'removeSubject'
  | 'savedGroupSubject'
  | 'savedUserSubject'
  | 'scope'
  | 'selectedSubjects'
  | 'subjectEmpty'
  | 'subjectLoadFailed'
  | 'subjectLoading'
  | 'subjectRequired'
  | 'subjectSearchPlaceholder'
  | 'subjectType'
  | 'subjectTypes'
>;

interface PolicySubjectScopeFieldsetProps {
  labels: PolicySubjectScopeLabels;
  name: string;
  onChange: (range: PolicySubjectRange) => void;
  value: PolicySubjectRange;
}

interface SubjectSelection extends PolicySubject {
  label: string;
  detail?: string;
}

export default function PolicySubjectScopeFieldset({
  labels,
  name,
  onChange,
  value,
}: PolicySubjectScopeFieldsetProps) {
  const { resources } = useI18n();
  const initialSubjects = useRef(value.subjects).current;
  const [subjects, setSubjects] = useState<SubjectSelection[]>(() => initialSubjects.map((subject) => ({
    ...subject,
    label: subject.type === 'USER' ? labels.savedUserSubject : labels.savedGroupSubject,
  })));
  const [subjectType, setSubjectType] = useState<PolicySubjectType>('GROUP');
  const [subjectKeyword, setSubjectKeyword] = useState('');
  const [usersPage, setUsersPage] = useState<AdminUserPage>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [groupsPage, setGroupsPage] = useState<UserGroupPage>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [subjectsLoading, setSubjectsLoading] = useState(false);
  const [subjectsError, setSubjectsError] = useState('');
  const subjectRequestIdRef = useRef(0);
  const savedSubjectRequestIdRef = useRef(0);

  useEffect(() => {
    if (initialSubjects.length === 0) {
      return undefined;
    }
    const controller = new AbortController();
    const requestId = savedSubjectRequestIdRef.current + 1;
    savedSubjectRequestIdRef.current = requestId;
    void resolveSavedSubjects(controller.signal, requestId);
    return () => controller.abort();
  }, [initialSubjects]);

  useEffect(() => {
    if (value.allSubject) {
      return undefined;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadSubjects(controller.signal), 220);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [subjectKeyword, subjectType, value.allSubject]);

  async function resolveSavedSubjects(signal: AbortSignal, requestId: number) {
    const resolved = await Promise.all(initialSubjects.map(async (subject): Promise<SubjectSelection> => {
      try {
        if (subject.type === 'USER') {
          const user = requireApiData(await getAdminUserDetail(subject.id, signal), labels.subjectLoadFailed);
          const label = user.displayName || user.email || labels.savedUserSubject;
          return { type: subject.type, id: subject.id, label, detail: user.email && user.email !== label ? user.email : undefined };
        }
        const group = requireApiData(await getUserGroup(subject.id, signal), labels.subjectLoadFailed);
        return { type: subject.type, id: subject.id, label: group.name, detail: group.code };
      } catch {
        return {
          ...subject,
          label: subject.type === 'USER' ? labels.savedUserSubject : labels.savedGroupSubject,
        };
      }
    }));
    if (savedSubjectRequestIdRef.current === requestId && !signal.aborted) {
      setSubjects(resolved);
    }
  }

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
        }, signal), labels.subjectLoadFailed);
        if (current()) {
          setUsersPage(page);
        }
      } else {
        const page = requireApiData(await getUserGroups({
          page: 1,
          pageSize: 20,
          keyword: subjectKeyword.trim(),
          status: 'ACTIVE',
        }, signal), labels.subjectLoadFailed);
        if (current()) {
          setGroupsPage(page);
        }
      }
    } catch (caught) {
      if (current()) {
        setSubjectsError(caught instanceof Error ? caught.message : labels.subjectLoadFailed);
      }
    } finally {
      if (current()) {
        setSubjectsLoading(false);
      }
    }
  }

  function updateSubjects(next: SubjectSelection[]) {
    setSubjects(next);
    onChange({
      allSubject: value.allSubject,
      subjects: next.map(({ type, id }) => ({ type, id })),
    });
  }

  function addSubject(subject: SubjectSelection) {
    if (subjects.some((current) => current.type === subject.type && current.id === subject.id)) {
      return;
    }
    updateSubjects([...subjects, subject]);
  }

  function removeSubject(subject: PolicySubject) {
    updateSubjects(subjects.filter((current) => current.type !== subject.type || current.id !== subject.id));
  }

  function selectAllSubjects(allSubject: boolean) {
    onChange({
      allSubject,
      subjects: subjects.map(({ type, id }) => ({ type, id })),
    });
  }

  return (
    <fieldset className="session-policy-scope-fieldset">
      <legend>{labels.scope}</legend>
      <label className="session-policy-scope-option">
        <input checked={value.allSubject} name={name} onChange={() => selectAllSubjects(true)} type="radio" />
        <span>{labels.allUsers}</span>
      </label>
      <label className="session-policy-scope-option">
        <input checked={!value.allSubject} name={name} onChange={() => selectAllSubjects(false)} type="radio" />
        <span>{labels.selectedSubjects}</span>
      </label>

      {!value.allSubject ? (
        <div className="session-policy-subject-editor">
          <div aria-label={labels.subjectType} className="session-policy-picker-tabs" role="tablist">
            {(['GROUP', 'USER'] as const).map((type) => (
              <button
                aria-selected={subjectType === type}
                className={subjectType === type ? 'selected' : ''}
                key={type}
                onClick={() => setSubjectType(type)}
                role="tab"
                type="button"
              >
                {labels.subjectTypes[type]}
              </button>
            ))}
          </div>
          <label className="session-policy-subject-search">
            <Search aria-hidden="true" />
            <span className="visually-hidden">{labels.subjectSearchPlaceholder}</span>
            <input aria-label={labels.subjectSearchPlaceholder} onChange={(event) => setSubjectKeyword(event.target.value)} placeholder={labels.subjectSearchPlaceholder} type="search" value={subjectKeyword} />
          </label>
          {subjectsError ? <p className="error-text" role="alert">{subjectsError}</p> : null}
          <div aria-busy={subjectsLoading} className="session-policy-subject-picker">
            {subjectsLoading ? <p>{labels.subjectLoading}</p> : null}
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
            {!subjectsLoading && !subjectsError && (subjectType === 'USER' ? usersPage.items : groupsPage.items).length === 0 ? <p>{labels.subjectEmpty}</p> : null}
          </div>
          <ul aria-label={labels.selectedSubjects} className="session-policy-subject-list">
            {subjects.map((subject) => (
              <li key={`${subject.type}-${subject.id}`}>
                <span><strong>{subject.label}</strong>{subject.detail ? <small>{subject.detail}</small> : null}</span>
                <button aria-label={labels.removeSubject(subject.label)} className="icon-button compact danger-icon-button" onClick={() => removeSubject(subject)} title={labels.remove} type="button">
                  <Trash2 aria-hidden="true" />
                </button>
              </li>
            ))}
            {subjects.length === 0 ? <li className="session-policy-subject-empty">{labels.subjectRequired}</li> : null}
          </ul>
        </div>
      ) : null}
    </fieldset>
  );
}
