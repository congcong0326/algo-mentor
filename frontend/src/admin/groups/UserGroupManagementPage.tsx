import { Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { deleteUserGroup, getUserGroups, requireApiData } from '../../services/api';
import type { UserGroupPage, UserGroupStatus, UserGroupSummary } from '../../types/api';
import { formatDateTime } from '../ai/aiFormat';
import UserGroupDialog from './UserGroupDialog';

interface UserGroupManagementPageProps {
  onNavigate: (path: string) => void;
}

const defaultPageSize = 20;

export default function UserGroupManagementPage({ onNavigate }: UserGroupManagementPageProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<UserGroupStatus | ''>('');
  const [groupsPage, setGroupsPage] = useState<UserGroupPage>({ items: [], total: 0, page: 1, pageSize: defaultPageSize });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [editingGroup, setEditingGroup] = useState<UserGroupSummary | null>();
  const [deletingGroup, setDeletingGroup] = useState<UserGroupSummary>();
  const [deletePending, setDeletePending] = useState(false);
  const requestIdRef = useRef(0);
  const totalPages = useMemo(() => Math.max(1, Math.ceil(groupsPage.total / groupsPage.pageSize)), [groupsPage]);

  useEffect(() => {
    const controller = new AbortController();
    void loadGroups(controller.signal);
    return () => controller.abort();
  }, [keyword, page, status]);

  async function loadGroups(signal?: AbortSignal) {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError('');
    try {
      const result = requireApiData(await getUserGroups({ page, pageSize: defaultPageSize, keyword, status }, signal), t.loadFailed);
      if (requestId === requestIdRef.current && !signal?.aborted) setGroupsPage(result);
    } catch (caught) {
      if (requestId === requestIdRef.current && !signal?.aborted) setError(caught instanceof Error ? caught.message : t.loadFailed);
    } finally {
      if (requestId === requestIdRef.current && !signal?.aborted) setLoading(false);
    }
  }

  async function confirmDelete() {
    if (!deletingGroup || deletePending) return;
    setDeletePending(true);
    setError('');
    try {
      requireApiData(await deleteUserGroup(deletingGroup.id), t.deleteFailed);
      setDeletingGroup(undefined);
      if (groupsPage.items.length === 1 && page > 1) {
        setPage((value) => Math.max(1, value - 1));
      } else {
        await loadGroups();
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.deleteFailed);
    } finally {
      setDeletePending(false);
    }
  }

  return (
    <section aria-label={t.title} className="admin-data-page user-groups-page">
      <header className="admin-page-toolbar">
        <div><h1>{t.title}</h1>{error ? <p className="error-text" role="alert">{error}</p> : null}</div>
        <div className="admin-page-commands">
          <button className="primary-button compact" onClick={() => setEditingGroup(null)} type="button"><Plus aria-hidden="true" /><span>{t.create}</span></button>
          <button aria-label={t.refresh} className="icon-button" disabled={loading} onClick={() => void loadGroups()} title={t.refresh} type="button"><RefreshCw aria-hidden="true" /></button>
        </div>
        <div className="admin-page-filters">
          <input aria-label={t.searchPlaceholder} onChange={(event) => { setPage(1); setKeyword(event.target.value.trim()); }} placeholder={t.searchPlaceholder} type="search" />
          <select aria-label={t.status} onChange={(event) => { setPage(1); setStatus(event.target.value as UserGroupStatus | ''); }} value={status}>
            <option value="">{t.statusAll}</option><option value="ACTIVE">{t.statusActive}</option><option value="DISABLED">{t.statusDisabled}</option>
          </select>
        </div>
      </header>

      <div className="admin-table-wrap">
        <table className="admin-data-table user-groups-table">
          <thead><tr><th>{t.name}</th><th>{t.code}</th><th>{t.status}</th><th>{t.activeMembers}</th><th>{t.createdAt}</th><th>{t.updatedAt}</th><th>{t.actions}</th></tr></thead>
          <tbody>
            {loading ? <tr><td colSpan={7}>{t.loading}</td></tr> : null}
            {!loading && groupsPage.items.map((group) => (
              <tr key={group.id}>
                <td><button className="admin-table-link" onClick={() => onNavigate(`/admin/user-groups/${group.id}`)} type="button"><strong>{group.name}</strong><small>{group.description || resources.common.empty}</small></button></td>
                <td><code>{group.code}</code></td>
                <td><span className={`admin-status-badge ${group.status.toLowerCase()}`}>{group.status === 'ACTIVE' ? t.statusActive : t.statusDisabled}</span></td>
                <td>{group.activeMemberCount}</td>
                <td>{formatDateTime(group.createdAt)}</td>
                <td>{formatDateTime(group.updatedAt)}</td>
                <td><div className="admin-row-actions"><button className="secondary-button compact" onClick={() => onNavigate(`/admin/user-groups/${group.id}`)} type="button">{resources.common.view}</button><button aria-label={t.editGroup(group.name)} className="icon-button compact" onClick={() => setEditingGroup(group)} title={t.edit} type="button"><Pencil aria-hidden="true" /></button><button aria-label={t.deleteGroup(group.name)} className="icon-button compact danger-icon-button" disabled={group.status !== 'DISABLED'} onClick={() => setDeletingGroup(group)} title={group.status === 'DISABLED' ? t.delete : t.deleteRequiresDisabled} type="button"><Trash2 aria-hidden="true" /></button></div></td>
              </tr>
            ))}
            {!loading && groupsPage.items.length === 0 ? <tr><td colSpan={7}>{t.empty}</td></tr> : null}
          </tbody>
        </table>
      </div>

      <footer className="admin-pagination"><span>{t.total(groupsPage.total)}</span><div><button className="secondary-button compact" disabled={page <= 1 || loading} onClick={() => setPage((value) => value - 1)} type="button">{resources.common.previousPage}</button><span>{resources.common.pageStatus(page, totalPages)}</span><button className="secondary-button compact" disabled={page >= totalPages || loading} onClick={() => setPage((value) => value + 1)} type="button">{resources.common.nextPage}</button></div></footer>

      {editingGroup !== undefined ? <UserGroupDialog group={editingGroup ?? undefined} onClose={() => setEditingGroup(undefined)} onSaved={() => { setEditingGroup(undefined); void loadGroups(); }} /> : null}
      {deletingGroup ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section aria-labelledby="delete-user-group-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog">
            <h2 id="delete-user-group-title">{t.deleteTitle}</h2>
            <p>{t.deleteDescription(deletingGroup.name, deletingGroup.code)}</p>
            <footer><button className="secondary-button" disabled={deletePending} onClick={() => setDeletingGroup(undefined)} type="button">{resources.common.cancel}</button><button className="danger-button" disabled={deletePending} onClick={() => void confirmDelete()} type="button">{deletePending ? t.deleting : t.delete}</button></footer>
          </section>
        </div>
      ) : null}
    </section>
  );
}
