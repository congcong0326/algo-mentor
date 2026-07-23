import { ArrowLeft, Pencil, Plus, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { getUserGroup, getUserGroupMembers, removeUserGroupMember, requireApiData } from '../../services/api';
import type { UserGroupDetail, UserGroupMember, UserGroupMemberPage } from '../../types/api';
import UserGroupDialog from './UserGroupDialog';
import UserGroupMemberDialog from './UserGroupMemberDialog';
import UserGroupMemberTable from './UserGroupMemberTable';

interface UserGroupDetailPageProps {
  groupId: number;
  onNavigate: (path: string) => void;
}

const pageSize = 20;

export default function UserGroupDetailPage({ groupId, onNavigate }: UserGroupDetailPageProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  const [group, setGroup] = useState<UserGroupDetail>();
  const [membersPage, setMembersPage] = useState<UserGroupMemberPage>({ items: [], total: 0, page: 1, pageSize });
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [groupLoading, setGroupLoading] = useState(true);
  const [membersLoading, setMembersLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(false);
  const [addingMembers, setAddingMembers] = useState(false);
  const [removing, setRemoving] = useState<UserGroupMember>();
  const [operationPending, setOperationPending] = useState(false);
  const groupRequestRef = useRef(0);
  const memberRequestRef = useRef(0);
  const totalPages = useMemo(() => Math.max(1, Math.ceil(membersPage.total / membersPage.pageSize)), [membersPage]);

  useEffect(() => {
    const controller = new AbortController();
    void loadGroup(controller.signal);
    return () => controller.abort();
  }, [groupId]);

  useEffect(() => {
    const controller = new AbortController();
    void loadMembers(controller.signal);
    return () => controller.abort();
  }, [groupId, keyword, page]);

  async function loadGroup(signal?: AbortSignal) {
    const requestId = ++groupRequestRef.current;
    setGroupLoading(true);
    setError('');
    try {
      const result = requireApiData(await getUserGroup(groupId, signal), t.detailLoadFailed);
      if (requestId === groupRequestRef.current && !signal?.aborted) setGroup(result);
    } catch (caught) {
      if (requestId === groupRequestRef.current && !signal?.aborted) setError(caught instanceof Error ? caught.message : t.detailLoadFailed);
    } finally {
      if (requestId === groupRequestRef.current && !signal?.aborted) setGroupLoading(false);
    }
  }

  async function loadMembers(signal?: AbortSignal) {
    const requestId = ++memberRequestRef.current;
    setMembersLoading(true);
    try {
      const result = requireApiData(await getUserGroupMembers(groupId, { page, pageSize, keyword }, signal), t.membersLoadFailed);
      if (requestId === memberRequestRef.current && !signal?.aborted) setMembersPage(result);
    } catch (caught) {
      if (requestId === memberRequestRef.current && !signal?.aborted) setError(caught instanceof Error ? caught.message : t.membersLoadFailed);
    } finally {
      if (requestId === memberRequestRef.current && !signal?.aborted) setMembersLoading(false);
    }
  }

  async function confirmRemove() {
    if (!removing || operationPending) return;
    setOperationPending(true);
    setError('');
    try {
      requireApiData(await removeUserGroupMember(groupId, removing.userId), t.memberRemoveFailed);
      setRemoving(undefined);
      const nextPage = membersPage.items.length === 1 && page > 1 ? page - 1 : page;
      if (nextPage !== page) setPage(nextPage); else await loadMembers();
      await loadGroup();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.memberRemoveFailed);
    } finally {
      setOperationPending(false);
    }
  }

  return (
    <section aria-label={t.detailTitle} className="admin-data-page user-group-detail-page">
      <header className="user-group-detail-header">
        <button aria-label={t.backToGroups} className="icon-button" onClick={() => onNavigate('/admin/user-groups')} title={t.backToGroups} type="button"><ArrowLeft aria-hidden="true" /></button>
        <div className="user-group-detail-title">
          {groupLoading ? <h1>{t.loading}</h1> : <><h1>{group?.name || t.detailTitle}</h1><div><code>{group?.code}</code>{group ? <span className={`admin-status-badge ${group.status.toLowerCase()}`}>{group.status === 'ACTIVE' ? t.statusActive : t.statusDisabled}</span> : null}</div></>}
          {group?.description ? <p>{group.description}</p> : null}
          {error ? <p className="error-text" role="alert">{error}</p> : null}
        </div>
        <div className="admin-page-commands">
          <button className="secondary-button compact" disabled={!group} onClick={() => setEditing(true)} type="button"><Pencil aria-hidden="true" /><span>{t.edit}</span></button>
          <button className="primary-button compact" disabled={!group || group.status === 'DISABLED'} onClick={() => setAddingMembers(true)} title={group?.status === 'DISABLED' ? t.disabledCannotAdd : undefined} type="button"><Plus aria-hidden="true" /><span>{t.addMembers}</span></button>
        </div>
      </header>

      <div className="user-group-detail-summary"><div><span>{t.activeMembers}</span><strong>{group?.activeMemberCount ?? '—'}</strong></div><div><span>{t.status}</span><strong>{group?.status === 'ACTIVE' ? t.statusActive : group ? t.statusDisabled : '—'}</strong></div></div>

      <div className="admin-page-toolbar user-group-members-toolbar">
        <div><h2>{t.membersTitle}</h2><span>{t.total(membersPage.total)}</span></div>
        <div className="admin-page-filters"><input aria-label={t.memberSearchPlaceholder} onChange={(event) => { setPage(1); setKeyword(event.target.value.trim()); }} placeholder={t.memberSearchPlaceholder} type="search" /><button aria-label={t.refresh} className="icon-button" disabled={membersLoading} onClick={() => { void loadMembers(); void loadGroup(); }} title={t.refresh} type="button"><RefreshCw aria-hidden="true" /></button></div>
      </div>

      <UserGroupMemberTable loading={membersLoading} members={membersPage.items} onOpenUser={(userId) => onNavigate(`/admin/users?userId=${userId}`)} onRemove={setRemoving} />
      <footer className="admin-pagination"><span>{t.total(membersPage.total)}</span><div><button className="secondary-button compact" disabled={page <= 1 || membersLoading} onClick={() => setPage((value) => value - 1)} type="button">{resources.common.previousPage}</button><span>{resources.common.pageStatus(page, totalPages)}</span><button className="secondary-button compact" disabled={page >= totalPages || membersLoading} onClick={() => setPage((value) => value + 1)} type="button">{resources.common.nextPage}</button></div></footer>

      {editing && group ? <UserGroupDialog group={group} onClose={() => setEditing(false)} onSaved={(saved) => { setGroup(saved); setEditing(false); }} /> : null}
      {addingMembers && group ? <UserGroupMemberDialog groupCode={group.code} groupId={group.id} onClose={() => setAddingMembers(false)} onCompleted={() => { void loadGroup(); void loadMembers(); }} /> : null}
      {removing ? (
        <div className="admin-dialog-backdrop" role="presentation"><section aria-labelledby="remove-member-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog"><h2 id="remove-member-title">{t.removeMemberTitle}</h2><p>{t.removeMemberDescription(removing.displayName || `#${removing.userId}`, group?.name || '')}</p><footer><button className="secondary-button" disabled={operationPending} onClick={() => setRemoving(undefined)} type="button">{resources.common.cancel}</button><button className="danger-button" disabled={operationPending} onClick={() => void confirmRemove()} type="button">{operationPending ? t.removing : t.remove}</button></footer></section></div>
      ) : null}
    </section>
  );
}
