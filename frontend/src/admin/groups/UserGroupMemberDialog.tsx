import { Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { addUserGroupMembers, getAdminUsers, requireApiData } from '../../services/api';
import type { AdminUserPage, AdminUserSummary, UserGroupMemberBatchResponse } from '../../types/api';

interface UserGroupMemberDialogProps {
  groupCode: string;
  groupId: number;
  onClose: () => void;
  onCompleted: () => void;
}

export default function UserGroupMemberDialog({ groupCode, groupId, onClose, onCompleted }: UserGroupMemberDialogProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  const [keyword, setKeyword] = useState('');
  const [usersPage, setUsersPage] = useState<AdminUserPage>({ items: [], total: 0, page: 1, pageSize: 20 });
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [expiresAt, setExpiresAt] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<UserGroupMemberBatchResponse>();
  const requestIdRef = useRef(0);
  const selectedCount = selected.size;
  const selectedUsers = useMemo(() => usersPage.items.filter((user) => selected.has(user.id)), [selected, usersPage.items]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => void loadUsers(controller.signal), 220);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [keyword]);

  async function loadUsers(signal?: AbortSignal) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError('');
    try {
      const page = requireApiData(await getAdminUsers({ page: 1, pageSize: 20, keyword }, signal), t.userSearchFailed);
      if (requestId === requestIdRef.current && !signal?.aborted) setUsersPage(page);
    } catch (caught) {
      if (requestId === requestIdRef.current && !signal?.aborted) setError(caught instanceof Error ? caught.message : t.userSearchFailed);
    } finally {
      if (requestId === requestIdRef.current && !signal?.aborted) setLoading(false);
    }
  }

  function isExisting(user: AdminUserSummary) {
    return user.groups?.some((group) => group.id === groupId) ?? false;
  }

  function toggle(userId: number) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(userId)) next.delete(userId); else next.add(userId);
      return next;
    });
  }

  async function submit() {
    if (saving || selectedCount === 0) return;
    let normalizedExpiry: string | null = null;
    if (expiresAt) {
      const parsed = new Date(expiresAt);
      if (Number.isNaN(parsed.getTime()) || parsed.getTime() <= Date.now()) {
        setError(t.expiryInvalid);
        return;
      }
      normalizedExpiry = parsed.toISOString();
    }
    setSaving(true);
    setError('');
    try {
      const response = requireApiData(await addUserGroupMembers(groupId, { userIds: [...selected], expiresAt: normalizedExpiry }), t.memberAddFailed);
      setResult(response);
      setSelected(new Set());
      onCompleted();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.memberAddFailed);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="admin-dialog-backdrop" role="presentation">
      <section aria-labelledby="member-dialog-title" aria-modal="true" className="admin-dialog user-group-member-dialog" role="dialog">
        <header><div><h2 id="member-dialog-title">{t.addMembersTitle}</h2><p>{t.addMembersDescription(groupCode)}</p></div><button aria-label={resources.common.close} className="icon-button" onClick={onClose} type="button"><X aria-hidden="true" /></button></header>
        {error ? <p className="error-text" role="alert">{error}</p> : null}
        {result ? <div className="user-group-result" role="status"><strong>{t.addCompleted}</strong><span>{t.addResult(result.addedCount, result.updatedCount, result.failedCount)}</span></div> : null}
        <label className="admin-dialog-search"><Search aria-hidden="true" /><input aria-label={t.userSearchPlaceholder} autoFocus onChange={(event) => setKeyword(event.target.value.trim())} placeholder={t.userSearchPlaceholder} type="search" /></label>
        <div className="user-group-user-picker" aria-busy={loading}>
          {loading ? <p>{t.userSearching}</p> : null}
          {!loading && usersPage.items.map((user) => {
            const existing = isExisting(user);
            const disabled = existing || user.status === 'DELETED';
            return (
              <label className={disabled ? 'disabled' : ''} key={user.id}>
                <input checked={selected.has(user.id)} disabled={disabled} onChange={() => toggle(user.id)} type="checkbox" />
                <span><strong>{user.displayName || resources.app.unknownUser(user.id)}</strong><small>{user.email || `#${user.id}`}</small></span>
                <em>{existing ? t.alreadyMember : user.status === 'DELETED' ? t.userStatuses.DELETED : ''}</em>
              </label>
            );
          })}
          {!loading && usersPage.items.length === 0 ? <p>{t.userSearchEmpty}</p> : null}
        </div>
        <div className="user-group-member-options">
          <label><span>{t.expiresAtOptional}</span><input min={minimumLocalDateTime()} onChange={(event) => setExpiresAt(event.target.value)} type="datetime-local" value={expiresAt} /></label>
          <p>{selectedCount ? t.selectedUsers(selectedCount, selectedUsers.map((user) => user.displayName || `#${user.id}`)) : t.noUsersSelected}</p>
        </div>
        <footer><button className="secondary-button" disabled={saving} onClick={onClose} type="button">{resources.common.close}</button><button className="primary-button" disabled={saving || selectedCount === 0} onClick={() => void submit()} type="button">{saving ? t.addingMembers : t.addSelected(selectedCount)}</button></footer>
      </section>
    </div>
  );
}

function minimumLocalDateTime() {
  const date = new Date(Date.now() + 60_000);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
