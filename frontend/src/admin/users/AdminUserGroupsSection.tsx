import { Plus, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { addUserGroupMembers, getUserGroups, removeUserGroupMember, requireApiData } from '../../services/api';
import type { AdminUserDetail, AdminUserGroupMembership, UserGroupSummary } from '../../types/api';
import { formatDateTime } from '../ai/aiFormat';

interface AdminUserGroupsSectionProps {
  onChanged: () => void;
  user: AdminUserDetail;
}

export default function AdminUserGroupsSection({ onChanged, user }: AdminUserGroupsSectionProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  const memberships = user.groups ?? [];
  const [dialogOpen, setDialogOpen] = useState(false);
  const [groups, setGroups] = useState<UserGroupSummary[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [removing, setRemoving] = useState<AdminUserGroupMembership>();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const existingGroupIds = useMemo(() => new Set(memberships.map((group) => group.id)), [memberships]);
  const availableGroups = groups.filter((group) => group.status === 'ACTIVE' && !existingGroupIds.has(group.id));

  useEffect(() => {
    if (!dialogOpen) return undefined;
    const controller = new AbortController();
    void loadGroups(controller.signal);
    return () => controller.abort();
  }, [dialogOpen]);

  async function loadGroups(signal?: AbortSignal) {
    setError('');
    try {
      const page = requireApiData(await getUserGroups({ page: 1, pageSize: 100, status: 'ACTIVE' }, signal), t.loadFailed);
      setGroups(page.items);
    } catch (caught) {
      if (!signal?.aborted) setError(caught instanceof Error ? caught.message : t.loadFailed);
    }
  }

  async function addMembership() {
    const groupId = Number(selectedGroupId);
    if (!Number.isSafeInteger(groupId) || groupId <= 0 || pending) return;
    let expiry: string | null = null;
    if (expiresAt) {
      const parsed = new Date(expiresAt);
      if (Number.isNaN(parsed.getTime()) || parsed.getTime() <= Date.now()) {
        setError(t.expiryInvalid);
        return;
      }
      expiry = parsed.toISOString();
    }
    setPending(true);
    setError('');
    try {
      requireApiData(await addUserGroupMembers(groupId, { userIds: [user.id], expiresAt: expiry }), t.memberAddFailed);
      setDialogOpen(false);
      setSelectedGroupId('');
      setExpiresAt('');
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.memberAddFailed);
    } finally {
      setPending(false);
    }
  }

  async function removeMembership() {
    if (!removing || pending) return;
    setPending(true);
    setError('');
    try {
      requireApiData(await removeUserGroupMember(removing.id, user.id), t.memberRemoveFailed);
      setRemoving(undefined);
      onChanged();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.memberRemoveFailed);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="admin-user-groups-section">
      <header><div><h3>{t.userGroupsTitle}</h3><p>{t.userGroupsDescription}</p></div><button className="secondary-button compact" onClick={() => setDialogOpen(true)} type="button"><Plus aria-hidden="true" /><span>{t.addToGroup}</span></button></header>
      {error ? <p className="error-text" role="alert">{error}</p> : null}
      <div className="admin-user-memberships">
        {memberships.map((membership) => (
          <div key={membership.id}><span><strong>{membership.name}</strong><code>{membership.code}</code></span><small>{membership.expiresAt ? t.expiresOn(formatDateTime(membership.expiresAt)) : t.neverExpires}</small><button aria-label={t.removeMember(membership.name)} className="icon-button compact danger-icon-button" onClick={() => setRemoving(membership)} title={t.remove} type="button"><Trash2 aria-hidden="true" /></button></div>
        ))}
        {memberships.length === 0 ? <p>{t.noUserGroups}</p> : null}
      </div>

      {dialogOpen ? (
        <div className="admin-dialog-backdrop" role="presentation"><section aria-labelledby="add-user-group-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog"><h2 id="add-user-group-title">{t.addToGroupTitle}</h2><p>{t.addToGroupDescription(user.displayName || `#${user.id}`)}</p><label><span>{t.targetGroup}</span><select autoFocus onChange={(event) => setSelectedGroupId(event.target.value)} value={selectedGroupId}><option value="">{t.selectGroup}</option>{availableGroups.map((group) => <option key={group.id} value={group.id}>{group.name} ({group.code})</option>)}</select></label><label><span>{t.expiresAtOptional}</span><input onChange={(event) => setExpiresAt(event.target.value)} type="datetime-local" value={expiresAt} /></label>{availableGroups.length === 0 ? <p>{t.noAvailableGroups}</p> : null}<footer><button className="secondary-button" disabled={pending} onClick={() => setDialogOpen(false)} type="button">{resources.common.cancel}</button><button className="primary-button" disabled={pending || !selectedGroupId} onClick={() => void addMembership()} type="button">{pending ? t.addingMembers : t.addToGroup}</button></footer></section></div>
      ) : null}

      {removing ? (
        <div className="admin-dialog-backdrop" role="presentation"><section aria-labelledby="remove-user-group-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog"><h2 id="remove-user-group-title">{t.removeMemberTitle}</h2><p>{t.removeMemberDescription(user.displayName || `#${user.id}`, removing.name)}</p><footer><button className="secondary-button" disabled={pending} onClick={() => setRemoving(undefined)} type="button">{resources.common.cancel}</button><button className="danger-button" disabled={pending} onClick={() => void removeMembership()} type="button">{pending ? t.removing : t.remove}</button></footer></section></div>
      ) : null}
    </section>
  );
}
